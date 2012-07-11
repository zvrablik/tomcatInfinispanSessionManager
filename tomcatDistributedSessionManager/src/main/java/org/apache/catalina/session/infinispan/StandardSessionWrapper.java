/* --------------------------------------------------------------*\
| Copyright (C) e-Spatial Solutions Limited, All rights reserved. |
\* --------------------------------------------------------------*/
package org.apache.catalina.session.infinispan;

import org.apache.catalina.Manager;
import org.apache.catalina.SessionListener;
import org.apache.catalina.session.StandardSession;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

/**
 *
 * Wrapper of StandardSession. This works only if the protected methods are not used in ManagerBase which is used
 * in InfinispanSessionManager
 * StandardSessionWrapper
 * User: zvrablikhenek
 * Since: 7/2/12
 */
public class StandardSessionWrapper extends StandardSession {


    private static final long serialVersionUID = 1L;
    private StandardSession session;

    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new Session associated with the specified Manager.
     *
     * @param session standard session
     */
    public StandardSessionWrapper(Manager manager, StandardSession session) {
        super(manager);
        this.session = session;

    }

    /**
     * The session event listeners for this Session.
     */
    protected transient ArrayList<SessionListener> w_listeners =
            new ArrayList<SessionListener>();


    /**
     * get all assigned listeners
     */
    protected List<SessionListener> getListeners(){
        return this.w_listeners;
    }


    /**
     * Return the authentication type used to authenticate our cached
     * Principal, if any.
     */
    @Override
    public String getAuthType() {
        return session.getAuthType();
    }


    /**
     * Set the authentication type used to authenticate our cached
     * Principal, if any.
     *
     * @param authType The new cached authentication type
     */
    @Override
    public void setAuthType(String authType) {
        session.setAuthType(authType);
    }

    /**
     * Set the creation time for this session.  This method is called by the
     * Manager when an existing Session instance is reused.
     *
     * @param time The new creation time
     */
    @Override
    public void setCreationTime(long time) {
        session.setCreationTime(time);
    }


    /**
     * Return the session identifier for this session.
     */
    @Override
    public String getId() {
        return session.getId();
    }

    /**
     * Return the session identifier for this session.
     */
    @Override
    public String getIdInternal() {
        return session.getIdInternal();
    }

