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
package org.apache.catalina.session.infinispan;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.SessionEvent;
import org.apache.catalina.SessionListener;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.security.SecurityUtil;
import org.apache.catalina.session.Constants;
import org.apache.catalina.session.StandardSessionFacade;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;
import org.infinispan.Cache;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Distributed session implementation. Uses one cache for both data and metadata.
 * Key is NAMESPACE+session id
 * This session object is "shell" which is instantiated locally in server node which request session to get or
 * write session data.
 *
 * @author ZHenek
 *
 */

public class InfinispanSession implements HttpSession, Session, Serializable {

    private static final long serialVersionUID = 1L;

    protected static final boolean STRICT_SERVLET_COMPLIANCE;

    protected static final boolean ACTIVITY_CHECK;

    protected static final boolean LAST_ACCESS_AT_START;

    /**
     * attributes and metadata cache, shared using namespaces
     */
    private Cache<String, ?> cache;

    /**
     * Session metadata
     */
    private SessionMetaAttributes metadata;


    static {
        STRICT_SERVLET_COMPLIANCE = Globals.STRICT_SERVLET_COMPLIANCE;

        String activityCheck = System.getProperty(
                "org.apache.catalina.session.InfinispanSession.ACTIVITY_CHECK");
        if (activityCheck == null) {
            ACTIVITY_CHECK = STRICT_SERVLET_COMPLIANCE;
        } else {
            ACTIVITY_CHECK =
                Boolean.valueOf(activityCheck).booleanValue();
        }

        String lastAccessAtStart = System.getProperty(
                "org.apache.catalina.session.InfinispanSession.LAST_ACCESS_AT_START");
        if (lastAccessAtStart == null) {
            LAST_ACCESS_AT_START = STRICT_SERVLET_COMPLIANCE;
        } else {
            LAST_ACCESS_AT_START =
                Boolean.valueOf(lastAccessAtStart).booleanValue();
        }
    }


    // ----------------------------------------------------------- Constructors

