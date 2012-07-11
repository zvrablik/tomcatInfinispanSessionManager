/* --------------------------------------------------------------*\
| Copyright (C) e-Spatial Solutions Limited, All rights reserved. |
\* --------------------------------------------------------------*/
package org.apache.catalina.session.infinispan;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.infinispan.Cache;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryVisited;
import org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryRemovedEvent;

/**
 * Listen to session attributes cache events.
 * Remove local session object if session removed from session.
 *
 * @author zhenek
 */
@Listener(sync = false)
public class InfinispanSessionListener {

    protected Log log = LogFactory.getLog(InfinispanSessionListener.class);
    private InfinispanSessionManager manager;

    /**
     * Construct listener.
     *
     * @param manager session manager
     */
    public InfinispanSessionListener(InfinispanSessionManager manager) {
        this.manager = manager;
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
        log.debug("REMOVED event Cache : " + cacheName
                + " removed session. Session id: " + sessionId);

        manager.removeLocalSession( sessionId );
    }

    @CacheEntryVisited
    public void visitSession(CacheEntryEvent<String, Object> event) {
        if (!event.isPre()) {
            return;
        }
        String sessionId = event.getKey();

        Cache<String, Object> cache = event.getCache();
        String cacheName = cache.getName();
        log.debug("VISITED event Cache : " + cacheName
                + " visited session. Session id: " + sessionId);
    }

    @CacheEntryCreated
    public void addSession(CacheEntryCreatedEvent<String, Object> event) {
        if (!event.isPre()) {
            return;
        }
        String sessionId = event.getKey();

        Cache<String, Object> cache = event.getCache();
        String cacheName = cache.getName();
        log.info("CREATED event Cache : " + cacheName
                + " added session. Session id: " + sessionId);
    }
}