    /**
     * Set the session identifier for this session.
     *
     * @param id The new session identifier
     */
    @Override
    public void setId(String id) {
        session.setId(id);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void setId(String id, boolean notify) {
        setId(id, notify);
    }


    /**
     * Inform the listeners about the new session.
     *
     */
    @Override
    public void tellNew() {
        session.tellNew();
    }


    /**
     * Return descriptive information about this Session implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    @Override
    public String getInfo() {
       return session.getInfo();
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
        return session.getThisAccessedTime();
    }

    /**
     * Return the last client access time without invalidation check
     * @see #getThisAccessedTime()
     */
    @Override
    public long getThisAccessedTimeInternal() {
        return session.getThisAccessedTimeInternal();
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
        return session.getLastAccessedTime();
    }

    /**
     * Return the last client access time without invalidation check
     * @see #getLastAccessedTime()
     */
    @Override
    public long getLastAccessedTimeInternal() {
        return session.getLastAccessedTimeInternal();
    }

    /**
     * Return the Manager within which this Session is valid.
     */
    @Override
    public Manager getManager() {
        return session.getManager();
    }


    /**
     * Set the Manager within which this Session is valid.
     *
     * @param manager The new Manager
     */
    @Override
    public void setManager(Manager manager) {
        session.setManager(manager);
    }


    /**
     * Return the maximum time interval, in seconds, between client requests
     * before the servlet container will invalidate the session.  A negative
     * time indicates that the session should never time out.
     */
    @Override
    public int getMaxInactiveInterval() {
        return session.getMaxInactiveInterval();
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
        session.setMaxInactiveInterval(interval);
    }

    /**
     * Set the <code>isNew</code> flag for this session.
     *
     * @param isNew The new value for the <code>isNew</code> flag
     */
    @Override
    public void setNew(boolean isNew) {
        session.setNew(isNew);
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
        return session.getPrincipal();
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
        session.setPrincipal(principal);
    }


    /**
     * Return the <code>HttpSession</code> for which this object
     * is the facade.
     */
    @Override
    public HttpSession getSession() {
        return session.getSession();
    }


    /**
     * Return the <code>isValid</code> flag for this session.
     */
    @Override
    public boolean isValid() {
        return session.isValid();
    }


    /**
     * Set the <code>isValid</code> flag for this session.
     *
     * @param isValid The new value for the <code>isValid</code> flag
     */
    @Override
    public void setValid(boolean isValid) {
        session.setValid(isValid);
    }


    // ------------------------------------------------- Session Public Methods


    /**
     * Update the accessed time information for this session.  This method
     * should be called by the context when a request comes in for a particular
     * session, even if the application does not reference it.
     */
    @Override
    public void access() {
        session.access();
    }


    /**
     * End the access.
     */
    @Override
    public void endAccess() {
        session.endAccess();
    }


    /**
     * Add a session event listener to this component.
     */
    @Override
    public void addSessionListener(SessionListener listener) {
        w_listeners.add(listener);
        session.addSessionListener(listener);
    }


    /**
     * Perform the internal processing required to invalidate this session,
     * without triggering an exception if the session has already expired.
     */
    @Override
    public void expire() {
        session.expire();
    }


    /**
     * Perform the internal processing required to invalidate this session,
     * without triggering an exception if the session has already expired.
     *
     * @param notify Should we notify listeners about the demise of
     *  this session?
     */
    @Override
    public void expire(boolean notify) {
        session.expire(notify);
    }


    /**
     * Perform the internal processing required to passivate
     * this session.
     */
    @Override
    public void passivate() {
        session.passivate();
    }


    /**
     * Perform internal processing required to activate this
     * session.
     */
    @Override
    public void activate() {
        session.activate();
    }


    /**
     * Return the object bound with the specified name to the internal notes
     * for this session, or <code>null</code> if no such binding exists.
     *
     * @param name Name of the note to be returned
     */
    @Override
    public Object getNote(String name) {
        return session.getNote(name);
    }

    /**
     * Return an Iterator containing the String names of all notes bindings
     * that exist for this session.
     */
    @Override
    public Iterator<String> getNoteNames() {
        return session.getNoteNames();
    }


    /**
     * Release all object references, and initialize instance variables, in
     * preparation for reuse of this object.
     */
    @Override
    public void recycle() {
        session.recycle();
    }


    /**
     * Remove any object bound to the specified name in the internal notes
     * for this session.
     *
     * @param name Name of the note to be removed
     */
    @Override
    public void removeNote(String name) {
        session.removeNote(name);
    }


    /**
     * Remove a session event listener from this component.
     */
    @Override
    public void removeSessionListener(SessionListener listener) {
        this.w_listeners.remove(listener);
        session.removeSessionListener(listener);
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
        session.setNote(name, value);
    }

    /**
     * Return a string representation of this object.
     */
    @Override
    public String toString() {
        return "StandardSessionWrapper [" + session.toString() + " ]";
    }


    // ------------------------------------------------ Session Package Methods


    /**
     * Read a serialized version of the contents of this session object from
     * the specified object input stream, without requiring that the
     * StandardSession itself have been serialized.
     *
     * @param stream The object input stream to read from
     *
     * @exception ClassNotFoundException if an unknown class is specified
     * @exception java.io.IOException if an input/output error occurs
     */
    @Override
    public void readObjectData(ObjectInputStream stream)
            throws ClassNotFoundException, IOException {
        session.readObjectData(stream);
    }


    /**
     * Write a serialized version of the contents of this session object to
     * the specified object output stream, without requiring that the
     * StandardSession itself have been serialized.
     *
     * @param stream The object output stream to write to
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void writeObjectData(ObjectOutputStream stream)
            throws IOException {
        session.writeObjectData(stream);
    }


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
        return session.getCreationTime();
    }


    /**
     * Return the time when this session was created, in milliseconds since
     * midnight, January 1, 1970 GMT, bypassing the session validation checks.
     */
    @Override
    public long getCreationTimeInternal() {
        return session.getCreationTimeInternal();
    }


    /**
     * Return the ServletContext to which this session belongs.
     */
    @Override
    public ServletContext getServletContext() {
        return session.getServletContext();
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
        return session.getSessionContext();
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
        return session.getAttribute(name);
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
        return session.getAttributeNames();
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
        return session.getValue(name);
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
        return session.getValueNames();
    }


    /**
     * Invalidates this session and unbinds any objects bound to it.
     *
     * @exception IllegalStateException if this method is called on
     *  an invalidated session
     */
    @Override
    public void invalidate() {
        session.invalidate();
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
        return session.isNew();
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
        session.putValue(name, value);
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
        session.removeAttribute(name);
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
        session.removeAttribute(name, notify);
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
        session.removeValue(name);
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
        session.setAttribute(name, value);
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
    @Override
    public void setAttribute(String name, Object value, boolean notify) {
        session.setAttribute(name, value, notify);
    }
}
