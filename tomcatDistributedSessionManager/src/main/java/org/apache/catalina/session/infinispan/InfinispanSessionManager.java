/* --------------------------------------------------------------*\
| Copyright (C) e-Spatial Solutions Limited, All rights reserved. |
\* --------------------------------------------------------------*/
package org.apache.catalina.session.infinispan;

import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Session;
import org.apache.catalina.security.SecurityUtil;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.session.TooManyActiveSessionsException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.infinispan.Cache;
import org.infinispan.DecoratedCache;
import org.infinispan.atomic.AtomicMapLookup;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;

import java.io.File;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * InfinispanSessionManager
 * Uses infinispan distributed cache to store sessions.
 *
 * User: zvrablikhenek
 * Since: 6/25/12
 */
public class InfinispanSessionManager extends ManagerBase {

    private final Log log = LogFactory.getLog(InfinispanSessionManager.class); // must not be static

    /**
     * Distributed cache manager
     */
    private DefaultCacheManager manager;

    /**
     * distributed session metadata cache
     */
    private Cache<String, Object> cache;


    // ---------------------------------------------------- Security Classes
    private class PrivilegedDoLoad
            implements PrivilegedExceptionAction<Void> {

        PrivilegedDoLoad() {
            // NOOP
        }

        @Override
        public Void run() throws Exception{
            return null;
        }
    }

