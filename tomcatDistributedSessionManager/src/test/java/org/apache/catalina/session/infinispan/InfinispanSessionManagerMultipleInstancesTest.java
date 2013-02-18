/* --------------------------------------------------------------*\
| Copyright (C) e-Spatial Solutions Limited, All rights reserved. |
\* --------------------------------------------------------------*/
package org.apache.catalina.session.infinispan;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.Session;
import org.apache.catalina.session.StandardSession;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.HashMap;

import static org.testng.AssertJUnit.*;

/**
 * InfinispanSessionManagerMultipleInstancesTest
 * User: zvrablikhenek
 * Since: 6/29/12
 */
public class InfinispanSessionManagerMultipleInstancesTest  {
    InfinispanSessionManager managerOne;
    InfinispanSessionManager managerTwo;
    InfinispanSessionManager managerThree;


    /**
     * create session
     * @throws Exception
     */
    @Test
    public void testCreateSession() throws Exception{
        Session emptySession = managerOne.createEmptySession();
        assertTrue( emptySession instanceof StandardSession);
        assertNull(emptySession.getId());

        Session session = managerOne.createSession(null);
        assertTrue( session instanceof InfinispanSession);
        assertNotNull( session.getId());

        String myId="dslfkjsiuiosyfsdljkfklsdfsf";
        Session session2 = managerOne.createSession(myId);
        assertTrue( session2 instanceof InfinispanSession);
        assertNotNull( session2.getId());
        assertEquals(myId, session2.getId());

//        Thread thisThread = Thread.currentThread();
//        thisThread.wait(2000);
        HashMap<String,String> sessionFromOtherManager = managerTwo.getSession(session.getId());
        assertNull(sessionFromOtherManager);
    }

    /**
     * Test add session to manager, use only one manager
     * @throws Exception
     */
    @Test
    public void testAddSession() throws Exception{
        TransactionManager tm = managerOne.getTransactionManager();
        tm.begin();
        Session session = managerOne.createSession(null);
        session.getSession().setAttribute("attrName", "attrValue");
        assertTrue( session instanceof InfinispanSession);
        managerOne.add(session);
        tm.commit();

        Session sessionFromManager = managerOne.findSession(session.getId());
        assertTrue( sessionFromManager instanceof InfinispanSession);
        assertEquals(session.getId(), sessionFromManager.getId());
        assertEquals( "attrValue", sessionFromManager.getSession().getAttribute("attrName"));

    }

    /**
     * Test add session to manager, and find in another manager witht transaction
     * @throws Exception
     */
    @Test
    public void testFindSession() throws Exception{
        TransactionManager tm = managerOne.getTransactionManager();
        tm.begin();
        Session session = managerOne.createSession(null);
        session.getSession().setAttribute("attrName", "attrValue");
        assertTrue(session instanceof InfinispanSession);
        managerOne.add(session);
        tm.commit();

        TransactionManager tm2 = managerThree.getTransactionManager();
        tm2.begin();
        Session sessionFromManager = managerThree.findSession(session.getId());
        assertNotNull(sessionFromManager);
        assertTrue( sessionFromManager instanceof InfinispanSession);
        assertEquals(session.getId(), sessionFromManager.getId());
        assertEquals( "attrValue", sessionFromManager.getSession().getAttribute("attrName"));
        tm2.commit();

    }

    /**
     * Test add session to manager, and find in another manager
     * @throws Exception
     */
    @Test
    public void testFindSessionWithoutTrn() throws Exception{
        Session session = managerOne.createSession(null);
        session.getSession().setAttribute("attrName", "attrValue");
        assertTrue(session instanceof InfinispanSession);
        managerOne.add(session);
        Session sessionFromManager = managerThree.findSession(session.getId());
        assertNotNull(sessionFromManager);
        assertTrue( sessionFromManager instanceof InfinispanSession);
        assertEquals(session.getId(), sessionFromManager.getId());
        assertEquals( "attrValue", sessionFromManager.getSession().getAttribute("attrName"));

    }

    /**
     * Create sessionManagers before each test method
     * @throws LifecycleException
     */
    @BeforeMethod
    private void createSessionManagers() throws LifecycleException {
        managerOne = createSessionManager("zzz", true);
        managerTwo = createSessionManager("zzz", true);
        managerThree = createSessionManager("zzz", true);
    }

    @AfterMethod
    private void removeCacheManagersManagers() throws LifecycleException{
       // this.stopManager(managerOne);
       // this.stopManager(managerTwo);
       // this.stopManager(managerThree);
    }

    private void stopManager( InfinispanSessionManager mgr) throws LifecycleException{
        mgr.cache.stop();
        mgr.manager.stop();
        mgr.stopInternal();
    }

    /**
     * Create session manager to use in
     * @return
     * @throws org.apache.catalina.LifecycleException
     */
    protected InfinispanSessionManager createSessionManager(String nameSuffix, boolean distributed) throws LifecycleException {
        InfinispanSessionManager sessionManager = InfinispanSessionManagerCommon.getInitializedManager(nameSuffix, distributed);

        return sessionManager;
    }
}
