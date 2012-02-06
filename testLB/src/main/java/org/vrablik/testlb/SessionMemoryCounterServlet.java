package org.vrablik.testlb;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import memoryagent.ObjectMemoryCounter;


/**
 * Count session items memory consumption.
 * 
 * @author zhenek
 *
 */
public class SessionMemoryCounterServlet extends HttpServlet {
    
    /**
     * Compute session memory consumption
     * @param req
     * @param res
     * @throws ServletException
     * @throws IOException
     */
    public void doGet (HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        
        HttpSession session = req.getSession();
        Enumeration<String> attributeNames = session.getAttributeNames();
        Map<String, Object> sessionAttributes = new HashMap<String, Object>();
        
        while (attributeNames.hasMoreElements()){
            String attrName = attributeNames.nextElement();
            Object obj = session.getAttribute(attrName);
            sessionAttributes.put(attrName, obj); 
        }
        
        Map<String, Double> sessionMemorySize = ObjectMemoryCounter.getMemorySizePerItem(sessionAttributes, 1024);
        long sumSize = 0;
        
        PrintWriter out = null;
        try {
            out = this.getOutput(res);
            out.print("<html>");
            out.print("<body>");
            out.print("Size of session items in kB");
            out.print("<br/>");
            Set<Entry<String, Double>> entrySet = sessionMemorySize.entrySet();
            for (Entry<String, Double> item : entrySet) {
                out.print(item.getKey());
                out.print(": size(kB)&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;  ");
                out.print(item.getValue().doubleValue());
                out.print("<br/>");
                
                sumSize += item.getValue();
            }
            
            out.print("<br/>");
            out.print("<br/>");
            out.print("SumSize(kB) :");
            out.print(sumSize );
            out.print("<br/>");
            
            out.print("<br/>");
            out.print("</body>");
            out.print("</html>");
            out.flush();
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
    
    private PrintWriter getOutput(HttpServletResponse res) throws IOException {
        res.setCharacterEncoding("UTF8");
        res.setContentType("text/html");
        PrintWriter out = res.getWriter();
        return out;
      }

    
}