    private class PrivilegedDoUnload
            implements PrivilegedExceptionAction<Void> {

        PrivilegedDoUnload() {
            // NOOP
        }

        @Override
        public Void run() throws Exception{
            return null;
        }

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The descriptive information about this implementation.
     */
    protected static final String info = "InfinispanSessionManager/1.0";


    /**
     * The descriptive name of this Manager implementation (for logging).
     */
    protected static final String name = "InfinispanSessionManager";

    // ------------------------------------------------------------- Properties


    /**
     * Return descriptive information about this Manager implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    @Override
    public String getInfo() {

        return (info);

    }


    /**
     * Return the descriptive short name of this Manager implementation.
     */
    @Override
    public String getName() {

        return (name);

    }

    // --------------------------------------------------------- Public Methods

    /**
     * Load any currently active sessions that were previously unloaded
     * to the appropriate persistence mechanism, if any.  If persistence is not
     * supported, this method returns without doing anything.
     *
     * @exception ClassNotFoundException if a serialized class cannot be
     *  found during the reload
     * @exception java.io.IOException if an input/output error occurs
     */
    @Override
    public void load() throws ClassNotFoundException, IOException {
        if (SecurityUtil.isPackageProtectionEnabled()){
            try{
                AccessController.doPrivileged(new PrivilegedDoLoad());
            } catch (PrivilegedActionException ex){
                Exception exception = ex.getException();
                if (exception instanceof ClassNotFoundException){
                    throw (ClassNotFoundException)exception;
                } else if (exception instanceof IOException){
                    throw (IOException)exception;
                }
                if (log.isDebugEnabled())
                    log.debug("Unreported exception in load() "
                            + exception);
            }
        } else {
            log.debug("Init " + this.getName());
        }
    }

    /**
     * Save any currently active sessions in the appropriate persistence
     * mechanism, if any.  If persistence is not supported, this method
     * returns without doing anything.
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void unload() throws IOException {
        if (SecurityUtil.isPackageProtectionEnabled()){
            try{
                AccessController.doPrivileged( new PrivilegedDoUnload() );
            } catch (PrivilegedActionException ex){
                Exception exception = ex.getException();
                if (exception instanceof IOException){
                    throw (IOException)exception;
                }
                if (log.isDebugEnabled())
                    log.debug("Unreported exception in unLoad() "
                            + exception);
            }
        } else {
            log.debug("Stop " + this.getName());
        }
    }

    /**
     * Start this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception org.apache.catalina.LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        super.startInternal();

        setState(LifecycleState.STARTING);

        log.debug("Starting " + info);
    }


    /**
     * Stop this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        log.debug("Stopping" + info);

        setState(LifecycleState.STOPPING);

        // Expire all active sessions
        //should expire if only one node in cluster?  - sessions are never persisted - than maybe it is ok not to invalidate sessions
//        Session sessions[] = findSessions();
//        for (int i = 0; i < sessions.length; i++) {
//            Session session = sessions[i];
//            try {
//                if (session.isValid()) {
//                    session.expire();
//                }
//            } catch (Throwable t) {
//                ExceptionUtils.handleThrowable(t);
//            } finally {
//                // Measure against memory leaking if references to the session
//                // object are kept in a shared field somewhere
//                session.recycle();
//            }
//        }

        // Require a new random number generator if we are restarted
        super.stopInternal();
    }

    /**
     * initialize infinispan
     *
     * @throws LifecycleException
     */
    protected void initInternal() throws LifecycleException{
        super.initInternal();

        //TODO ?? disable clustering if war state not distributed
        this.initInfinispan();
    }

    /**
     * Create new StandardSession wrapped in StandardSessionWrapper
     * @return
     */
    @Override
    protected StandardSession getNewSession(){
        return new StandardSessionWrapper( this, super.getNewSession() );
    }


    /**
     * Create new Session wrapped in StandardSessionWrapper
     * @return
     */
    @Override
    public Session createEmptySession(){
        return this.getNewSession();
    }

    /**
     * Generate and return a new session identifier.
     */
    @Override
    protected String generateSessionId() {
        String id;
        do {
            id = super.generateSessionId();
        } while (this.sessionExists(id));

        return id;
    }

    /**
     * Create new InfinispanSession without using wrapper
     * @param sessionId
     * @return
     */
    @Override
    public Session createSession(String sessionId ){
        //copy from ManagerBase - generate sessionId sooner
        String id = sessionId;
        if (id == null) {
            do {
                id = this.generateSessionId();
            } while (this.sessionExists(id));
        }
        sessionCounter++;


        if ((maxActiveSessions >= 0) &&
                (getActiveSessions() >= maxActiveSessions)) {
            rejectedSessions++;
            throw new TooManyActiveSessionsException(
                    sm.getString("managerBase.createSession.ise"),
                    maxActiveSessions);
        }

        //create a Session instance
        Session session = new InfinispanSession(this, cache, id);

        // Initialize the properties of the new session and return it
        session.setNew(true);
        session.setValid(true);
        session.setCreationTime(System.currentTimeMillis());
        session.setMaxInactiveInterval(this.maxInactiveInterval);

        SessionTiming timing = new SessionTiming(session.getCreationTime(), 0);
        synchronized (sessionCreationTiming) {
            sessionCreationTiming.add(timing);
            sessionCreationTiming.poll();
        }
        return (session);

    }

    /**
     * Create local cache to use distributed cache metadata and data in cluster node
     * @param sessionId
     * @return
     * @throws RuntimeException local session can't be created
     */
    private Session createLocalSession(String sessionId) throws RuntimeException{
        if (sessionId == null){
            throw new RuntimeException("Can't create local session when sessionId is null!");
        }

        //connect to existing session
        Session session = new InfinispanSession(this, cache, sessionId);
        session.setValid(true);

        return session;
    }

    /**
     * True if session already exists
     * @param id
     * @return
     */
    protected boolean sessionExists(String id) {
        String fullSessionId = id;
        id = this.stripDotSuffix(id);//remove possible jvm route, session metadata is not stored with jvm route in distributed cache
        String cacheId = SessionMetaAttributes.createCacheId(id);
        //test metadata existence in cache
        return cache.get(cacheId) != null;
    }

    /**
     * Add infinispan session or transform session object to distributed infinispan session
     * @param session
     */
    @Override
    public void add(Session session){
        Session newSession;

        if ( session instanceof InfinispanSession){
            newSession = session;
        } else if (session instanceof StandardSessionWrapper){
            newSession = new InfinispanSession(this, cache, (StandardSessionWrapper)session);
        } else {
            newSession = new InfinispanSession(this, cache, session.getId());
        }

        super.add( newSession );
    }

    /**
     * Find session and create new local session instance if session has meta attributes in cache.
     * @param sessionId
     * @return
     */
    @Override
    public Session findSession(String sessionId) throws IOException{
        if (sessionId == null)
            return (null);

        Session session = super.findSession(sessionId);
        if ( session == null && this.sessionExists(sessionId) ){
            session = this.createLocalSession(sessionId);
        }

        return session;
    }

    /**
     * Remove session from all cluster
     * @param session
     * @param update
     */
    @Override
    public void remove(Session session, boolean update) {
        super.remove(session, update);

        //remove attributes
        String attributesCacheId = SessionAttributes.createCacheId(session.getId());
        AtomicMapLookup.removeAtomicMap(cache, attributesCacheId);

        //remove metadata
        String metadataCacheId = SessionMetaAttributes.createCacheId(session.getId());
        AtomicMapLookup.removeAtomicMap(cache, metadataCacheId);
    }
    //TODO override remove function to remove cache items, namespace must be used!



    // ------------------------------------------------------ Protected Methods

    /**
     * Retrieve the enclosing Engine for this Manager.
     *
     * @return an Engine object (or null).
     */
    public Engine getEngine() {
        Engine e = null;
        for (Container c = getContainer(); e == null && c != null ; c = c.getParent()) {
            if (c != null && c instanceof Engine) {
                e = (Engine)c;
            }
        }
        return e;
    }


    /**
     * Retrieve the JvmRoute for the enclosing Engine.
     * @return the JvmRoute or null.
     */
    public String getJvmRoute() {
        Engine e = getEngine();
        return e == null ? null : e.getJvmRoute();
    }

    /**
     * Remove jvm route from session id
     * @param sessionId
     * @return
     */
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

    /**
     * Returns true if session object exists locally. Session object == local shell to distributed data and metadata
     *
     * @param sessionId
     * @return
     */
    protected boolean containsLocalSession(String sessionId){
        return sessions.containsKey(sessionId);
    }

    /**
     * Remove locall session shell if exists. Other cluster nodes are not notified.
     * Should be used only to remove sessions which were removed in other nodes.
     * @param sessionId
     */
    protected void removeLocalSession(String sessionId){
        if (this.containsLocalSession(sessionId)) {
            sessions.remove(sessionId);
        } else {
            //sessionId with jvmRoute suffix, could be any jvmRoute not just local
            Set<String> keySet = new HashSet<String>(sessions.keySet());
            for (String key : keySet) {
                if (key.startsWith(sessionId)) {
                    sessions.remove(key);
                    break;
                }
            }
        }
    }

    /**
     * Create default infinispan config
     * @param appName
     * @return
     */
    private Configuration createDefaultInfinispanConfiguration(String appName){
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.jmxStatistics().enabled( true );
        cb.clustering().cacheMode(CacheMode.DIST_SYNC).l1().disable().lifespan(600000).hash().numOwners(2).rehashRpcTimeout(6000);
        cb.invocationBatching().enable();
        //cb.transaction().transactionManagerLookup(new DummyTransactionManagerLookup()).lockingMode(LockingMode.PESSIMISTIC);
        //default config
        //cb.name("_session_attr_" + appName);

        return cb.build();
    }

    /**
     * Create default global config.
     * @param appName
     * @return
     */
    private GlobalConfiguration createGlobalDefaultInfinispanConfiguration(String appName){
        GlobalConfigurationBuilder gcb = new GlobalConfigurationBuilder();
        gcb.transport().defaultTransport();
        if (appName == null || appName.length() == 0){
            int i = new Random().nextInt(1000);
            appName = String.valueOf( i );
        }
        gcb.transport().clusterName("tomcatSession_" + appName);
        gcb.globalJmxStatistics().enabled( true )
                .allowDuplicateDomains( true ).jmxDomain( "defaultIspn_" + appName );

        return gcb.build();
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

        File configFile = this.getConfigFile(configFileName);
        boolean useDefault = configFile == null;

        try {
            if ( useDefault ){
                File defaultFile = this.getConfigFile("defaultDistributedCache.xml");
                if ( defaultFile != null){
                    log.debug("Initialize infinispan cache manager. Use default config file: " + defaultFile.getAbsolutePath());                    manager = new DefaultCacheManager(defaultFile.getAbsolutePath());
                } else {
                    GlobalConfiguration globalDefaultConfig = this.createGlobalDefaultInfinispanConfiguration(appName);
                    Configuration cacheConfiguration = this.createDefaultInfinispanConfiguration(appName);

                    log.debug("Initialize infinispan cache manager. Default cache settings used.");
                    manager = new DefaultCacheManager(globalDefaultConfig, cacheConfiguration);
                }
            } else {

                log.debug("Initialize infinispan cache manager. Config file: " + configFile.getAbsolutePath());
                manager = new DefaultCacheManager(configFile.getAbsolutePath());
            }
        } catch (Exception ex) {
            String message = "Error initializing distributed session cache!";
            if ( useDefault ) {
                message += " Used default infinispan configuration.";
            } else {
                message += " ConfigFileName:" + configFile.getAbsolutePath();
            }
            //to log root error, lifecycleException doesn't do it
            log.error(message, ex);
            throw new LifecycleException(message, ex);
        }

        return manager;
    }

    /**
     * Find config file. Return null if config file doesn't exist or is not possible to read.
     * @param configFileName
     * @return
     */
    private File getConfigFile(String configFileName) {

        String baseDirName = System.getenv("CATALINA_BASE");
        String configFileBase =  baseDirName + "/conf/" + configFileName;
        String configFileHome = null;
        File configFile = new File( configFileBase );

        if ( !configFile.exists()) {
            String homeDirName = System.getenv("CATALINA_HOME");
            configFileHome = homeDirName + "/conf/" + configFileName;
            configFile = new File(configFileHome);
        }

        if ( !configFile.exists() ) {
            String message = "Config file " + configFileName + " doesn't exist.";
            message += "Tested files: " + configFileBase + " and " + configFileHome;
            message += " Used default infinispan configuration instead.";
            log.info(message);

            configFile = null;
        } else if (!(configFile.isFile() && configFile.canRead())){
            String message = "Config file " + configFile.getAbsoluteFile()
                    + " is not file or current tomcat process is not permitted to read this file.";
            message += " Used default infinispan configuration instead.";

            log.info(message);

            configFile = null;
        }

        return configFile;
    }

    /**
     * Get cache instance
     *
     * @param manager   cache manager
     * @param cacheName name of cache to get
     *
     * @return wrapped cache to use correct class loader
     */
    private Cache<String, Object> getCacheObject(DefaultCacheManager manager,
                                                 String cacheName) {
        Cache<String, Object> cache = manager.getCache(cacheName);
        //use war app class loader
        Cache<String, Object> wrappedCache = new DecoratedCache<String, Object>(cache.getAdvancedCache(), Thread.currentThread().getContextClassLoader() );
        org.infinispan.config.Configuration configuration = wrappedCache.getConfiguration();
        configuration.setClassLoader(Thread.currentThread().getContextClassLoader());
        manager.defineConfiguration(cacheName, configuration);

        return wrappedCache;
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
        containerName = containerName.replace('/', '_');

        log.debug("Initialize infinispan cache app: " +  containerName);
        DefaultCacheManager manager = initializeCacheManager( containerName );

        String cacheName = "tc_session_" + containerName;
        cache = getCacheObject(manager, cacheName);
        cache.addListener( new InfinispanSessionListener( this ) );
    }

    /**
     * Get session manager cache name
     * @return
     */
    public String getCacheName(){
        return this.cache.getName();
    }
}
