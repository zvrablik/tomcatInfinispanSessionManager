/* --------------------------------------------------------------*\
| Copyright (C) e-Spatial Solutions Limited, All rights reserved. |
\* --------------------------------------------------------------*/
package org.apache.catalina.session.infinispan;

import org.infinispan.Cache;
import org.infinispan.atomic.AtomicMapLookup;

import java.util.Map;

/**
 * Session metadata distributed attributes.
 *
 * @author zhenek
 *
 */
class SessionMetaAttributes {

    /**
     * Shared cache namespace
     */
    private static final String NAMESPACE = "metaAttr";
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

    private Cache<String, ?> cache;
    /**
     * key to metadata cache items in distributed cache
     */
    private String cacheId;

    /**
     * Constructor
     *
     * @param cache cache to store metadata object
     * @param sessionId
     */
    public SessionMetaAttributes(Cache<String, ?> cache, String sessionId) {
        this.cache = cache;
        this.sessionId = sessionId;
        this.cacheId = SessionMetaAttributes.createCacheId(sessionId);
    }

    public long getCreationTime() {
        Map<String, Object> c = this.getCache();
        Object creationTimeObject = c.get(CREATION_TIME);
        return (Long)creationTimeObject;
    }

    public void setCreationTime(long creationTime) {
        this.getCache().put( CREATION_TIME, creationTime );
    }

    public long getLastAccessedTime() {
        Object lastAccessedTimeObject = this.getCache().get(LAST_ACCESSED_TIME);
        return (Long)lastAccessedTimeObject;
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
     * Get one session metadata, set default values if not available.
     *
     * @return
     */
    private Map<String, Object> getCache() {
        Map<String, Object> cacheItem =
                AtomicMapLookup.getFineGrainedAtomicMap(cache, cacheId);

        return cacheItem;
    }

    /**
     * Get cache id. (add cache namespace of shared cache )
     * @param sessionId
     * @return
     */
    public static String createCacheId(String sessionId) {
        return NAMESPACE + sessionId;
    }


    /**
     * Set session id if changed
     */
    public void setSessionId(String id){
        Map<String, Object> oldCache = this.getCache();
        String oldSessionId = this.sessionId;
        String oldCacheId = this.cacheId;

        this.sessionId = id;
        this.cacheId = SessionMetaAttributes.createCacheId(id);

        Map<String, Object> newCache = this.getCache();

        //move content to new cache
        for ( String key : oldCache.keySet() ){
            Object value = oldCache.get(key);
            newCache.put(key, value);
        }

        AtomicMapLookup.removeAtomicMap(cache, oldCacheId);
    }

    public String getSessionId(){
        return this.sessionId;
    }
}
