package org.vrablik.testlb;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Session content with requests log
 * @author zhenek
 *
 */
public class SessionServlet extends HttpServlet {
  public static final String REQUEST_LOG_NAME = "requestLog";
  
    public void doGet (HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        PrintWriter out = getOutput(res);
        //get session and create if it doesn't exist
        HttpSession session = req.getSession();
        
        
        RequestLog oneLog = this.createRequestLog("GetSessionContent", req);
        updateRequestLog(session, oneLog);
        
        Enumeration<String> attributeNames = session.getAttributeNames();
        
        out.print("<html>");
        out.print("<body>");
        out.print("<a href=\"index.jsp\"> Back </a><br/><br/>");
        
        while ( attributeNames.hasMoreElements()){
          String key = attributeNames.nextElement();
          if ( !REQUEST_LOG_NAME.equals(key)){
            renderOneItem(out, session, key);
          }
        }
        
        renderOneItem(out, session, REQUEST_LOG_NAME);
                
        out.print("</body>");
        out.print("</html>");
        
        out.flush();
        out.close();
    }
    
    private void renderOneItem(PrintWriter out, HttpSession session,
        String key) {
       Object value = session.getAttribute(key);
       out.print("key: " + key + " value: " + value );
       out.print("<br/>");
    }
    
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
      String action = request.getParameter("action");
      String message = "Action " + action + " finished successfully";
      
      HttpSession session = request.getSession();
      boolean updateLog = true;
      if ( "addToSession".equals(action)){
        String key = request.getParameter("key");
        String value = request.getParameter("value");
        
        session.setAttribute(key, value);
        message += " Key " + key + " added to session. Value: " + value;
      } else if ( "removeFromSession".equals(action)){
          String key = request.getParameter("key");
          session.removeAttribute(key);
          message += " Key " + key + "removed from session.";
      } else if ( "invalidateSession".equals(action)){
          session.invalidate();
          message += " Session has been invalidated.";
          updateLog = false;
      } else {
        message = "Action is not recognized";
      }
      
      if ( updateLog){
          RequestLog oneLog = this.createRequestLog( action, request );
          updateRequestLog(session, oneLog);
      }
      
      PrintWriter out = this.getOutput(response);
      out.print("<html>");
      out.print("<body>");
      out.print(message);
      out.print("<br/>");
      out.print("<a href=\"index.jsp\"> Back </a><br/><br/>");
      out.print("</body>");
      out.print("</html>");
      
      out.flush();
      out.close();
    }
    
    private RequestLog createRequestLog(String action, HttpServletRequest request) throws UnknownHostException{
      InetAddress addr = InetAddress.getLocalHost();
      String localName = addr.getHostName();
      byte[] ipAddr = addr.getAddress();
     
      
      String remoteHost = request.getRemoteHost();
      String remoteAddr = request.getRemoteAddr();
      
      RequestLog oneLog = new RequestLog(localName, ipAddr, remoteHost, remoteAddr, new Date(), action);
      
      return oneLog;
    }
    

    private void updateRequestLog(HttpSession session, RequestLog oneLog) {
      List<RequestLog> logs = (List<RequestLog>)session.getAttribute(REQUEST_LOG_NAME);

      if ( logs == null ){
        logs = new ArrayList<RequestLog>();
        logs.add(oneLog);
      } else {
        //newes log is first
        List<RequestLog>newLogs = new ArrayList<RequestLog>(logs.size()+1);
        newLogs.add(oneLog);
        newLogs.addAll(logs);
        logs = newLogs;
      }

      session.setAttribute(REQUEST_LOG_NAME, logs);
    }
    

    private PrintWriter getOutput(HttpServletResponse res) throws IOException {
      res.setCharacterEncoding("UTF8");
      res.setContentType("text/html");
      PrintWriter out = res.getWriter();
      return out;
    }


}

