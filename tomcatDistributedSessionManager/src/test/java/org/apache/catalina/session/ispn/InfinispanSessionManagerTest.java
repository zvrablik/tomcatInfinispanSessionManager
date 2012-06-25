package org.apache.catalina.session.ispn;


import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import org.testng.AssertJUnit;
import mockit.Expectations;
import mockit.Mocked;

import org.apache.catalina.Engine;

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
        AssertJUnit.assertNull(newSessionId);
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
        AssertJUnit.assertEquals("testtest", newSessionId);
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
        AssertJUnit.assertEquals("testtest.tc1", newSessionId);
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
        AssertJUnit.assertEquals("testtest", newSessionId);
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
        AssertJUnit.assertEquals("testtest1", newSessionId);
    }
}
