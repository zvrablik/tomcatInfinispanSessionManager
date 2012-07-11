/* --------------------------------------------------------------*\
| Copyright (C) e-Spatial Solutions Limited, All rights reserved. |
\* --------------------------------------------------------------*/
package org.vrablik.testlb;

import org.infinispan.Cache;
import org.infinispan.manager.DefaultCacheManager;

import java.io.InputStream;
import java.util.Map;

/**
 * Cache
 * User: zvrablikhenek
 * Since: 1/31/12
 */
public class CacheObj {

    private static DefaultCacheManager manager = null;

    private Cache<String, String> cache;

    public CacheObj( Cache<String, String> cache){
        this.cache = cache;
    }

    /**
     * Set key/value to cache and trow exception if required.
     *
     * @param key
     * @param value
     * @param throwException
     *
     * @throws RuntimeException throw if parameter throwException is true
     */
    public void set( String key, String value, boolean throwException){
      cache.put(key, value);        
        
      if ( throwException ){
          throw new RuntimeException("forced throw runtime exception");
      }
    }

    /**
     * Get all cache records
     * @return
     */
    public Map<String, String> getStoredData(){
            return cache;
    }

    /**
     * Create cache.
     *
     * @param cacheName name of the infinispan cache
     * @return
     * @throws Exception
     */
    public static CacheObj createCache( String cacheName ) throws Exception {
        ClassLoader webappClassLoader = Thread.currentThread().getContextClassLoader();
        System.out.println("testLB " + webappClassLoader);
        System.out.println("testLB " + webappClassLoader.getParent());

        InputStream configStream = CacheObj.class.getResourceAsStream("testLBApp.xml");
        manager = new DefaultCacheManager( configStream );
        Cache cache =  manager.getCache(cacheName);
        return new CacheObj( cache );
    }
}
