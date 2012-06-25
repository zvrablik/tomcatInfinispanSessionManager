package org.apache.catalina.session.ispn;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.SessionEvent;
import org.apache.catalina.SessionListener;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.security.SecurityUtil;
import org.apache.catalina.session.StandardSessionFacade;
import org.apache.catalina.util.Enumerator;
import org.apache.catalina.util.StringManager;
import org.infinispan.Cache;
import org.infinispan.atomic.AtomicMapLookup;
import org.infinispan.atomic.FineGrainedAtomicMap;

/**
 * Standard implementation of the <b>Session</b> interface.  This object is
 * serializable, so that it can be stored in persistent storage or transferred
 * to a different JVM for distributable session support.
 * <p>
 * <b>IMPLEMENTATION NOTE</b>:  An instance of this class represents both the
 * internal (Session) and application level (HttpSession) view of the session.
 * However, because the class itself is not declared public, Java logic outside
 * of the <code>org.apache.catalina.session</code> package cannot cast an
 * HttpSession view of this instance back to a Session view.
 * <p>
 * <b>IMPLEMENTATION NOTE</b>:  If you add fields to this class, you must
 * make sure that you carry them over in the read/writeObject methods so
 * that this class is properly serialized.
 *
 * @author zhenek
 * @author Craig R. McClanahan
 * @author Sean Legassick
 * @author <a href="mailto:jon@latchkey.com">Jon S. Stevens</a>
 * @version $Id: StandardSession.java 946841 2010-05-21 00:56:52Z kkolinko $
 */

