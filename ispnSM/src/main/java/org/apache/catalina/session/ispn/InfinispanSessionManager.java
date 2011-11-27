package org.apache.catalina.session.ispn;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Session;
import org.apache.catalina.util.LifecycleSupport;
import org.infinispan.Cache;
import org.infinispan.DecoratedCache;
import org.infinispan.atomic.AtomicMapLookup;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryRemovedEvent;

/**
 * Infinispan session manager.
 * 
 * Most of code taken from StandardManager
 * Implements session management with infinispan instead of plain ConcurrentHashMap.
 * Session persistence code have been removed.
 * @author zhenek
 *
 */
public class InfinispanSessionManager
    extends InfinispanSessionManagerBase
    implements Lifecycle, PropertyChangeListener {
    
  /**
   * The descriptive name of this Manager implementation (for logging).
   */
  protected static String name = "InfinispanSessionManager";
  
  /**
   * The descriptive information about this implementation.
   */
  protected static final String info = name + "/A";
  
    /**
     * The lifecycle event support for this component.
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * The maximum number of active Sessions allowed, or -1 for no limit.
     */
    protected int maxActiveSessions = -1;

    /**
     * Has this component been started yet?
     */
    protected boolean started = false;


    /**
     * Number of session creations that failed due to maxActiveSessions.
     */
    protected int rejectedSessions = 0;


    /**
     * Processing time during session expiration.
     */
    protected long processingTime = 0;

    /**
     * distributed session cache
     */
    private Cache<String, Object> attributesCache;
    
    /**
     * Local sessions
     * to prevent create new object per request
     * session is removed through listener when any node removes session.
     */
    private final Map<String, Session> localSessions = new ConcurrentHashMap<String, Session>();
    
    /**
     * Synchronization distributed cache manager lock
     */
    private Object managerLock = new Object();
    
    /**
     * Distributed cache manager
     */
    private DefaultCacheManager manager;

    // ------------------------------------------------------------- Properties


    /**
     * Set the Container with which this Manager has been associated.  If
     * it is a Context (the usual case), listen for changes to the session
     * timeout property.
     *
     * @param container The associated Container
     */
    public void setContainer(Container container) {

        // De-register from the old Container (if any)
        if ((this.container != null) && (this.container instanceof Context))
            ((Context) this.container).removePropertyChangeListener(this);

        // Default processing provided by our superclass
        super.setContainer(container);

        // Register with the new Container (if any)
        if ((this.container != null) && (this.container instanceof Context)) {
            setMaxInactiveInterval
                ( ((Context) this.container).getSessionTimeout()*60 );
            ((Context) this.container).addPropertyChangeListener(this);
        }

    }


    /**
     * Return descriptive information about this Manager implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {

        return (info);

    }


    /**
     * Return the maximum number of active Sessions allowed, or -1 for
     * no limit.
     */
    public int getMaxActiveSessions() {

        return (this.maxActiveSessions);

    }


    /** Number of session creations that failed due to maxActiveSessions
     *
     * @return The count
     */
    public int getRejectedSessions() {
        return rejectedSessions;
    }


    public void setRejectedSessions(int rejectedSessions) {
        this.rejectedSessions = rejectedSessions;
    }


    /**
     * Set the maximum number of actives Sessions allowed, or -1 for
     * no limit.
     *
     * @param max The new maximum number of sessions
     */
    public void setMaxActiveSessions(int max) {

        int oldMaxActiveSessions = this.maxActiveSessions;
        this.maxActiveSessions = max;
        support.firePropertyChange("maxActiveSessions",
                                   new Integer(oldMaxActiveSessions),
                                   new Integer(this.maxActiveSessions));

    }


    /**
     * Return the descriptive short name of this Manager implementation.
     */
    public String getName() {

        return (name);

    }


    // --------------------------------------------------------- Public Methods

    /**
     * Construct and return a new session object, based on the default
     * settings specified by this Manager's properties.  The session
     * id will be assigned by this method, and available via the getId()
     * method of the returned session.  If a new session cannot be created
     * for any reason, return <code>null</code>.
     *
     * @exception IllegalStateException if a new session cannot be
     *  instantiated for any reason
     */
    public Session createSession(String sessionId) {
        Map<String, Session> sessions = this.getSessions();
        
        if ((maxActiveSessions >= 0) &&
            (sessions.size() >= maxActiveSessions)) {
            rejectedSessions++;
            throw new IllegalStateException
                (sm.getString("standardManager.createSession.ise"));
        }

        return (super.createSession(sessionId));

    }


    /**
     * Get a session from the recycled ones or create a new empty one.
     * The PersistentManager manager does not need to create session data
     * because it reads it from the Store.
     * 
     * @param cache       attributes cache
     */
    @Override
    Session createEmptySession(String sessionId) {
        return new InfinispanStandardSession(this, attributesCache, sessionId);
    }
    
    /**
     * Create empty session with null session id
     */
    public Session createEmptySession() {
        //TODO where it is used? BackupManager, DeltaManager, JDBCStore FileStore - shouldn't be used 
        return new InfinispanStandardSession(this, attributesCache, null);
    }

    
    /* (non-Javadoc)
     * @see org.apache.catalina.session.ispn.InfinispanSessionManagerBase#stripJvmRoute(java.lang.String)
     */
    @Override
    protected String stripJvmRoute(String sessionId){
        String jvmRoute = this.getJvmRoute();
        
        if (sessionId == null){
            return null;
        }
        
        String newSessionId = sessionId;
        
        if ( jvmRoute != null && jvmRoute.length() > 0 && sessionId.endsWith(jvmRoute) ){
            // -1 is the dot delimiter and jvmRoute length to truncate
            int endIndex = newSessionId.length() -1 - jvmRoute.length(); 
            newSessionId = newSessionId.substring(0, endIndex);
        }
        
        return newSessionId;
    }
    
    /**
     * Remove any suffix starting with dot (.)
     * 
     * @param sessionId session id with possible . suffix
     * 
     * @return sessionId without any dot suffix
     */
    protected String stripDotSuffix(String sessionId){
        if (sessionId == null){
            return null;
        }
 
        int index = sessionId.indexOf(".");
        if (index > 0) {
            sessionId = sessionId.substring(0, index);
        }
        
        return sessionId;
    }
    
    

    /* (non-Javadoc)
     * @see org.apache.catalina.session.ispn.InfinispanSessionManagerBase#getSessionFromCache(java.lang.String)
     */
    @Override
    protected Session createSessionFromCache(String sessionId) {
        String cacheSessionId = this.stripDotSuffix(sessionId);
        Session session = null;
        if (attributesCache.containsKey(cacheSessionId) ) {
            session = this.createSession(sessionId);
            //set local session to cache session object on given node
            //session is removed from all nodes through ispn event
            localSessions.put(sessionId, session);
        }
        
        return session;
    }


    /**
     * Load any currently active sessions that were previously unloaded
     * to the appropriate persistence mechanism, if any.  If persistence is not
     * supported, this method returns without doing anything.
     *
     * @exception ClassNotFoundException if a serialized class cannot be
     *  found during the reload
     * @exception IOException if an input/output error occurs
     */
    public void load() throws ClassNotFoundException, IOException {
        //intentionally empty
        //infinispan is used to handle sessions
    }

    public void unload() throws IOException {
        //intentionally empty
        //infinispan is used to handle sessions
    }

    @Override
    Map<String, Session> getSessions() {
      return localSessions;
    }
    
    /**
     * Remove this Session from the active Sessions for this Manager.
     *
     * @param session Session to be removed
     */
    @Override
    public void remove(Session session) {
      String cacheSessionId = this.stripDotSuffix( session.getId() );
      AtomicMapLookup.removeAtomicMap(attributesCache, cacheSessionId );
      
      localSessions.remove(session.getIdInternal());
    }


    /**
     * Add a lifecycle event listener to this component.
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {

        lifecycle.addLifecycleListener(listener);

    }


    /**
     * Get the lifecycle listeners associated with this lifecycle. If this
     * Lifecycle has no listeners registered, a zero-length array is returned.
     */
    public LifecycleListener[] findLifecycleListeners() {

        return lifecycle.findLifecycleListeners();

    }


    /**
     * Remove a lifecycle event listener from this component.
     *
     * @param listener The listener to remove
     */
    public void removeLifecycleListener(LifecycleListener listener) {

        lifecycle.removeLifecycleListener(listener);

    }

    /**
     * Prepare for the beginning of active use of the public methods of this
     * component.  This method should be called after <code>configure()</code>,
     * and before any of the public methods of the component are utilized.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    public void start() throws LifecycleException {

        if( ! initialized ){
            init();
            initInfinispan();
        }

        // Validate and update our current component state
        if (started) {
            return;
        }
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

        // Force initialization of the random number generator
        if (log.isDebugEnabled())
            log.debug("Force random number initialization starting");
        String dummy = generateSessionId();
        if (log.isDebugEnabled())
            log.debug("Force random number initialization completed");

        // Load unloaded sessions, if any
        try {
            load();
        } catch (Throwable t) {
            log.error(sm.getString( name + ".managerLoad"), t);
        }

    }
    
    /**
     * Initialize distributed cache
     * Cache name is _session_attr and container name
     * Example: _session_attr/testLB
     * @throws LifecycleException
     */
    private void initInfinispan() throws LifecycleException {
        
      String containerName = container.getName();
      if (containerName.startsWith("/")){
          //remove leading 
          containerName = containerName.substring(1);
      }
      
      log.debug("Initialize infinispan cache app: " +  containerName);
      DefaultCacheManager manager = initializeCacheManager( containerName );
      
      String cacheName = "_session_attr_" + containerName;
      Cache<String, Object> cache = manager.getCache(cacheName);
      //use war app class loader
      attributesCache = new DecoratedCache<String, Object>(cache.getAdvancedCache(), Thread.currentThread().getContextClassLoader() );
      org.infinispan.config.Configuration configuration = attributesCache.getConfiguration();
      configuration.setClassLoader(Thread.currentThread().getContextClassLoader());
      manager.defineConfiguration(cacheName, configuration);
      
      cache.addListener( new SessionListener( this.getJvmRoute() ) );
    }
    
    /**
     * Initialize cache manager 
     * @param appName  config file suffix (example suffix testLB config file in conf directory
     * is sessionInfinispanConfigtestLB.xml
     * @return
     * @throws LifecycleException
     */
    private DefaultCacheManager initializeCacheManager(String appName)
            throws LifecycleException {
        String configFileName = "sessionInfinispanConfig" + appName + ".xml";
        
        String baseDirName = System.getenv("CATALINA_BASE");
        String configFileBase =  baseDirName + "/conf/" + configFileName;
        String configFileHome = null;
        File configFile = new File( configFileBase );
        if ( !configFile.exists()) {
            String homeDirName = System.getenv("CATALINA_HOME");
            configFileHome = homeDirName + "/conf/" + configFileName;
            configFile = new File(configFileHome);
        }
        
        boolean useDefault = false;
        
        if ( !configFile.exists() ) {
            String message = "Config file " + configFileName + " doesn't exist.";
            message += "Tested files: " + configFileBase + " and " + configFileHome;
            message += " Used default infinispan configuration instead.";
            log.error(message);
            useDefault = true;
        }
        
        if (!(configFile.isFile() && configFile.canRead())){
            String message = "Config file " + configFile.getAbsoluteFile() 
                    + " is not file or current tomcat process is not permitted to read this file.";
            message += " Used default infinispan configuration instead.";
            
            log.error(message);
            useDefault = true;
        }
        
        try {
             if ( useDefault ){
                 GlobalConfiguration globalDefaultConfig = this.createGlobalDefaultInfinispanConfiguration(appName);
                 Configuration cacheConfiguration = this.createDefaultInfinispanConfiguration(appName);
                 
                 log.debug("Initialize infinispan cache manager. Default cache settings used. Invalid config file: " + configFile.getAbsoluteFile());
                 manager = new DefaultCacheManager(globalDefaultConfig, cacheConfiguration);
             } else {
                 
                 log.debug("Initialize infinispan cache manager. Config file: " + configFile.getAbsolutePath());
                 manager = new DefaultCacheManager(configFile.getAbsolutePath());
             }
        } catch (Exception ex) {
            String message = "Error initializing distributed session cache! ConfigFileName:" + configFile.getAbsolutePath();
            if ( useDefault ){
                message += " Used default infinispan configuration.";
            }
            //to log root error, lifecycleException doesn't do it
            log.error(message, ex);
            throw new LifecycleException(message, ex);
        }

        return manager;
    }
    
    /**
     * Create default global config.
     * @param appName
     * @return
     */
    private GlobalConfiguration createGlobalDefaultInfinispanConfiguration(String appName){
        GlobalConfigurationBuilder gcb = new GlobalConfigurationBuilder();
        
        gcb.transport().clusterName("tomcatSession");
        gcb.globalJmxStatistics().allowDuplicateDomains(true).jmxDomain("org.infinispan." + appName);
        
        return gcb.build();
    }
    
    /**
     * Create default infinispan config
     * @param appName
     * @return
     */
    private Configuration createDefaultInfinispanConfiguration(String appName){
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.jmxStatistics();
        cb.clustering().cacheMode(CacheMode.DIST_SYNC).l1().disable().lifespan(600000).hash().numOwners(2).rehashRpcTimeout(6000);
        cb.invocationBatching();
        //default config
        //cb.name("_session_attr_" + appName);
        
        return cb.build();
    }

    /**
     * Gracefully terminate the active use of the public methods of this
     * component.  This method should be the last one called on a given
     * instance of this component.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    public void stop() throws LifecycleException {

        if (log.isDebugEnabled())
            log.debug("Stopping");

        // Validate and update our current component state
        if (!started)
            throw new LifecycleException
                (sm.getString( name + ".notStarted"));
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        
        //FIXME probably shouldn't do anything on stop
        // Write out sessions
        try {
            unload();
        } catch (Throwable t) {
            log.error(sm.getString( name + ".managerUnload"), t);
        }

        // Expire all active sessions
        Session sessions[] = findSessions();
        for (int i = 0; i < sessions.length; i++) {
            Session session = sessions[i];
            try {
                if (session.isValid()) {
                    session.expire();
                }
            } catch (Throwable t) {
                ;
            } finally {
                // Measure against memory leaking if references to the session
                // object are kept in a shared field somewhere
                session.recycle();
            }
        }

        // Require a new random number generator if we are restarted
        this.random = null;

        if( initialized ) {
            destroy();
        }
    }


    // ----------------------------------------- PropertyChangeListener Methods


    /**
     * Process property change events from our associated Context.
     *
     * @param event The property change event that has occurred
     */
    public void propertyChange(PropertyChangeEvent event) {

        // Validate the source of this event
        if (!(event.getSource() instanceof Context))
            return;
        Context context = (Context) event.getSource();

        // Process a relevant property change
        if (event.getPropertyName().equals("sessionTimeout")) {
            try {
                setMaxInactiveInterval
                    ( ((Integer) event.getNewValue()).intValue()*60 );
            } catch (NumberFormatException e) {
                log.error(sm.getString( name + ".sessionTimeout",
                                 event.getNewValue().toString()));
            }
        }

    }
    
    /**
     * Listen to session attributes cache events.
     * Remove local session object if session removed from session.
     * 
     * @author zhenek
     * 
     */
    @Listener(sync = false)
    public class SessionListener {
        
        private String jvmRoute;

        /**
         * Construct listener.
         * 
         * @param jvmRoute
         */
        public SessionListener(String jvmRoute){
            this.jvmRoute = jvmRoute;
        }

        @CacheEntryRemoved()
        public void removeSession(CacheEntryRemovedEvent<String, Object> event) {
            if (!event.isPre()) {
                return;
            }

            // only session prefix
            String sessionId = event.getKey();

            Cache<String, Object> cache = event.getCache();
            String cacheName = cache.getName();
            log.debug("REMOVED Session : " + cacheName
                    + " removed session. Session id: " + sessionId);
           
            if ( localSessions.containsKey(sessionId) ){
                localSessions.remove(sessionId);
            } else {
                //sessionId with jvmRoute suffix, could be any jvmRoute not just local
                Set<String> keySet = localSessions.keySet();
                for ( String key : keySet ){
                    if ( key.startsWith(sessionId) ){
                        localSessions.remove(key);
                        break;
                    }
                }
            }
           
        }

        @CacheEntryCreated
        public void addSession(CacheEntryCreatedEvent<String, Object> event) {
            if (!event.isPre()) {
                return;
            }
            String sessionId = event.getKey();

            Cache<String, Object> cache = event.getCache();
            String cacheName = cache.getName();
            log.debug("CREATED Session : " + cacheName
                    + " added session. Session id: " + sessionId);
        }
    }
}

