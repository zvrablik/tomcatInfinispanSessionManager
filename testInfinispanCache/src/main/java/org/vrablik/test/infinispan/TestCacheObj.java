/* --------------------------------------------------------------*\
| Copyright (C) e-Spatial Solutions Limited, All rights reserved. |
\* --------------------------------------------------------------*/
package org.vrablik.test.infinispan;

import org.infinispan.Cache;
import org.infinispan.manager.DefaultCacheManager;

import java.io.InputStream;
import java.util.Map;

/**
 * Cache
 * User: zvrablikhenek
 * Since: 1/31/12
 */
public class TestCacheObj {

    private static DefaultCacheManager manager = null;

    public static TestCacheObj cache;

    private Cache<String, String> c;

    static{
        try {
            cache = TestCacheObj.createCache("testInfinispanCache");
        } catch (Exception e) {
            System.out.println("Create testInfinispanCache failed.");
            e.printStackTrace();
            cache = null;
        }
    }

    public TestCacheObj(Cache<String, String> cache){
        this.c = cache;
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
      c.put(key, value);
        
      if ( throwException ){
          throw new RuntimeException("forced throw runtime exception");
      }
    }

    /**
     * Get value from cache.
     *
     * @param key value key
     *
     * @param throwException throw exception if true
     *
     * @return cache value of key key
     */
    public String get( String key, boolean throwException){
        String value = c.get(key);
        if ( throwException ){
            throw new RuntimeException("forced throw runtime exception");
        }

        return value;
    }


    /**
     * Create cache.
     *
     * @param cacheName name of the infinispan cache
     * @return
     * @throws Exception
     */
    private static TestCacheObj createCache( String cacheName ) throws Exception {
        InputStream configStream = TestCacheObj.class.getResourceAsStream("testInfinispanCache.xml");

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        System.out.println("testInfinispanCache" + cl);
        System.out.println("testInfinispanCache" + cl.getParent());

        //current classloader is web app classloader
        Thread.currentThread().setContextClassLoader(cl.getParent());
        try{
            manager = new DefaultCacheManager( configStream );
            Cache c =  manager.getCache(cacheName);
            return new TestCacheObj( c );
        } finally {
            Thread.currentThread().setContextClassLoader( cl );
        }
    }
}