public class InfinispanStandardSession 
    implements HttpSession, Session {

      protected static final boolean ACTIVITY_CHECK = 
          Globals.STRICT_SERVLET_COMPLIANCE
          || Boolean.valueOf(System.getProperty("org.apache.catalina.session.StandardSession.ACTIVITY_CHECK", "false")).booleanValue();


      // ----------------------------------------------------------- Constructors


      /**
       * Construct a new Session associated with the specified Manager.
       *
       * @param manager The manager with which this Session is associated
       * @param cache attributes - distributed cache
       * @param metaCache sessions metadata - distributed cache
       * @param sessionId  created session id
       * 
       */
      public InfinispanStandardSession(InfinispanSessionManagerBase manager, Cache<String, ?> cache, 
              Cache<String, ?> metaCache, String sessionId) {

          super();
          this.manager = manager;
          this.cache = cache;
          this.metaCache = metaCache;
          //store session without suffix to avoid session rename after cluster node disabled by load balancer
          String sessionIdWithoutJvmRoute = manager.stripJvmRoute(sessionId);
          this.attributes = new SessionAttributesCache(cache, sessionIdWithoutJvmRoute);
          
          this.metadata = new SessionMetaAttributes(metaCache, sessionIdWithoutJvmRoute);

          // Initialize access count
          if (ACTIVITY_CHECK) {
              accessCount = new AtomicInteger();
          }

      }


      // ----------------------------------------------------- Instance Variables


      /**
       * Type array.
       */
      protected static final String EMPTY_ARRAY[] = new String[0];


      /**
       * The dummy attribute value serialized when a NotSerializableException is
       * encountered in <code>writeObject()</code>.
       */
      protected static final String NOT_SERIALIZED =
          "___NOT_SERIALIZABLE_EXCEPTION___";

      /**
       * The authentication type used to authenticate our cached Principal,
       * if any.  NOTE:  This value is not included in the serialized
       * version of this object.
       */
      protected transient String authType = null;


      


      /**
       * Set of attribute names which are not allowed to be persisted.
       */
      protected static final String[] excludedAttributes = {
          Globals.SUBJECT_ATTR
      };


      /**
       * We are currently processing a session expiration, so bypass
       * certain IllegalStateException tests.  NOTE:  This value is not
       * included in the serialized version of this object.
       */
      protected transient volatile boolean expiring = false;


      /**
       * The facade associated with this session.  NOTE:  This value is not
       * included in the serialized version of this object.
       */
      protected transient StandardSessionFacade facade = null;


      /**
       * The session identifier of this Session.
       */
      protected String id = null;


      /**
       * Descriptive information describing this Session implementation.
       */
      protected static final String info = "InfinispanStandardSession/1.0";

      /**
       * The session event listeners for this Session.
       */
      protected transient ArrayList listeners = new ArrayList();


      /**
       * The Manager with which this Session is associated.
       */
      protected transient InfinispanSessionManagerBase manager = null;


      /**
       * distributed cache to store attributes
       */       
      protected transient Cache<String, ?> cache = null;
      
      /**
       * The collection of user data attributes associated with this Session.
       */
      protected SessionAttributesCache attributes = null;
      
      /**
       * distributed cache to store session metadata
       */
      private Cache<String, ?> metaCache = null;
      
      protected SessionMetaAttributes metadata = null;

      /**
       * Flag indicating whether this session is new or not.
       */
      protected boolean isNew = false;


      /**
       * Flag indicating whether this session is valid or not.
       */
      protected volatile boolean isValid = false;

      
      /**
       * Internal notes associated with this session by Catalina components
       * and event listeners.  <b>IMPLEMENTATION NOTE:</b> This object is
       * <em>not</em> saved and restored across session serializations!
       */
      protected transient Map notes = new Hashtable();


      /**
       * The authenticated Principal associated with this session, if any.
       * <b>IMPLEMENTATION NOTE:</b>  This object is <i>not</i> saved and
       * restored across session serializations!
       */
      protected transient Principal principal = null;


      /**
       * The string manager for this package.
       */
      protected static StringManager sm =
          StringManager.getManager(InfinispanStandardSession.class.getPackage().getName());


      /**
       * The HTTP session context associated with this session.
       */
      protected static HttpSessionContext sessionContext = null;


      /**
       * The property change support for this component.  NOTE:  This value
       * is not included in the serialized version of this object.
       */
      protected transient PropertyChangeSupport support =
          new PropertyChangeSupport(this);

      /**
       * The access count for this session.
       */
      protected transient AtomicInteger accessCount = null;

      
      // ----------------------------------------------------- Session Properties


      /**
       * Return the authentication type used to authenticate our cached
       * Principal, if any.
       */
      public String getAuthType() {

          return (this.authType);

      }


      /**
       * Set the authentication type used to authenticate our cached
       * Principal, if any.
       *
       * @param authType The new cached authentication type
       */
      public void setAuthType(String authType) {

          String oldAuthType = this.authType;
          this.authType = authType;
          support.firePropertyChange("authType", oldAuthType, this.authType);

      }


      /**
       * Set the creation time for this session.  This method is called by the
       * Manager when an existing Session instance is reused.
       *
       * @param time The new creation time
       */
      public void setCreationTime(long time) {
          metadata.setCreationTime(time);
          metadata.setLastAccessedTime(time);
          metadata.setThisAccessedTime(time);
      }


      /**
       * Return the session identifier for this session.
       */
      public String getId() {

          return (this.id);

      }


      /**
       * Return the session identifier for this session.
       */
      public String getIdInternal() {

          return (this.id);

      }


      /**
       * Set the session identifier for this session.
       *
       * @param id The new session identifier
       */
      public void setId(String id) {
          Map<String, Object> allAttributes = this.attributes.getAll();
          long creationTime = metadata.getCreationTime();
          long lastAccessedTime = metadata.getLastAccessedTime();
          int maxInactiveInterval = metadata.getMaxInactiveInterval();
          long thisAccessedTime = metadata.getThisAccessedTime();
          
          //remove sessions from all nodes through manager.remove
          //it is not necessary re-create these session on other nodes,
          //will be created on first request of that nodes
          if ((this.id != null) && (manager != null))
              manager.remove(this);

          this.id = id;
          this.attributes.setSessionId( manager.stripDotSuffix(id) );
          this.metadata.setSessionId( manager.stripDotSuffix(id) );

          if (manager != null){
              this.attributes.putAll(allAttributes);
              
              this.metadata.setCreationTime(creationTime);
              this.metadata.setLastAccessedTime(lastAccessedTime);
              this.metadata.setMaxInactiveInterval(maxInactiveInterval);
              this.metadata.setThisAccessedTime(thisAccessedTime);
              
              manager.add(this);
          }        
          
          tellNew();
      }


      /**
       * Inform the listeners about the new session.
       *
       */
      public void tellNew() {

          // Notify interested session event listeners
          fireSessionEvent(Session.SESSION_CREATED_EVENT, null);

          // Notify interested application event listeners
          Context context = (Context) manager.getContainer();
          Object listeners[] = context.getApplicationLifecycleListeners();
          if (listeners != null) {
              HttpSessionEvent event =
                  new HttpSessionEvent(getSession());
              for (int i = 0; i < listeners.length; i++) {
                  if (!(listeners[i] instanceof HttpSessionListener))
                      continue;
                  HttpSessionListener listener =
                      (HttpSessionListener) listeners[i];
                  try {
                      fireContainerEvent(context,
                                         "beforeSessionCreated",
                                         listener);
                      listener.sessionCreated(event);
                      fireContainerEvent(context,
                                         "afterSessionCreated",
                                         listener);
                  } catch (Throwable t) {
                      try {
                          fireContainerEvent(context,
                                             "afterSessionCreated",
                                             listener);
                      } catch (Exception e) {
                          ;
                      }
                      manager.getContainer().getLogger().error
                          (sm.getString("standardSession.sessionEvent"), t);
                  }
              }
          }

      }


      /**
       * Return descriptive information about this Session implementation and
       * the corresponding version number, in the format
       * <code>&lt;description&gt;/&lt;version&gt;</code>.
       */
      public String getInfo() {

          return (info);

      }


      /**
       * Return the last time the client sent a request associated with this
       * session, as the number of milliseconds since midnight, January 1, 1970
       * GMT.  Actions that your application takes, such as getting or setting
       * a value associated with the session, do not affect the access time.
       */
      public long getLastAccessedTime() {

          if (!isValidInternal()) {
              throw new IllegalStateException
                  (sm.getString("standardSession.getLastAccessedTime.ise"));
          }

          return (this.metadata.getLastAccessedTime());
      }

      /**
       * Return the last client access time without invalidation check
       * @see #getLastAccessedTime().
       */
      public long getLastAccessedTimeInternal() {
          return (this.metadata.getLastAccessedTime());
      }

      /**
       * Return the Manager within which this Session is valid.
       */
      public Manager getManager() {

          return (this.manager);

      }


      /**
       * Set the Manager within which this Session is valid.
       *
       * @param manager The new Manager
       */
      public void setManager(Manager manager) {
          if ( manager == null || ! (manager instanceof InfinispanSessionManagerBase)){
              throw new RuntimeException("Set incorrect session manger. InfinispanSessionManagerBase or any inherited manager must be used!");
          }
          this.manager = (InfinispanSessionManager)manager;

      }


      /**
       * Return the maximum time interval, in seconds, between client requests
       * before the servlet container will invalidate the session.  A negative
       * time indicates that the session should never time out.
       */
      public int getMaxInactiveInterval() {

          return (this.metadata.getMaxInactiveInterval());

      }


      /**
       * Set the maximum time interval, in seconds, between client requests
       * before the servlet container will invalidate the session.  A negative
       * time indicates that the session should never time out.
       *
       * @param interval The new maximum interval
       */
      public void setMaxInactiveInterval(int interval) {

          this.metadata.setMaxInactiveInterval(interval);
          if (isValid && interval == 0) {
              expire();
          }

      }


      /**
       * Set the <code>isNew</code> flag for this session.
       *
       * @param isNew The new value for the <code>isNew</code> flag
       */
      public void setNew(boolean isNew) {

          this.isNew = isNew;

      }


      /**
       * Return the authenticated Principal that is associated with this Session.
       * This provides an <code>Authenticator</code> with a means to cache a
       * previously authenticated Principal, and avoid potentially expensive
       * <code>Realm.authenticate()</code> calls on every request.  If there
       * is no current associated Principal, return <code>null</code>.
       */
      public Principal getPrincipal() {

          return (this.principal);

      }


      /**
       * Set the authenticated Principal that is associated with this Session.
       * This provides an <code>Authenticator</code> with a means to cache a
       * previously authenticated Principal, and avoid potentially expensive
       * <code>Realm.authenticate()</code> calls on every request.
       *
       * @param principal The new Principal, or <code>null</code> if none
       */
      public void setPrincipal(Principal principal) {

          Principal oldPrincipal = this.principal;
          this.principal = principal;
          support.firePropertyChange("principal", oldPrincipal, this.principal);

      }


      /**
       * Return the <code>HttpSession</code> for which this object
       * is the facade.
       */
      public HttpSession getSession() {

          if (facade == null){
              if (SecurityUtil.isPackageProtectionEnabled()){
                  final HttpSession fsession = this;
                  facade = (StandardSessionFacade)AccessController.doPrivileged(new PrivilegedAction(){
                      public Object run(){
                          return new StandardSessionFacade(fsession);
                      }
                  });
              } else {
                  facade = new StandardSessionFacade(this);
              }
          }
          return (facade);

      }


      /**
       * Return the <code>isValid</code> flag for this session.
       */
      public boolean isValid() {

          if (this.expiring) {
              return true;
          }

          if (!this.isValid) {
              return false;
          }

          if (ACTIVITY_CHECK && accessCount.get() > 0) {
              return true;
          }
          
          int maxInactiveInterval = this.metadata.getMaxInactiveInterval();
          long thisAccessedTime = this.metadata.getThisAccessedTime();
          if (maxInactiveInterval >= 0) { 
              long timeNow = System.currentTimeMillis();
              int timeIdle = (int) ((timeNow - thisAccessedTime) / 1000L);
              if (timeIdle >= maxInactiveInterval) {
                  expire(true);
              }
          }

          return (this.isValid);
      }


      /**
       * Set the <code>isValid</code> flag for this session.
       *
       * @param isValid The new value for the <code>isValid</code> flag
       */
      public void setValid(boolean isValid) {
          this.isValid = isValid;
      }


      // ------------------------------------------------- Session Public Methods


      /**
       * Update the accessed time information for this session.  This method
       * should be called by the context when a request comes in for a particular
       * session, even if the application does not reference it.
       */
      public void access() {

          this.metadata.setLastAccessedTime( this.metadata.getThisAccessedTime() );
          this.metadata.setThisAccessedTime( System.currentTimeMillis() );
          
          if (ACTIVITY_CHECK) {
              accessCount.incrementAndGet();
          }

      }


      /**
       * End the access.
       */
      public void endAccess() {

          isNew = false;

          if (ACTIVITY_CHECK) {
              accessCount.decrementAndGet();
          }

      }


      /**
       * Add a session event listener to this component.
       */
      public void addSessionListener(SessionListener listener) {

          listeners.add(listener);

      }


      /**
       * Perform the internal processing required to invalidate this session,
       * without triggering an exception if the session has already expired.
       */
      public void expire() {

          expire(true);

      }


      /**
       * Perform the internal processing required to invalidate this session,
       * without triggering an exception if the session has already expired.
       *
       * @param notify Should we notify listeners about the demise of
       *  this session?
       */
      public void expire(boolean notify) {

          // Check to see if expire is in progress or has previously been called
          if (expiring || !isValid)
              return;

          synchronized (this) {
              // Check again, now we are inside the sync so this code only runs once
              // Double check locking - expiring and isValid need to be volatile
              if (expiring || !isValid)
                  return;

              if (manager == null)
                  return;

              // Mark this session as "being expired"
              expiring = true;
          
              // Notify interested application event listeners
              // FIXME - Assumes we call listeners in reverse order
              Context context = (Context) manager.getContainer();
              
              // The call to expire() may not have been triggered by the webapp.
              // Make sure the webapp's class loader is set when calling the
              // listeners
              ClassLoader oldTccl = null;
              if (context.getLoader() != null &&
                      context.getLoader().getClassLoader() != null) {
                  oldTccl = Thread.currentThread().getContextClassLoader();
                  if (Globals.IS_SECURITY_ENABLED) {
                      PrivilegedAction<Void> pa = new PrivilegedSetTccl(
                              context.getLoader().getClassLoader());
                      AccessController.doPrivileged(pa);
                  } else {
                      Thread.currentThread().setContextClassLoader(
                              context.getLoader().getClassLoader());
                  }
              }
              try {
                  Object listeners[] = context.getApplicationLifecycleListeners();
                  if (notify && (listeners != null)) {
                      HttpSessionEvent event =
                          new HttpSessionEvent(getSession());
                      for (int i = 0; i < listeners.length; i++) {
                          int j = (listeners.length - 1) - i;
                          if (!(listeners[j] instanceof HttpSessionListener))
                              continue;
                          HttpSessionListener listener =
                              (HttpSessionListener) listeners[j];
                          try {
                              fireContainerEvent(context,
                                                 "beforeSessionDestroyed",
                                                 listener);
                              listener.sessionDestroyed(event);
                              fireContainerEvent(context,
                                                 "afterSessionDestroyed",
                                                 listener);
                          } catch (Throwable t) {
                              try {
                                  fireContainerEvent(context,
                                                     "afterSessionDestroyed",
                                                     listener);
                              } catch (Exception e) {
                                  // Ignore
                              }
                              manager.getContainer().getLogger().error
                                  (sm.getString("standardSession.sessionEvent"), t);
                          }
                      }
                  }
              } finally {
                  if (oldTccl != null) {
                      if (Globals.IS_SECURITY_ENABLED) {
                          PrivilegedAction<Void> pa =
                              new PrivilegedSetTccl(oldTccl);
                          AccessController.doPrivileged(pa);
                      } else {
                          Thread.currentThread().setContextClassLoader(oldTccl);
                      }
                  }
              }

              if (ACTIVITY_CHECK) {
                  accessCount.set(0);
              }
              setValid(false);

              /*
               * Compute how long this session has been alive, and update
               * session manager's related properties accordingly
               */
              long timeNow = System.currentTimeMillis();
              long creationTime = this.metadata.getCreationTime();
              int timeAlive = (int) ((timeNow - creationTime)/1000);
              synchronized (manager) {
                  if (timeAlive > manager.getSessionMaxAliveTime()) {
                      manager.setSessionMaxAliveTime(timeAlive);
                  }
                  int numExpired = manager.getExpiredSessions();
                  if (numExpired < Integer.MAX_VALUE) {
                      numExpired++;
                      manager.setExpiredSessions(numExpired);
                  }

                  int average = manager.getSessionAverageAliveTime();
                  // Using long, as otherwise (average * numExpired) might overflow 
                  average = (int) (((((long) average) * (numExpired - 1)) + timeAlive)
                          / numExpired);
                  manager.setSessionAverageAliveTime(average);
              }

              // Remove this session from our manager's active sessions
              manager.remove(this);

              // Notify interested session event listeners
              if (notify) {
                  fireSessionEvent(Session.SESSION_DESTROYED_EVENT, null);
              }

              // Call the logout method
              if (principal instanceof GenericPrincipal) {
                  GenericPrincipal gp = (GenericPrincipal) principal;
                  try {
                      gp.logout();
                  } catch (Exception e) {
                      manager.getContainer().getLogger().error(
                              sm.getString("standardSession.logoutfail"),
                              e);
                  }
              }

              // We have completed expire of this session
              expiring = false;

              // Unbind any objects associated with this session
              String keys[] = keys();
              for (int i = 0; i < keys.length; i++)
                  removeAttributeInternal(keys[i], notify);

          }

      }


      /**
       * Perform the internal processing required to passivate
       * this session.
       */
      public void passivate() {

          // Notify interested session event listeners
          fireSessionEvent(Session.SESSION_PASSIVATED_EVENT, null);

          // Notify ActivationListeners
          HttpSessionEvent event = null;
          String keys[] = keys();
          for (int i = 0; i < keys.length; i++) {
              Object attribute = attributes.get(keys[i]);
              if (attribute instanceof HttpSessionActivationListener) {
                  if (event == null)
                      event = new HttpSessionEvent(getSession());
                  try {
                      ((HttpSessionActivationListener)attribute)
                          .sessionWillPassivate(event);
                  } catch (Throwable t) {
                      manager.getContainer().getLogger().error
                          (sm.getString("standardSession.attributeEvent"), t);
                  }
              }
          }

      }


      /**
       * Perform internal processing required to activate this
       * session.
       */
      public void activate() {

          // Initialize access count
          if (ACTIVITY_CHECK) {
              accessCount = new AtomicInteger();
          }
          
          // Notify interested session event listeners
          fireSessionEvent(Session.SESSION_ACTIVATED_EVENT, null);

          // Notify ActivationListeners
          HttpSessionEvent event = null;
          String keys[] = keys();
          for (int i = 0; i < keys.length; i++) {
              Object attribute = attributes.get(keys[i]);
              if (attribute instanceof HttpSessionActivationListener) {
                  if (event == null)
                      event = new HttpSessionEvent(getSession());
                  try {
                      ((HttpSessionActivationListener)attribute)
                          .sessionDidActivate(event);
                  } catch (Throwable t) {
                      manager.getContainer().getLogger().error
                          (sm.getString("standardSession.attributeEvent"), t);
                  }
              }
          }

      }


      /**
       * Return the object bound with the specified name to the internal notes
       * for this session, or <code>null</code> if no such binding exists.
       *
       * @param name Name of the note to be returned
       */
      public Object getNote(String name) {

          return (notes.get(name));

      }


      /**
       * Return an Iterator containing the String names of all notes bindings
       * that exist for this session.
       */
      public Iterator getNoteNames() {

          return (notes.keySet().iterator());

      }


      /**
       * Release all object references, and initialize instance variables, in
       * preparation for reuse of this object.
       */
      public void recycle() {

          // Reset the instance variables associated with this Session
          attributes.clear();
          attributes.setSessionId("");
          setAuthType(null);
          this.metadata.setCreationTime(0L);
          expiring = false;
          id = null;
          this.metadata.setLastAccessedTime(0L);
          this.metadata.setMaxInactiveInterval(-1);
          notes.clear();
          setPrincipal(null);
          isNew = false;
          isValid = false;
          manager = null;

      }


      /**
       * Remove any object bound to the specified name in the internal notes
       * for this session.
       *
       * @param name Name of the note to be removed
       */
      public void removeNote(String name) {

          notes.remove(name);

      }


      /**
       * Remove a session event listener from this component.
       */
      public void removeSessionListener(SessionListener listener) {

          listeners.remove(listener);

      }


      /**
       * Bind an object to a specified name in the internal notes associated
       * with this session, replacing any existing binding for this name.
       *
       * @param name Name to which the object should be bound
       * @param value Object to be bound to the specified name
       */
      public void setNote(String name, Object value) {

          notes.put(name, value);

      }


      /**
       * Return a string representation of this object.
       */
      public String toString() {

          StringBuffer sb = new StringBuffer();
          sb.append("StandardSession[");
          sb.append(id);
          sb.append("]");
          return (sb.toString());

      }


      /**
       * Return the time when this session was created, in milliseconds since
       * midnight, January 1, 1970 GMT.
       *
       * @exception IllegalStateException if this method is called on an
       *  invalidated session
       */
      public long getCreationTime() {

          if (!isValidInternal())
              throw new IllegalStateException
                  (sm.getString("standardSession.getCreationTime.ise"));

          return (this.metadata.getCreationTime());
      }


      /**
       * Return the ServletContext to which this session belongs.
       */
      public ServletContext getServletContext() {

          if (manager == null)
              return (null);
          Context context = (Context) manager.getContainer();
          if (context == null)
              return (null);
          else
              return (context.getServletContext());

      }


      /**
       * Return the session context with which this session is associated.
       *
       * @deprecated As of Version 2.1, this method is deprecated and has no
       *  replacement.  It will be removed in a future version of the
       *  Java Servlet API.
       */
      public HttpSessionContext getSessionContext() {

          if (sessionContext == null)
              sessionContext = new StandardSessionContext();
          return (sessionContext);

      }


      // ----------------------------------------------HttpSession Public Methods


      /**
       * Return the object bound with the specified name in this session, or
       * <code>null</code> if no object is bound with that name.
       *
       * @param name Name of the attribute to be returned
       *
       * @exception IllegalStateException if this method is called on an
       *  invalidated session
       */
      public Object getAttribute(String name) {

          if (!isValidInternal())
              throw new IllegalStateException
                  (sm.getString("standardSession.getAttribute.ise"));

          if (name == null) return null;

          return (attributes.get(name));

      }


      /**
       * Return an <code>Enumeration</code> of <code>String</code> objects
       * containing the names of the objects bound to this session.
       *
       * @exception IllegalStateException if this method is called on an
       *  invalidated session
       */
      public Enumeration getAttributeNames() {

          if (!isValidInternal())
              throw new IllegalStateException
                  (sm.getString("standardSession.getAttributeNames.ise"));

          return (new Enumerator(attributes.keys(), true));

      }


      /**
       * Return the object bound with the specified name in this session, or
       * <code>null</code> if no object is bound with that name.
       *
       * @param name Name of the value to be returned
       *
       * @exception IllegalStateException if this method is called on an
       *  invalidated session
       *
       * @deprecated As of Version 2.2, this method is replaced by
       *  <code>getAttribute()</code>
       */
      public Object getValue(String name) {

          return (getAttribute(name));

      }


      /**
       * Return the set of names of objects bound to this session.  If there
       * are no such objects, a zero-length array is returned.
       *
       * @exception IllegalStateException if this method is called on an
       *  invalidated session
       *
       * @deprecated As of Version 2.2, this method is replaced by
       *  <code>getAttributeNames()</code>
       */
      public String[] getValueNames() {

          if (!isValidInternal())
              throw new IllegalStateException
                  (sm.getString("standardSession.getValueNames.ise"));

          return (keys());

      }


      /**
       * Invalidates this session and unbinds any objects bound to it.
       *
       * @exception IllegalStateException if this method is called on
       *  an invalidated session
       */
      public void invalidate() {

          if (!isValidInternal())
              throw new IllegalStateException
                  (sm.getString("standardSession.invalidate.ise"));

          // Cause this session to expire
          expire();

      }


      /**
       * Return <code>true</code> if the client does not yet know about the
       * session, or if the client chooses not to join the session.  For
       * example, if the server used only cookie-based sessions, and the client
       * has disabled the use of cookies, then a session would be new on each
       * request.
       *
       * @exception IllegalStateException if this method is called on an
       *  invalidated session
       */
      public boolean isNew() {

          if (!isValidInternal())
              throw new IllegalStateException
                  (sm.getString("standardSession.isNew.ise"));

          return (this.isNew);

      }



      /**
       * Bind an object to this session, using the specified name.  If an object
       * of the same name is already bound to this session, the object is
       * replaced.
       * <p>
       * After this method executes, and if the object implements
       * <code>HttpSessionBindingListener</code>, the container calls
       * <code>valueBound()</code> on the object.
       *
       * @param name Name to which the object is bound, cannot be null
       * @param value Object to be bound, cannot be null
       *
       * @exception IllegalStateException if this method is called on an
       *  invalidated session
       *
       * @deprecated As of Version 2.2, this method is replaced by
       *  <code>setAttribute()</code>
       */
      public void putValue(String name, Object value) {

          setAttribute(name, value);

      }


      /**
       * Remove the object bound with the specified name from this session.  If
       * the session does not have an object bound with this name, this method
       * does nothing.
       * <p>
       * After this method executes, and if the object implements
       * <code>HttpSessionBindingListener</code>, the container calls
       * <code>valueUnbound()</code> on the object.
       *
       * @param name Name of the object to remove from this session.
       *
       * @exception IllegalStateException if this method is called on an
       *  invalidated session
       */
      public void removeAttribute(String name) {

          removeAttribute(name, true);

      }


      /**
       * Remove the object bound with the specified name from this session.  If
       * the session does not have an object bound with this name, this method
       * does nothing.
       * <p>
       * After this method executes, and if the object implements
       * <code>HttpSessionBindingListener</code>, the container calls
       * <code>valueUnbound()</code> on the object.
       *
       * @param name Name of the object to remove from this session.
       * @param notify Should we notify interested listeners that this
       *  attribute is being removed?
       *
       * @exception IllegalStateException if this method is called on an
       *  invalidated session
       */
      public void removeAttribute(String name, boolean notify) {

          // Validate our current state
          if (!isValidInternal())
              throw new IllegalStateException
                  (sm.getString("standardSession.removeAttribute.ise"));

          removeAttributeInternal(name, notify);

      }


      /**
       * Remove the object bound with the specified name from this session.  If
       * the session does not have an object bound with this name, this method
       * does nothing.
       * <p>
       * After this method executes, and if the object implements
       * <code>HttpSessionBindingListener</code>, the container calls
       * <code>valueUnbound()</code> on the object.
       *
       * @param name Name of the object to remove from this session.
       *
       * @exception IllegalStateException if this method is called on an
       *  invalidated session
       *
       * @deprecated As of Version 2.2, this method is replaced by
       *  <code>removeAttribute()</code>
       */
      public void removeValue(String name) {

          removeAttribute(name);

      }


      /**
       * Bind an object to this session, using the specified name.  If an object
       * of the same name is already bound to this session, the object is
       * replaced.
       * <p>
       * After this method executes, and if the object implements
       * <code>HttpSessionBindingListener</code>, the container calls
       * <code>valueBound()</code> on the object.
       *
       * @param name Name to which the object is bound, cannot be null
       * @param value Object to be bound, cannot be null
       *
       * @exception IllegalArgumentException if an attempt is made to add a
       *  non-serializable object in an environment marked distributable.
       * @exception IllegalStateException if this method is called on an
       *  invalidated session
       */
      public void setAttribute(String name, Object value) {
          setAttribute(name,value,true);
      }
      /**
       * Bind an object to this session, using the specified name.  If an object
       * of the same name is already bound to this session, the object is
       * replaced.
       * <p>
       * After this method executes, and if the object implements
       * <code>HttpSessionBindingListener</code>, the container calls
       * <code>valueBound()</code> on the object.
       *
       * @param name Name to which the object is bound, cannot be null
       * @param value Object to be bound, cannot be null
       * @param notify whether to notify session listeners
       * @exception IllegalArgumentException if an attempt is made to add a
       *  non-serializable object in an environment marked distributable.
       * @exception IllegalStateException if this method is called on an
       *  invalidated session
       */

      public void setAttribute(String name, Object value, boolean notify) {

          // Name cannot be null
          if (name == null)
              throw new IllegalArgumentException
                  (sm.getString("standardSession.setAttribute.namenull"));

          // Null value is the same as removeAttribute()
          if (value == null) {
              removeAttribute(name);
              return;
          }

          // Validate our current state
          if (!isValidInternal())
              throw new IllegalStateException
                  (sm.getString("standardSession.setAttribute.ise"));
          if ((manager != null) && manager.getDistributable() &&
            !(value instanceof Serializable)){
              String msg = sm.getString("standardSession.setAttribute.iae", name);
              if ( value != null){
                msg += " Class name: " + value.getClass().getName();
              }
              throw new IllegalArgumentException( msg );
          }
          // Construct an event with the new value
          HttpSessionBindingEvent event = null;

          // Call the valueBound() method if necessary
          if (notify && value instanceof HttpSessionBindingListener) {
              // Don't call any notification if replacing with the same value
              Object oldValue = attributes.get(name);
              if (value != oldValue) {
                  event = new HttpSessionBindingEvent(getSession(), name, value);
                  try {
                      ((HttpSessionBindingListener) value).valueBound(event);
                  } catch (Throwable t){
                      manager.getContainer().getLogger().error
                      (sm.getString("standardSession.bindingEvent"), t); 
                  }
              }
          }

          // Replace or add this attribute
          Object unbound = attributes.put(name, value);

          // Call the valueUnbound() method if necessary
          if (notify && (unbound != null) && (unbound != value) &&
              (unbound instanceof HttpSessionBindingListener)) {
              try {
                  ((HttpSessionBindingListener) unbound).valueUnbound
                      (new HttpSessionBindingEvent(getSession(), name));
              } catch (Throwable t) {
                  manager.getContainer().getLogger().error
                      (sm.getString("standardSession.bindingEvent"), t);
              }
          }
          
          if ( !notify ) return;
          
          // Notify interested application event listeners
          Context context = (Context) manager.getContainer();
          Object listeners[] = context.getApplicationEventListeners();
          if (listeners == null)
              return;
          for (int i = 0; i < listeners.length; i++) {
              if (!(listeners[i] instanceof HttpSessionAttributeListener))
                  continue;
              HttpSessionAttributeListener listener =
                  (HttpSessionAttributeListener) listeners[i];
              try {
                  if (unbound != null) {
                      fireContainerEvent(context,
                                         "beforeSessionAttributeReplaced",
                                         listener);
                      if (event == null) {
                          event = new HttpSessionBindingEvent
                              (getSession(), name, unbound);
                      }
                      listener.attributeReplaced(event);
                      fireContainerEvent(context,
                                         "afterSessionAttributeReplaced",
                                         listener);
                  } else {
                      fireContainerEvent(context,
                                         "beforeSessionAttributeAdded",
                                         listener);
                      if (event == null) {
                          event = new HttpSessionBindingEvent
                              (getSession(), name, value);
                      }
                      listener.attributeAdded(event);
                      fireContainerEvent(context,
                                         "afterSessionAttributeAdded",
                                         listener);
                  }
              } catch (Throwable t) {
                  try {
                      if (unbound != null) {
                          fireContainerEvent(context,
                                             "afterSessionAttributeReplaced",
                                             listener);
                      } else {
                          fireContainerEvent(context,
                                             "afterSessionAttributeAdded",
                                             listener);
                      }
                  } catch (Exception e) {
                      ;
                  }
                  manager.getContainer().getLogger().error
                      (sm.getString("standardSession.attributeEvent"), t);
              }
          }

      }


      // ------------------------------------------ HttpSession Protected Methods


      /**
       * Return the <code>isValid</code> flag for this session without any expiration
       * check.
       */
      protected boolean isValidInternal() {
          return (this.isValid || this.expiring);
      }

      /**
       * Exclude attribute that cannot be serialized.
       * @param name the attribute's name
       */
      protected boolean exclude(String name){

          for (int i = 0; i < excludedAttributes.length; i++) {
              if (name.equalsIgnoreCase(excludedAttributes[i]))
                  return true;
          }

          return false;
      }


      // ------------------------------------------------------ Protected Methods


      /**
       * Fire container events if the Context implementation is the
       * <code>org.apache.catalina.core.StandardContext</code>.
       *
       * @param context Context for which to fire events
       * @param type Event type
       * @param data Event data
       *
       * @exception Exception occurred during event firing
       */
      protected void fireContainerEvent(Context context,
                                      String type, Object data)
          throws Exception {

          if (context instanceof StandardContext) {
              ((StandardContext) context).fireContainerEvent(type, data);
          }
      }
                                        


      /**
       * Notify all session event listeners that a particular event has
       * occurred for this Session.  The default implementation performs
       * this notification synchronously using the calling thread.
       *
       * @param type Event type
       * @param data Event data
       */
      public void fireSessionEvent(String type, Object data) {
          if (listeners.size() < 1)
              return;
          SessionEvent event = new SessionEvent(this, type, data);
          SessionListener list[] = new SessionListener[0];
          synchronized (listeners) {
              list = (SessionListener[]) listeners.toArray(list);
          }

          for (int i = 0; i < list.length; i++){
              ((SessionListener) list[i]).sessionEvent(event);
          }

      }


      /**
       * Return the names of all currently defined session attributes
       * as an array of Strings.  If there are no defined attributes, a
       * zero-length array is returned.
       */
      protected String[] keys() {

          return ((String[]) attributes.keys().toArray(EMPTY_ARRAY));

      }


      /**
       * Remove the object bound with the specified name from this session.  If
       * the session does not have an object bound with this name, this method
       * does nothing.
       * <p>
       * After this method executes, and if the object implements
       * <code>HttpSessionBindingListener</code>, the container calls
       * <code>valueUnbound()</code> on the object.
       *
       * @param name Name of the object to remove from this session.
       * @param notify Should we notify interested listeners that this
       *  attribute is being removed?
       */
      protected void removeAttributeInternal(String name, boolean notify) {

          // Avoid NPE
          if (name == null) return;

          // Remove this attribute from our collection
          Object value = attributes.remove(name);

          // Do we need to do valueUnbound() and attributeRemoved() notification?
          if (!notify || (value == null)) {
              return;
          }

          // Call the valueUnbound() method if necessary
          HttpSessionBindingEvent event = null;
          if (value instanceof HttpSessionBindingListener) {
              event = new HttpSessionBindingEvent(getSession(), name, value);
              ((HttpSessionBindingListener) value).valueUnbound(event);
          }

          // Notify interested application event listeners
          Context context = (Context) manager.getContainer();
          Object listeners[] = context.getApplicationEventListeners();
          if (listeners == null)
              return;
          for (int i = 0; i < listeners.length; i++) {
              if (!(listeners[i] instanceof HttpSessionAttributeListener))
                  continue;
              HttpSessionAttributeListener listener =
                  (HttpSessionAttributeListener) listeners[i];
              try {
                  fireContainerEvent(context,
                                     "beforeSessionAttributeRemoved",
                                     listener);
                  if (event == null) {
                      event = new HttpSessionBindingEvent
                          (getSession(), name, value);
                  }
                  listener.attributeRemoved(event);
                  fireContainerEvent(context,
                                     "afterSessionAttributeRemoved",
                                     listener);
              } catch (Throwable t) {
                  try {
                      fireContainerEvent(context,
                                         "afterSessionAttributeRemoved",
                                         listener);
                  } catch (Exception e) {
                      ;
                  }
                  manager.getContainer().getLogger().error
                      (sm.getString("standardSession.attributeEvent"), t);
              }
          }

      }


      private static class PrivilegedSetTccl
      implements PrivilegedAction<Void> {

          private ClassLoader cl;

          PrivilegedSetTccl(ClassLoader cl) {
              this.cl = cl;
          }

          public Void run() {
              Thread.currentThread().setContextClassLoader(cl);
              return null;
          }
      }

  }


  // ------------------------------------------------------------ Protected Class


  /**
   * This class is a dummy implementation of the <code>HttpSessionContext</code>
   * interface, to conform to the requirement that such an object be returned
   * when <code>HttpSession.getSessionContext()</code> is called.
   *
   * @author Craig R. McClanahan
   *
   * @deprecated As of Java Servlet API 2.1 with no replacement.  The
   *  interface will be removed in a future version of this API.
   */

  final class StandardSessionContext implements HttpSessionContext {


      protected HashMap dummy = new HashMap();

      /**
       * Return the session identifiers of all sessions defined
       * within this context.
       *
       * @deprecated As of Java Servlet API 2.1 with no replacement.
       *  This method must return an empty <code>Enumeration</code>
       *  and will be removed in a future version of the API.
       */
      public Enumeration getIds() {

          return (new Enumerator(dummy));

      }


      /**
       * Return the <code>HttpSession</code> associated with the
       * specified session identifier.
       *
       * @param id Session identifier for which to look up a session
       *
       * @deprecated As of Java Servlet API 2.1 with no replacement.
       *  This method must return null and will be removed in a
       *  future version of the API.
       */
      public HttpSession getSession(String id) {

          return (null);

      }
  }
  
  /**
   * Session metadata distributed attributes.
   * Separated cache.
   * 
   * @author zhenek
   *
   */
