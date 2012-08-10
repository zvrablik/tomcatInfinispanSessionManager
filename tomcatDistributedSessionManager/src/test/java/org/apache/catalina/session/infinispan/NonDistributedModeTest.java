/* --------------------------------------------------------------*\
| Copyright (C) e-Spatial Solutions Limited, All rights reserved. |
\* --------------------------------------------------------------*/
package org.apache.catalina.session.infinispan;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.Session;
import org.apache.catalina.session.StandardSession;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.testng.Assert.*;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * NonDistributedModeTest
 * User: zvrablikhenek
 * Since: 8/9/12
 */
public class NonDistributedModeTest {

    /**
     * Test create empty session
     * @throws LifecycleException
     */
    @Test
    public void testInstantiateNonDistributedSessionWithInfinispanSessionManager() throws LifecycleException{
        InfinispanSessionManager sessionManager = this.createSessionManager("zzzNonTransactional");
        Session emptySession = sessionManager.createEmptySession();
        assertTrue(emptySession instanceof StandardSession);
    }

    @Test
    public void testNonDistributedSessionCreate() throws LifecycleException, IOException{
        InfinispanSessionManager manager = this.createSessionManager("zzzNonTransactional2");
        Session session = manager.createSession(null);
        assertTrue( session instanceof StandardSession);

        assertNotNull(session.getId());
        manager.add(session);
        String sessionId = session.getId();
        Session rsession = manager.findSession(sessionId);
        assertEquals(sessionId, rsession.getId());
        assertTrue( rsession instanceof StandardSession);

        Session[] sessions = manager.findSessions();
        assertEquals(1, sessions.length);
        assertTrue( sessions[0] instanceof StandardSession);

        boolean exists = manager.sessionExists(sessionId);
        assertTrue(exists);

        manager.remove(rsession);

        sessions = manager.findSessions();
        assertEquals(0, sessions.length);
    }

    /**
     * Create session manager to use in
     * @return
     * @throws org.apache.catalina.LifecycleException
     */
    protected InfinispanSessionManager createSessionManager(String nameSuffix) throws LifecycleException {
        InfinispanSessionManager sessionManager = InfinispanSessionManagerCommon.getInitializedManager(nameSuffix, false);
        return sessionManager;
    }
}
