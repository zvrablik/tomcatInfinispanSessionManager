package org.vrablik.testlb;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;

/**
 * Tracking request host details
 * 
 * @author zhenek
 *
 */
public class RequestLog implements Serializable {
  
  
  /**
   * 
   */
  private static final long serialVersionUID = 6647037140658199520L;
  
  private String action;
  private Date time;
  private String localHostName;
  private byte[] localHostAddr;
  private String remoteHostAddr;
  private String remoteHostName;

  /**
   * Log about session actions
   * 
   * @param time      timestamp
   * @param action    what action
   */
  public RequestLog(String localHostName, byte[] localHostAddr, 
      String remoteHostAddr, String remoteHostName, Date time, String action){
    this.action = action;
    this.time = time;
    this.localHostName = localHostName;
    this.localHostAddr = localHostAddr;
    this.remoteHostAddr = remoteHostAddr;
    this.remoteHostName = remoteHostName;
  }

  @Override
  public String toString() {
    return "<br/>RequestLog [action=" + action + ", time=" + time
        + ", localHostName=" + localHostName + ", localHostAddr="
        + Arrays.toString(localHostAddr) + ", browserHostAddr=" + remoteHostAddr
        + ", browserHostName=" + remoteHostName + "]";
  }

  
}