class SessionMetaAttributes {
    /**
     * The time this session was created, in milliseconds since midnight,
     * January 1, 1970 GMT.
     */
    private static final String CREATION_TIME = "creationTime";
    
    /**
     * The last accessed time for this Session.
     */
    private static final String LAST_ACCESSED_TIME = "lastAccessedTime";
    
    /**
     * The current accessed time for this session.
     */
    private static final String THIS_ACCESSED_TIME = "thisAccessedTime";
    
    /**
     * The maximum time interval, in seconds, between client requests before the
     * servlet container may invalidate this session. A negative time indicates
     * that the session should never time out.
     */
    private static final String MAX_INACTIVE_INTERVAL = "maxInactiveInterval";
    
    private String sessionId;

    private Cache<String, ?> metadataCache;

    /**
     * Constructor
     * 
     * @param metadataCache
     * @param sessionId
     */
    public SessionMetaAttributes(Cache<String, ?> metadataCache,
            String sessionId) {
        this.metadataCache = metadataCache;
        this.sessionId = sessionId;
        
        FineGrainedAtomicMap<String, Object> cacheItem = getCache();
        cacheItem.put(CREATION_TIME, 0L);
        cacheItem.put(LAST_ACCESSED_TIME, 0L);
        cacheItem.put(MAX_INACTIVE_INTERVAL, -1);
        cacheItem.put(THIS_ACCESSED_TIME, 0L);
    }

