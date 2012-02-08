package org.vrablik.testlb;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Exposed session add, get, remove, update actions
 * 
 * @author zhenek
 * 
 */
public class SessionTestServlet extends HttpServlet {
    
    /**
     * 
     */
    private static final long serialVersionUID = -8415580532639538574L;
    
    
    protected Log log = LogFactory.getLog(SessionTestServlet.class);

    /**
     * Get session value by key
     */
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession();
        String key = (String)req.getParameter("key");
        
        res.setStatus(HttpServletResponse.SC_OK);
        res.setContentType("text/plain");
        String message = "";
        String value = null;
        
        if ( key == null){
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            message = " Key value is null";
            log.error(message);
        } else {
            
            value = (String)session.getAttribute(key);
            
            if ( value == null){
                message = "Value doesn't exist. Key: " + key;
                res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                log.error(message);
            }
        }
        
        PrintWriter writer = null;
        try{
            writer = res.getWriter();
            
            if ( value != null && ! (message.length() > 0 ) ){
                writer.write(value);
            } else {
                writer.write(message);
            }
            
            writer.flush();
            res.flushBuffer();
        
        } finally {
            if (writer != null){
                writer.close();
            }
        }
    }

    /**
     * put value with key to session
     */
    public void doPut(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        HttpSession session = req.getSession();
        String key = (String)req.getParameter("key");
        String value = (String)req.getParameter("value");
        
        if ( key != null && value != null){
            session.setAttribute(key, value);
            res.setStatus(HttpServletResponse.SC_OK);
        } else {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            PrintWriter writer = null;
            try{
                writer = res.getWriter();
                String message = "Attributes key and value must be specified to put into session.";
                writer.write(message);
                writer.flush();
                res.flushBuffer();
                log.error(message);
            } finally {
                if (writer != null){
                    writer.close();
                }
            }
        }
    }
    
    /**
     * Delete entry key from session
     */
    public void doDelete(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        HttpSession session = req.getSession();
        String key = (String)req.getParameter("key");
        
        if ( key != null ){
            session.removeAttribute(key);
            res.setStatus(HttpServletResponse.SC_OK);
        } else {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            
            PrintWriter writer = null;
            try{
                writer = res.getWriter();
                String message = "Attribute key must be specified to remove from session.";
                writer.write(message);
                writer.flush();
                res.flushBuffer();
                log.error(message);
            } finally {
                if (writer != null){
                    writer.close();
                }
            }
        }
    }
    
    /**
     * Call PUT or DELETE from browser
     */
    public void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        String action = req.getParameter("action");
        
        if ( "put".equals(action) ){
            this.doPut(req, res);
        } else if ( "delete".equals(action)){
            this.doDelete(req, res);
        } else {
            String message = "Action " + action + " not found.";
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            PrintWriter writer = null;
            try{
                writer = res.getWriter();
                writer.write(message);
                writer.flush();
                res.flushBuffer();
                log.error(message);
            } finally {
                if (writer != null){
                    writer.close();
                }
            }
        }
    }
}
