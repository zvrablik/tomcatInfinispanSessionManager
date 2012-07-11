package org.apache.catalina.session.infinispan;


import mockit.Expectations;
import mockit.Mocked;
import org.apache.catalina.Engine;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Session;
import org.apache.catalina.session.StandardSession;
import org.testng.annotations.BeforeMethod;

import static org.testng.AssertJUnit.*;

import org.testng.annotations.Test;

import java.io.IOException;

public class InfinispanSessionManagerTest {

    @Mocked Engine engine = null;
    
    @BeforeMethod
    public void setUp() throws Exception {
    }
    
    /**
     * Test pass null sessionId,
     * should return null
     */
    @Test
    public void stripJvmRouteNull() {
        InfinispanSessionManager ispnSM = new InfinispanSessionManager();
        String sessionId = null;
        String newSessionId = ispnSM.stripJvmRoute(sessionId);
        assertNull(newSessionId);
    }
     
    /**
     * strip valid session with jvmRoute suffix
     *
     */
    @Test
    public void stripJvmRoute1() {
        final InfinispanSessionManager ispnSM = new InfinispanSessionManager();
        
        new Expectations() {
            {   
                engine.getParent(); returns(null);
                engine.getJvmRoute(); returns("tc1");
            }
        };
        
        ispnSM.setContainer(engine);
        
        String sessionId = "testtest.tc1";
        String newSessionId = ispnSM.stripJvmRoute(sessionId);
        assertEquals("testtest", newSessionId);
    }
    
    /**
     * Don't remove jvmRoute if is different.
     * 
     * Hmm. What about recovery? - need method to strip anything after comman(including comma)?
     */
    @Test
    public void stripJvmRoutetcx() {
        final InfinispanSessionManager ispnSM = new InfinispanSessionManager();
        
        new Expectations() {
            {   
                engine.getParent(); returns(null);
                engine.getJvmRoute(); returns("tcx");
            }
        };
        
        ispnSM.setContainer(engine);
        
        String sessionId = "testtest.tc1";
        String newSessionId = ispnSM.stripJvmRoute(sessionId);
        assertEquals("testtest.tc1", newSessionId);
    }
    
    /**
     * strip valid session with jvmRoute suffix
     *
     */
    @Test
    public void testStripJvmRoute2() {
        final InfinispanSessionManager ispnSM = new InfinispanSessionManager();
        
        new Expectations() {
            {   
                engine.getParent(); returns(null);
                engine.getJvmRoute(); returns("tc1");
            }
        };
        
        ispnSM.setContainer(engine);
        
        String sessionId = "testtest";
        String newSessionId = ispnSM.stripJvmRoute(sessionId);
        assertEquals("testtest", newSessionId);
    }
    
    /**
     * strip valid session with jvmRoute suffix - could be any suffix separated by dot
     *
     */
    @Test
    public void testStripJvmRoute3() {
        final InfinispanSessionManager ispnSM = new InfinispanSessionManager();
        
        new Expectations() {
            {   
            }
        };
        
        ispnSM.setContainer(engine);
        
        String sessionId = "testtest1.bla";
        String newSessionId = ispnSM.stripDotSuffix(sessionId);
        assertEquals("testtest1", newSessionId);
    }


    /**
     * test initialize one session manager
     * @throws LifecycleException
     */
    @Test
    public void testInitSessionManager() throws LifecycleException {

        InfinispanSessionManager initializedManager = createSessionManager("zzz");

        Session session = initializedManager.createEmptySession();

        assertTrue(session instanceof StandardSession);
    }

    /**
     * Create three session managers
     * @throws LifecycleException
     */
    @Test
    public void testInitSessionManagers() throws  LifecycleException{
        InfinispanSessionManager managerOne = createSessionManager("zzz");
        InfinispanSessionManager managerTwo = createSessionManager("zzz");
        InfinispanSessionManager managerThree = createSessionManager("zzz");

        assertEquals("tc_session_contextNamezzz", managerOne.getCacheName());
        assertEquals("tc_session_contextNamezzz", managerTwo.getCacheName());
        assertEquals("tc_session_contextNamezzz", managerThree.getCacheName());
    }

    /**
     * test creating adding and removing sessions
     * @throws LifecycleException
     */
    @Test
    public void testCreateSetGetSessionManager() throws LifecycleException, IOException {

        InfinispanSessionManager manager = createSessionManager("zzz");

        Session session = manager.createSession(null);
        assertTrue( session instanceof InfinispanSession);

        assertNotNull(session.getId());
        manager.add(session);
        String sessionId = session.getId();
        Session rsession = manager.findSession(sessionId);
        assertEquals(sessionId, rsession.getId());

        Session[] sessions = manager.findSessions();
        assertEquals(1, sessions.length);

        manager.remove(rsession);

        sessions = manager.findSessions();
        assertEquals(0, sessions.length);
    }

    /**
     * Create session manager to use in
     * @return
     * @throws LifecycleException
     */
    protected InfinispanSessionManager createSessionManager(String nameSuffix) throws LifecycleException {
        return InfinispanSessionManagerCommon.getInitializedManager(nameSuffix);
    }
}