    public long getCreationTime() {
            return (Long)this.getCache().get( CREATION_TIME );
        }

    public void setCreationTime(long creationTime) {
        this.getCache().put( CREATION_TIME, creationTime );
    }

    public long getLastAccessedTime() {
        return (Long)this.getCache().get( LAST_ACCESSED_TIME );
    }

    public void setLastAccessedTime(long lastAccessedTime) {
        this.getCache().put( LAST_ACCESSED_TIME, lastAccessedTime );
    }

    public int getMaxInactiveInterval() {
        return (Integer)this.getCache().get( MAX_INACTIVE_INTERVAL );
    }

    public void setMaxInactiveInterval(int maxInactiveInterval) {
        this.getCache().put( MAX_INACTIVE_INTERVAL, maxInactiveInterval );
    }
    
    public long getThisAccessedTime() {
        return (Long)this.getCache().get( THIS_ACCESSED_TIME );
    }

    public void setThisAccessedTime(long thisAccessedTime) {
        this.getCache().put( THIS_ACCESSED_TIME, thisAccessedTime );
    }

    /**
     * Get one session metadata
     * 
     * @return
     */
    private FineGrainedAtomicMap<String, Object> getCache() {
        FineGrainedAtomicMap<String, Object> cacheItem = 
                AtomicMapLookup.getFineGrainedAtomicMap(metadataCache, sessionId);
        return cacheItem;
    }
    