    /**
     * Construct a new Session associated with the specified Manager and copy data and metadata from existing standard session
     *
     * @param manager The manager with which this Session is associated
     * @param cache  attributes and metadata cache
     * @param session standard session to create distributed session
     */
    public InfinispanSession(InfinispanSessionManager manager, Cache<String, ?> cache, StandardSessionWrapper session) {
        this(manager, cache, session.getId());

        //copy attributes
        Enumeration<String> attributeNames = session.getAttributeNames();
        while ( attributeNames.hasMoreElements()){
            String attrName = attributeNames.nextElement();

            this.attributes.put(attrName, session.getAttribute(attrName));
        }

        //copy metadata
        this.authType = session.getAuthType();
        this.metadata.setCreationTime(session.getCreationTime());
        //excludedAttributes - is not used - InfinispanSessionManager doesn't persist sessions
        this.expiring = false;
        //id - already set
        this.metadata.setLastAccessedTime(session.getLastAccessedTime());

        this.listeners = new ArrayList<SessionListener>(session.getListeners());
        //this.manager - already set
        this.metadata.setMaxInactiveInterval(session.getMaxInactiveInterval());
        this.isNew = session.isNew();
        this.isValid = session.isValid();
        //notes
        Iterator<String> noteNames = session.getNoteNames();
        while( noteNames.hasNext()){
            String noteName = noteNames.next();
            this.notes.put(noteName, session.getNote(noteName));
        }
        this.principal = session.getPrincipal();
        this.sessionContext = null; //deprecated
        this.support = new PropertyChangeSupport(this);
        this.metadata.setThisAccessedTime(session.getThisAccessedTime());
        this.accessCount = new AtomicInteger(0);
    }
    /**
     * Construct a new Session associated with the specified Manager.
     *
     * @param manager The manager with which this Session is associated
     * @param cache  attributes and metadata cache
     * @param sessionId id of newly created session
     */
    public InfinispanSession(InfinispanSessionManager manager, Cache<String, ?> cache, String sessionId) {

        super();
        this.id = sessionId;
        this.manager = manager;
        this.cache = cache;
        //store session without suffix to avoid session rename after cluster node disabled by load balancer
        String sessionIdWithoutJvmRoute = manager.stripJvmRoute(sessionId);
        this.attributes = new SessionAttributes(cache, sessionIdWithoutJvmRoute);

        this.metadata = new SessionMetaAttributes(cache, sessionIdWithoutJvmRoute);


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
     * The collection of user data attributes associated with this Session.
     */
    private SessionAttributes attributes;


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
        Globals.SUBJECT_ATTR,
        Globals.GSS_CREDENTIAL_ATTR
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
    protected static final String info = "InfinispanSession/1.0";

    /**
     * The session event listeners for this Session.
     */
    protected transient List<SessionListener> listeners =
        new ArrayList<SessionListener>();


    /**
     * The Manager with which this Session is associated.
     */
    protected transient InfinispanSessionManager manager = null;


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
    protected transient Map<String, Object> notes = new Hashtable<String, Object>();


    /**
     * The authenticated Principal associated with this session, if any.
     * <b>IMPLEMENTATION NOTE:</b>  This object is <i>not</i> saved and
     * restored across session serializations!
     */
    protected transient Principal principal = null;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * The HTTP session context associated with this session.
     */
    @Deprecated
    protected static volatile
            javax.servlet.http.HttpSessionContext sessionContext = null;


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
    @Override
    public String getAuthType() {

        return (this.authType);

    }


    /**
     * Set the authentication type used to authenticate our cached
     * Principal, if any.
     *
     * @param authType The new cached authentication type
     */
    @Override
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
    @Override
    public void setCreationTime(long time) {

        this.metadata.setCreationTime(time);
        this.metadata.setLastAccessedTime(time);
        this.metadata.setThisAccessedTime(time);

    }


    /**
     * Return the session identifier for this session.
     */
    @Override
    public String getId() {

        return (this.id);

    }


    /**
     * Return the session identifier for this session.
     */
    @Override
    public String getIdInternal() {

        return (this.id);

    }


    /**
     * Set the session identifier for this session.
     *
     * @param id The new session identifier
     */
    @Override
    public void setId(String id) {
        setId(id, true);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void setId(String id, boolean notify) {
        if ( id == null ){
            Session tmpSession = manager.createSession(null);
            id = tmpSession.getId();
        }

        this.id = id;

        String strippedSessionId = manager.stripDotSuffix(this.id);
        this.metadata.setSessionId(strippedSessionId);
        this.attributes.setSessionId(strippedSessionId);

        if (notify) {
            tellNew();
        }
    }


    /**
     * Inform the listeners about the new session.
     *
     */
    public void tellNew() {
        //TODO distributed mode notify other nodes too -- use infinispan listener add item to cache
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
                    context.fireContainerEvent("beforeSessionCreated",
                            listener);
                    listener.sessionCreated(event);
                    context.fireContainerEvent("afterSessionCreated", listener);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    try {
                        context.fireContainerEvent("afterSessionCreated",
                                listener);
                    } catch (Exception e) {
                        // Ignore
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
    @Override
    public String getInfo() {

        return (info);

    }


    /**
     * Return the last time the client sent a request associated with this
     * session, as the number of milliseconds since midnight, January 1, 1970
     * GMT.  Actions that your application takes, such as getting or setting
     * a value associated with the session, do not affect the access time.
     * This one gets updated whenever a request starts.
     */
    @Override
    public long getThisAccessedTime() {

        if (!isValidInternal()) {
            throw new IllegalStateException
                (sm.getString("standardSession.getThisAccessedTime.ise"));
        }

        return getThisAccessedTimeInternal();
    }

    /**
     * Return the last client access time without invalidation check
     * @see #getThisAccessedTime()
     */
    @Override
    public long getThisAccessedTimeInternal() {
        return (this.metadata.getThisAccessedTime());
    }

    /**
     * Return the last time the client sent a request associated with this
     * session, as the number of milliseconds since midnight, January 1, 1970
     * GMT.  Actions that your application takes, such as getting or setting
     * a value associated with the session, do not affect the access time.
     * This one gets updated whenever a request finishes.
     */
    @Override
    public long getLastAccessedTime() {

        if (!isValidInternal()) {
            throw new IllegalStateException
                (sm.getString("standardSession.getLastAccessedTime.ise"));
        }

        return this.getLastAccessedTimeInternal();
    }

    /**
     * Return the last client access time without invalidation check
     * @see #getLastAccessedTime()
     */
    @Override
    public long getLastAccessedTimeInternal() {
        return this.metadata.getLastAccessedTime();
    }

    /**
     * Return the Manager within which this Session is valid.
     */
    @Override
    public Manager getManager() {

        return (this.manager);

    }


    /**
     * Set the Manager within which this Session is valid.
     *
     * @param manager The new Manager
     */
    @Override
    public void setManager(Manager manager) {
        if ( manager instanceof InfinispanSessionManager){
         this.manager = (InfinispanSessionManager)manager;
        } else {
            throw new RuntimeException("Manager is not infinispan session manager");
        }
    }


    /**
     * Return the maximum time interval, in seconds, between client requests
     * before the servlet container will invalidate the session.  A negative
     * time indicates that the session should never time out.
     */
    @Override
    public int getMaxInactiveInterval() {

        return this.metadata.getMaxInactiveInterval();

    }


    /**
     * Set the maximum time interval, in seconds, between client requests
     * before the servlet container will invalidate the session.  A zero or
     * negative time indicates that the session should never time out.
     *
     * @param interval The new maximum interval
     */
    @Override
    public void setMaxInactiveInterval(int interval) {
        this.metadata.setMaxInactiveInterval( interval );
    }


    /**
     * Set the <code>isNew</code> flag for this session.
     *
     * @param isNew The new value for the <code>isNew</code> flag
     */
    @Override
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
    @Override
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
    @Override
    public void setPrincipal(Principal principal) {

        Principal oldPrincipal = this.principal;
        this.principal = principal;
        support.firePropertyChange("principal", oldPrincipal, this.principal);

    }


    /**
     * Return the <code>HttpSession</code> for which this object
     * is the facade.
     */
    @Override
    public HttpSession getSession() {

        if (facade == null){
            if (SecurityUtil.isPackageProtectionEnabled()){
                final InfinispanSession fsession = this;
                facade = AccessController.doPrivileged(
                        new PrivilegedAction<StandardSessionFacade>(){
                    @Override
                    public StandardSessionFacade run(){
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
    @Override
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

        long maxInactiveInterval = this.metadata.getMaxInactiveInterval();
        long lastAccessedTime = this.metadata.getLastAccessedTime();
        long thisAccessedTime = this.metadata.getThisAccessedTime();

        if (maxInactiveInterval > 0) {
            long timeNow = System.currentTimeMillis();
            int timeIdle;
            if (LAST_ACCESS_AT_START) {
                timeIdle = (int) ((timeNow - lastAccessedTime) / 1000L);
            } else {
                timeIdle = (int) ((timeNow - thisAccessedTime) / 1000L);
            }
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
    @Override
    public void setValid(boolean isValid) {
        this.isValid = isValid;
    }


    // ------------------------------------------------- Session Public Methods


    /**
     * Update the accessed time information for this session.  This method
     * should be called by the context when a request comes in for a particular
     * session, even if the application does not reference it.
     */
    @Override
    public void access() {

        long thisAccessedTime = System.currentTimeMillis();
        this.metadata.setThisAccessedTime(thisAccessedTime);

        if (ACTIVITY_CHECK) {
            accessCount.incrementAndGet();
        }

    }


    /**
     * End the access.
     */
    @Override
    public void endAccess() {

        isNew = false;

        /**
         * The servlet spec mandates to ignore request handling time
         * in lastAccessedTime.
         */
        if (LAST_ACCESS_AT_START) {
            this.metadata.setLastAccessedTime(this.metadata.getThisAccessedTime());
            long thisAccessedTime = System.currentTimeMillis();
            this.metadata.setThisAccessedTime( thisAccessedTime );
        } else {
            long thisAccessedTime = System.currentTimeMillis();
            this.metadata.setThisAccessedTime( thisAccessedTime);
            this.metadata.setLastAccessedTime( thisAccessedTime );
        }

        if (ACTIVITY_CHECK) {
            accessCount.decrementAndGet();
        }

    }


    /**
     * Add a session event listener to this component.
     */
    @Override
    public void addSessionListener(SessionListener listener) {

        listeners.add(listener);

    }


    /**
     * Perform the internal processing required to invalidate this session,
     * without triggering an exception if the session has already expired.
     */
    @Override
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
                            context.fireContainerEvent("beforeSessionDestroyed",
                                    listener);
                            listener.sessionDestroyed(event);
                            context.fireContainerEvent("afterSessionDestroyed",
                                    listener);
                        } catch (Throwable t) {
                            ExceptionUtils.handleThrowable(t);
                            try {
                                context.fireContainerEvent(
                                        "afterSessionDestroyed", listener);
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

            // Remove this session from our manager's active sessions
            manager.remove(this, true);

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
     * Return the object bound with the specified name to the internal notes
     * for this session, or <code>null</code> if no such binding exists.
     *
     * @param name Name of the note to be returned
     */
    @Override
    public Object getNote(String name) {

        return (notes.get(name));

    }


    /**
     * Return an Iterator containing the String names of all notes bindings
     * that exist for this session.
     */
    @Override
    public Iterator<String> getNoteNames() {

        return (notes.keySet().iterator());

    }


    /**
     * Release all object references, and initialize instance variables, in
     * preparation for reuse of this object.
     */
    @Override
    public void recycle() {
        //TODO better recycle - will be used in remove session to remove cached metadata and attributes from cache
        // Reset the instance variables associated with this Session
        attributes.clear();
        setAuthType(null);
        this.metadata.setCreationTime(0L);
        expiring = false;
        id = null;
        this.metadata.setLastAccessedTime(0L);
        this.setMaxInactiveInterval(-1);
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
    @Override
    public void removeNote(String name) {

        notes.remove(name);

    }


    /**
     * Remove a session event listener from this component.
     */
    @Override
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
    @Override
    public void setNote(String name, Object value) {

        notes.put(name, value);

    }


    /**
     * Return a string representation of this object.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append("InfinispanSession[");
        sb.append(id);
        sb.append("]");
        return (sb.toString());

    }


    // ------------------------------------------------ Session Package Methods

    // ------------------------------------------------- HttpSession Properties


    /**
     * Return the time when this session was created, in milliseconds since
     * midnight, January 1, 1970 GMT.
     *
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     */
    @Override
    public long getCreationTime() {

        if (!isValidInternal())
            throw new IllegalStateException
                (sm.getString("standardSession.getCreationTime.ise"));

        return this.getCreationTimeInternal();

    }


    /**
     * Return the time when this session was created, in milliseconds since
     * midnight, January 1, 1970 GMT, bypassing the session validation checks.
     */
    @Override
    public long getCreationTimeInternal() {
        return this.metadata.getCreationTime();
    }


    /**
     * Return the ServletContext to which this session belongs.
     */
    @Override
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
    @Override
    @Deprecated
    public javax.servlet.http.HttpSessionContext getSessionContext() {

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
    @Override
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
    @Override
    public Enumeration<String> getAttributeNames() {

        if (!isValidInternal())
            throw new IllegalStateException
                (sm.getString("standardSession.getAttributeNames.ise"));

        Set<String> names = new HashSet<String>();
        names.addAll(attributes.getAll().keySet());
        return Collections.enumeration(names);
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
    @Override
    @Deprecated
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
    @Override
    @Deprecated
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
    @Override
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
    @Override
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
    @Override
    @Deprecated
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
    @Override
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
    @Override
    @Deprecated
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
    @Override
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
            throw new IllegalStateException(sm.getString(
                    "standardSession.setAttribute.ise", getIdInternal()));
        if ((manager != null) && manager.getDistributable() &&
          !isAttributeDistributable(name, value))
            throw new IllegalArgumentException
                (sm.getString("standardSession.setAttribute.iae", name));
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
                ExceptionUtils.handleThrowable(t);
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
                    context.fireContainerEvent("beforeSessionAttributeReplaced",
                            listener);
                    if (event == null) {
                        event = new HttpSessionBindingEvent
                            (getSession(), name, unbound);
                    }
                    listener.attributeReplaced(event);
                    context.fireContainerEvent("afterSessionAttributeReplaced",
                            listener);
                } else {
                    context.fireContainerEvent("beforeSessionAttributeAdded",
                            listener);
                    if (event == null) {
                        event = new HttpSessionBindingEvent
                            (getSession(), name, value);
                    }
                    listener.attributeAdded(event);
                    context.fireContainerEvent("afterSessionAttributeAdded",
                            listener);
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                try {
                    if (unbound != null) {
                        context.fireContainerEvent(
                                "afterSessionAttributeReplaced", listener);
                    } else {
                        context.fireContainerEvent("afterSessionAttributeAdded",
                                listener);
                    }
                } catch (Exception e) {
                    // Ignore
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
     * Check whether the Object can be distributed. This implementation
     * simply checks for serializability. Derived classes might use other
     * distribution technology not based on serialization and can extend
     * this check.
     * @param name The name of the attribute to check
     * @param value The value of the attribute to check
     * @return true if the attribute is distributable, false otherwise
     */
    protected boolean isAttributeDistributable(String name, Object value) {
        return value instanceof Serializable;
    }

    /**
     * Exclude standard attributes that cannot be serialized.
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
     *
     * @deprecated  No longer necessary since {@link org.apache.catalina.core.StandardContext} implements
     *              the {@link org.apache.catalina.Container} interface.
     *
     */
    @Deprecated
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
            list = listeners.toArray(list);
        }

        for (int i = 0; i < list.length; i++){
            (list[i]).sessionEvent(event);
        }

    }


    /**
     * Return the names of all currently defined session attributes
     * as an array of Strings.  If there are no defined attributes, a
     * zero-length array is returned.
     */
    protected String[] keys() {

        return attributes.getAll().keySet().toArray(EMPTY_ARRAY);

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
                context.fireContainerEvent("beforeSessionAttributeRemoved",
                        listener);
                if (event == null) {
                    event = new HttpSessionBindingEvent
                        (getSession(), name, value);
                }
                listener.attributeRemoved(event);
                context.fireContainerEvent("afterSessionAttributeRemoved",
                        listener);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                try {
                    context.fireContainerEvent("afterSessionAttributeRemoved",
                            listener);
                } catch (Exception e) {
                    // Ignore
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

        @Override
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

@Deprecated
final class StandardSessionContext
        implements javax.servlet.http.HttpSessionContext {

    private static final List<String> emptyString = Collections.emptyList();

    /**
     * Return the session identifiers of all sessions defined
     * within this context.
     *
     * @deprecated As of Java Servlet API 2.1 with no replacement.
     *  This method must return an empty <code>Enumeration</code>
     *  and will be removed in a future version of the API.
     */
    @Override
    @Deprecated
    public Enumeration<String> getIds() {
        return Collections.enumeration(emptyString);
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
    @Override
    @Deprecated
    public HttpSession getSession(String id) {
        return (null);
    }
}