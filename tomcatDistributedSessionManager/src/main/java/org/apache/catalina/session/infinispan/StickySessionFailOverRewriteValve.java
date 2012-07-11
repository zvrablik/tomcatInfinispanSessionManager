package org.apache.catalina.session.infinispan;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

public class StickySessionFailOverRewriteValve extends ValveBase {

    /**
     * logger
     */
    private static org.apache.juli.logging.Log log = org.apache.juli.logging.LogFactory
            .getLog(StickySessionFailOverRewriteValve.class);

    /**
     * The descriptive information about this implementation.
     */
    protected static final String info = "StickySessionFailOverRewriteValve/1.0";
    
    /**
     * Return descriptive information about this implementation.
     */
    public String getInfo() {

        return (info);

    }

    /**
     * Invoke session id rewrite if needed
     * @param request
     * @param response
     * @throws IOException
     * @throws ServletException
     */
    public void invoke(Request request, Response response) throws IOException, ServletException {
        String localJvmRoute = this.getJvmRoute();
        String requestSessionId = request.getRequestedSessionId();
        boolean performRewrite = localJvmRoute != null && localJvmRoute.length() > 0 
                && requestSessionId != null && requestSessionId.length() > 0;
                
        if ( performRewrite && !requestSessionId.endsWith(localJvmRoute)){
            this.rewriteSessionId(request, response, requestSessionId, localJvmRoute);
        }
        // Pass this request on to the next valve in our pipeline
        getNext().invoke(request, response);
    }

    /**
     * Change session id in request to use actual tomcat server
     * after original node failed.
     * 
     * @param request
     * @param response
     * @param sessionId       request sessionId ( including previous node jvmRoute)
     * @param localJvmRoute   actual server jvmRoute
     */
    protected void rewriteSessionId(
            Request request, Response response,String sessionId, String localJvmRoute) {
        if (log.isDebugEnabled()) {
            log.debug("SessionId must be rewritten. SessionId: " + sessionId + " local jvmRoute: " + localJvmRoute);
        }
        
        String newSessionId = sessionId;
        int index = sessionId.indexOf(".");
        if (index > 0) {
            newSessionId = sessionId.substring(0, index) + "." + localJvmRoute;
        }
        
        request.changeSessionId(newSessionId);
    }

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
     * log valve started
     * @exception LifecycleException
     *                if this component detects a fatal error that prevents this
     *                component from being used
     */
    public void startInternal() throws LifecycleException {

        super.startInternal();

        if (log.isInfoEnabled()) {
            log.info(info + " started.");
        }
    }

    /**
     * log valve stopped
     * @exception LifecycleException
     *                if this component detects a fatal error that needs to be
     *                reported
     */
    public void stopInternal() throws LifecycleException {

        if (log.isInfoEnabled()){
            log.info(info + " stopped.");
        }
        super.stopInternal();

    }
}