    /**
     * Set session id if changed
     */
    public void setSessionId(String id){
        this.sessionId = id;
    }
  
    public String getSessionId(){
        return this.sessionId;
    }
}
  
  /**
   * User session attributes.
   * Separated cache to meta attributes
   * 
   * @author zhenek
   *
   */
  class SessionAttributesCache {
  
    private String sessionId;
    private Cache<String, ?> attributesCache;

    public SessionAttributesCache (Cache<String,?> attributesCache, String sessionId){
          this.attributesCache = attributesCache;
          this.sessionId = sessionId;
      }


    public void clear() {
        FineGrainedAtomicMap<String, Object> attributes = getCachedAttributes();
       attributes.clear();
    }


    /**
      * put new attribute value
      */
    public Object put(String key, Object value) {
        FineGrainedAtomicMap<String, Object> attributes = getCachedAttributes();
        
        return attributes.put(key, value);
    }

    /**
      * get all attribute names
      */
    public Set<String> keys() {
        FineGrainedAtomicMap<String, Object> attributes = getCachedAttributes();
        
        return attributes.keySet();
    }

    /**
      * Remove attribute from session
      */
    public Object remove(String key) {
        FineGrainedAtomicMap<String, Object> attributes = getCachedAttributes();
        
        return attributes.remove(key);
    }

    /**
     * Get attribute of distributed cache attributes
     */
    public Object get(String key) {
        FineGrainedAtomicMap<String, Object> attributes = getCachedAttributes();
        
        return attributes.get( key );
    }
    
    /**
     * Get all session attributes as Map
     * @return
     */
    public Map<String, Object> getAll(){
        FineGrainedAtomicMap<String, Object> attributes = getCachedAttributes();
        Map<String, Object> attribs = new HashMap<String, Object>();
        attribs.putAll( attributes );
        
        return attribs;
    }
    
    /**
     * Set all attributes to session attributes
     * @param attributes
     */
    public void putAll(Map<String, Object> attributes){
        FineGrainedAtomicMap<String, Object> attribs = getCachedAttributes();
        attribs.putAll(attributes);
    }

    /**
     * get attributes reference to distributed cache
     */
    private FineGrainedAtomicMap<String, Object> getCachedAttributes() {
        //use atomic map to store one session attributes.
        //doesn't use distributed transaction, use <invocationBatching enabled="true"/> in _session_attr named cache
       FineGrainedAtomicMap<String, Object> attributes = AtomicMapLookup.getFineGrainedAtomicMap(attributesCache, sessionId);
        
        return attributes;
    }
    
    /**
     * Set session id if changed
     */
    public void setSessionId(String id){
        this.sessionId = id;
    }
  
    public String getSessionId(){
        return this.sessionId;
    }
}
