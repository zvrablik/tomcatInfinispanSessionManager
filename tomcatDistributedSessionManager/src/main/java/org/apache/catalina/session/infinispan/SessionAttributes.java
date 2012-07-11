/* --------------------------------------------------------------*\
| Copyright (C) e-Spatial Solutions Limited, All rights reserved. |
\* --------------------------------------------------------------*/
package org.apache.catalina.session.infinispan;

import org.infinispan.Cache;
import org.infinispan.atomic.AtomicMapLookup;
import org.infinispan.atomic.FineGrainedAtomicMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * SessionAttributes
 * User session attributes.
 * Separated cache to meta attributes
 * <p/>
 * User: zvrablikhenek
 * Since: 6/25/12
 */
public class SessionAttributes {
    /**
     * cache namespace
     */
    private static final String NAMESPACE = "attr";

    private String sessionId;
    private Cache<String, ?> attributesCache;
    private String cacheId;

    public SessionAttributes(Cache<String, ?> attributesCache, String sessionId) {
        this.attributesCache = attributesCache;
        this.sessionId = sessionId;
        this.cacheId = this.createCacheId(sessionId);
    }

    public void clear() {
        FineGrainedAtomicMap<String, Object> attributes = getCachedAttributes();
        attributes.clear();
    }

    /**
     * Remove attributes from cache
     */
    public void remove(){
        AtomicMapLookup.removeAtomicMap(attributesCache, cacheId );
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

        return attributes.get(key);
    }

    /**
     * Get all session attributes as Map
     *
     * @return
     */
    public Map<String, Object> getAll() {
        FineGrainedAtomicMap<String, Object> attributes = getCachedAttributes();
        Map<String, Object> attribs = new HashMap<String, Object>();
        attribs.putAll(attributes);

        return attribs;
    }

    /**
     * Set all attributes to session attributes
     *
     * @param attributes
     */
    public void putAll(Map<String, Object> attributes) {
        FineGrainedAtomicMap<String, Object> attribs = getCachedAttributes();
        attribs.putAll(attributes);
    }

    /**
     * get attributes reference to distributed cache
     */
    private FineGrainedAtomicMap<String, Object> getCachedAttributes() {
        //use atomic map to store one session attributes.
        //doesn't use distributed transaction, use <invocationBatching enabled="true"/> in _session_attr named cache
        FineGrainedAtomicMap<String, Object> attributes = AtomicMapLookup.getFineGrainedAtomicMap(attributesCache, cacheId);

        return attributes;
    }

    private String getCacheId() {
        return this.cacheId;
    }

    /**
     * Set session id if changed
     */
    public void setSessionId(String id) {


        //move attributes in cluster cache

        Map<String, Object> oldCache = this.getCachedAttributes();

        String oldCacheId = this.cacheId;

        this.sessionId = id;
        this.cacheId = SessionAttributes.createCacheId(this.sessionId);

        FineGrainedAtomicMap<String, Object> newCache = this.getCachedAttributes();

        //move content to new cache
        for ( String key : oldCache.keySet() ){
            Object value = oldCache.get(key);
            newCache.put(key, value);
        }

        AtomicMapLookup.removeAtomicMap(attributesCache, oldCacheId);

    }

    public String getSessionId() {
        return this.sessionId;
    }

    /**
     * Create attributes cache id
     * @param sessionId
     * @return
     */
    public static String createCacheId(String sessionId) {
        return NAMESPACE + sessionId;
    }
}
