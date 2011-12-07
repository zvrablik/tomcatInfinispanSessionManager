package org.apache.catalina.session.ispn;

  import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.Registry;
import org.infinispan.Cache;


/**
 * Copy of ManagerBase but the sessions variable is not defined.
 * I did not changes unless needed to have minimum changes to original ManagerBase class.
 * But I guess all members should be volatile!
 * 
 * abstract method to get sessions reference is used instead
 * 
 * @author zhenek
 *
 */
  public abstract class InfinispanSessionManagerBase implements Manager, MBeanRegistration {
      protected Log log = LogFactory.getLog(InfinispanSessionManagerBase.class);

      // ----------------------------------------------------- Instance Variables

      protected DataInputStream randomIS=null;
      protected String devRandomSource="/dev/urandom";

      /**
       * The default message digest algorithm to use if we cannot use
       * the requested one.
       */
      protected static final String DEFAULT_ALGORITHM = "MD5";


      /**
       * The message digest algorithm to be used when generating session
       * identifiers.  This must be an algorithm supported by the
       * <code>java.security.MessageDigest</code> class on your platform.
       */
      protected String algorithm = DEFAULT_ALGORITHM;


      /**
       * The Container with which this Manager is associated.
       */
      protected Container container;


      /**
       * Return the MessageDigest implementation to be used when
       * creating session identifiers.
       */
      protected MessageDigest digest = null;


      /**
       * The distributable flag for Sessions created by this Manager.  If this
       * flag is set to <code>true</code>, any user attributes added to a
       * session controlled by this Manager must be Serializable.
       */
      protected boolean distributable;


      /**
       * A String initialization parameter used to increase the entropy of
       * the initialization of our random number generator.
       */
      protected String entropy = null;


      /**
       * The descriptive information string for this implementation.
       */
      private static final String info = "ManagerBase/1.0";


      /**
       * The default maximum inactive interval for Sessions created by
       * this Manager.
       */
      protected int maxInactiveInterval = 60;


      /**
       * The session id length of Sessions created by this Manager.
       */
      protected int sessionIdLength = 16;


      /**
       * The descriptive name of this Manager implementation (for logging).
       */
      protected static String name = "ManagerBase";


      /**
       * A random number generator to use when generating session identifiers.
       */
      protected Random random = null;


      /**
       * The Java class name of the random number generator class to be used
       * when generating session identifiers.
       */
      protected String randomClass = "java.security.SecureRandom";


      /**
       * The longest time (in seconds) that an expired session had been alive.
       */
      protected int sessionMaxAliveTime;


      /**
       * Average time (in seconds) that expired sessions had been alive.
       */
      protected int sessionAverageAliveTime;


      /**
       * Number of sessions that have expired.
       */
      protected int expiredSessions = 0;

      // Number of sessions created by this manager
      protected int sessionCounter=0;

      protected volatile int maxActive=0;

      private final Object maxActiveUpdateLock = new Object();

      // number of duplicated session ids - anything >0 means we have problems
      protected int duplicates=0;

      protected boolean initialized=false;
      
      /**
       * Processing time during session expiration.
       */
      protected long processingTime = 0;

      /**
       * Iteration count for background processing.
       */
      private int count = 0;


      /**
       * Frequency of the session expiration, and related manager operations.
       * Manager operations will be done once for the specified amount of
       * backgrondProcess calls (ie, the lower the amount, the most often the
       * checks will occur).
       */
      protected int processExpiresFrequency = 6;

      /**
       * The string manager for this package.
       */
      protected static StringManager sm =
          StringManager.getManager(InfinispanSessionManagerBase.class.getPackage().getName());

      /**
       * The property change support for this component.
       */
      protected PropertyChangeSupport support = new PropertyChangeSupport(this);
      
      // ------------------------------------------------------------- Security classes


      private class PrivilegedSetRandomFile
              implements PrivilegedAction<DataInputStream>{
          
          public PrivilegedSetRandomFile(String s) {
              devRandomSource = s;
          }
          
          public DataInputStream run(){
              try {
                  File f=new File( devRandomSource );
                  if( ! f.exists() ) return null;
                  randomIS= new DataInputStream( new FileInputStream(f));
                  randomIS.readLong();
                  if( log.isDebugEnabled() )
                      log.debug( "Opening " + devRandomSource );
                  return randomIS;
              } catch (IOException ex){
                  log.warn("Error reading " + devRandomSource, ex);
                  if (randomIS != null) {
                      try {
                          randomIS.close();
                      } catch (Exception e) {
                          log.warn("Failed to close randomIS.");
                      }
                  }
                  devRandomSource = null;
                  randomIS=null;
                  return null;
              }
          }
      }


      // ------------------------------------------------------------- Properties

      /**
       * Return the message digest algorithm for this Manager.
       */
      public String getAlgorithm() {

          return (this.algorithm);

      }


      /**
       * Set the message digest algorithm for this Manager.
       *
       * @param algorithm The new message digest algorithm
       */
      public void setAlgorithm(String algorithm) {

          String oldAlgorithm = this.algorithm;
          this.algorithm = algorithm;
          support.firePropertyChange("algorithm", oldAlgorithm, this.algorithm);

      }


      /**
       * Return the Container with which this Manager is associated.
       */
      public Container getContainer() {

          return (this.container);

      }


      /**
       * Set the Container with which this Manager is associated.
       *
       * @param container The newly associated Container
       */
      public void setContainer(Container container) {

          Container oldContainer = this.container;
          this.container = container;
          support.firePropertyChange("container", oldContainer, this.container);
      }


      /** Returns the name of the implementation class.
       */
      public String getClassName() {
          return this.getClass().getName();
      }


      /**
       * Return the MessageDigest object to be used for calculating
       * session identifiers.  If none has been created yet, initialize
       * one the first time this method is called.
       */
      public synchronized MessageDigest getDigest() {

          if (this.digest == null) {
              long t1=System.currentTimeMillis();
              if (log.isDebugEnabled())
                  log.debug(sm.getString( this.getName() + ".getting", algorithm));
              try {
                  this.digest = MessageDigest.getInstance(algorithm);
              } catch (NoSuchAlgorithmException e) {
                  log.error(sm.getString(this.getName() + ".digest", algorithm), e);
                  try {
                      this.digest = MessageDigest.getInstance(DEFAULT_ALGORITHM);
                  } catch (NoSuchAlgorithmException f) {
                      log.error(sm.getString(this.getName() + ".digest",
                                       DEFAULT_ALGORITHM), e);
                      this.digest = null;
                  }
              }
              if (log.isDebugEnabled())
                  log.debug(sm.getString(this.getName() + ".gotten"));
              long t2=System.currentTimeMillis();
              if( log.isDebugEnabled() )
                  log.debug("getDigest() " + (t2-t1));
          }

          return (this.digest);

      }


      /**
       * Return the distributable flag for the sessions supported by
       * this Manager.
       */
      public boolean getDistributable() {

          return (this.distributable);

      }


      /**
       * Set the distributable flag for the sessions supported by this
       * Manager.  If this flag is set, all user data objects added to
       * sessions associated with this manager must implement Serializable.
       *
       * @param distributable The new distributable flag
       */
      public void setDistributable(boolean distributable) {

          boolean oldDistributable = this.distributable;
          this.distributable = distributable;
          support.firePropertyChange("distributable",
                                     new Boolean(oldDistributable),
                                     new Boolean(this.distributable));

      }


      /**
       * Return the entropy increaser value, or compute a semi-useful value
       * if this String has not yet been set.
       */
      public String getEntropy() {

          // Calculate a semi-useful value if this has not been set
          if (this.entropy == null) {
              // Use APR to get a crypto secure entropy value
              byte[] result = new byte[32];
              boolean apr = false;
              try {
                  String methodName = "random";
                  Class paramTypes[] = new Class[2];
                  paramTypes[0] = result.getClass();
                  paramTypes[1] = int.class;
                  Object paramValues[] = new Object[2];
                  paramValues[0] = result;
                  paramValues[1] = new Integer(32);
                  Method method = Class.forName("org.apache.tomcat.jni.OS")
                      .getMethod(methodName, paramTypes);
                  method.invoke(null, paramValues);
                  apr = true;
              } catch (Throwable t) {
                  // Ignore
              }
              if (apr) {
                  setEntropy(new String(result));
              } else {
                  setEntropy(this.toString());
              }
          }

          return (this.entropy);

      }


      /**
       * Set the entropy increaser value.
       *
       * @param entropy The new entropy increaser value
       */
      public void setEntropy(String entropy) {

          String oldEntropy = entropy;
          this.entropy = entropy;
          support.firePropertyChange("entropy", oldEntropy, this.entropy);
          
      }


      /**
       * Return descriptive information about this Manager implementation and
       * the corresponding version number, in the format
       * <code>&lt;description&gt;/&lt;version&gt;</code>.
       */
      public String getInfo() {

          return (info);

      }


      /**
       * Return the default maximum inactive interval (in seconds)
       * for Sessions created by this Manager.
       */
      public int getMaxInactiveInterval() {

          return (this.maxInactiveInterval);

      }


      /**
       * Set the default maximum inactive interval (in seconds)
       * for Sessions created by this Manager.
       *
       * @param interval The new default value
       */
      public void setMaxInactiveInterval(int interval) {

          int oldMaxInactiveInterval = this.maxInactiveInterval;
          this.maxInactiveInterval = interval;
          support.firePropertyChange("maxInactiveInterval",
                                     new Integer(oldMaxInactiveInterval),
                                     new Integer(this.maxInactiveInterval));

      }


      /**
       * Gets the session id length (in bytes) of Sessions created by
       * this Manager.
       *
       * @return The session id length
       */
      public int getSessionIdLength() {

          return (this.sessionIdLength);

      }


      /**
       * Sets the session id length (in bytes) for Sessions created by this
       * Manager.
       *
       * @param idLength The session id length
       */
      public void setSessionIdLength(int idLength) {

          int oldSessionIdLength = this.sessionIdLength;
          this.sessionIdLength = idLength;
          support.firePropertyChange("sessionIdLength",
                                     new Integer(oldSessionIdLength),
                                     new Integer(this.sessionIdLength));

      }


      /**
       * Return the descriptive short name of this Manager implementation.
       */
      public String getName() {

          return (name);

      }

      /** 
       * Use /dev/random-type special device. This is new code, but may reduce
       * the big delay in generating the random.
       *
       *  You must specify a path to a random generator file. Use /dev/urandom
       *  for linux ( or similar ) systems. Use /dev/random for maximum security
       *  ( it may block if not enough "random" exist ). You can also use
       *  a pipe that generates random.
       *
       *  The code will check if the file exists, and default to java Random
       *  if not found. There is a significant performance difference, very
       *  visible on the first call to getSession ( like in the first JSP )
       *  - so use it if available.
       */
      public void setRandomFile( String s ) {
          // as a hack, you can use a static file - and generate the same
          // session ids ( good for strange debugging )
          if (Globals.IS_SECURITY_ENABLED){
              randomIS = AccessController.doPrivileged(new PrivilegedSetRandomFile(s));
          } else {
              try{
                  devRandomSource=s;
                  File f=new File( devRandomSource );
                  if( ! f.exists() ) return;
                  randomIS= new DataInputStream( new FileInputStream(f));
                  randomIS.readLong();
                  if( log.isDebugEnabled() )
                      log.debug( "Opening " + devRandomSource );
              } catch( IOException ex ) {
                  log.warn("Error reading " + devRandomSource, ex);
                  if (randomIS != null) {
                      try {
                          randomIS.close();
                      } catch (Exception e) {
                          log.warn("Failed to close randomIS.");
                      }
                  }
                  devRandomSource = null;
                  randomIS=null;
              }
          }
      }

      public String getRandomFile() {
          return devRandomSource;
      }


      /**
       * Return the random number generator instance we should use for
       * generating session identifiers.  If there is no such generator
       * currently defined, construct and seed a new one.
       */
      public Random getRandom() {
          if (this.random == null) {
              // Calculate the new random number generator seed
              long seed = System.currentTimeMillis();
              long t1 = seed;
              char entropy[] = getEntropy().toCharArray();
              for (int i = 0; i < entropy.length; i++) {
                  long update = ((byte) entropy[i]) << ((i % 8) * 8);
                  seed ^= update;
              }
              try {
                  // Construct and seed a new random number generator
                  Class clazz = Class.forName(randomClass);
                  this.random = (Random) clazz.newInstance();
                  this.random.setSeed(seed);
              } catch (Exception e) {
                  // Fall back to the simple case
                  log.error(sm.getString(this.getName() + ".random", randomClass),
                          e);
                  this.random = new java.util.Random();
                  this.random.setSeed(seed);
              }
              if(log.isDebugEnabled()) {
                  long t2=System.currentTimeMillis();
                  if( (t2-t1) > 100 )
                      log.debug(sm.getString(this.getName() + ".seeding", randomClass) + " " + (t2-t1));
              }
          }
          
          return (this.random);

      }


      /**
       * Return the random number generator class name.
       */
      public String getRandomClass() {

          return (this.randomClass);

      }


      /**
       * Set the random number generator class name.
       *
       * @param randomClass The new random number generator class name
       */
      public void setRandomClass(String randomClass) {

          String oldRandomClass = this.randomClass;
          this.randomClass = randomClass;
          support.firePropertyChange("randomClass", oldRandomClass,
                                     this.randomClass);

      }


      /**
       * Gets the number of sessions that have expired.
       *
       * @return Number of sessions that have expired
       */
      public int getExpiredSessions() {
          return expiredSessions;
      }


      /**
       * Sets the number of sessions that have expired.
       *
       * @param expiredSessions Number of sessions that have expired
       */
      public void setExpiredSessions(int expiredSessions) {
          this.expiredSessions = expiredSessions;
      }

      public long getProcessingTime() {
          return processingTime;
      }


      public void setProcessingTime(long processingTime) {
          this.processingTime = processingTime;
      }
      
      /**
       * Return the frequency of manager checks.
       */
      public int getProcessExpiresFrequency() {

          return (this.processExpiresFrequency);

      }

      /**
       * Set the manager checks frequency.
       *
       * @param processExpiresFrequency the new manager checks frequency
       */
      public void setProcessExpiresFrequency(int processExpiresFrequency) {

          if (processExpiresFrequency <= 0) {
              return;
          }

          int oldProcessExpiresFrequency = this.processExpiresFrequency;
          this.processExpiresFrequency = processExpiresFrequency;
          support.firePropertyChange("processExpiresFrequency",
                                     new Integer(oldProcessExpiresFrequency),
                                     new Integer(this.processExpiresFrequency));

      }
      // --------------------------------------------------------- Public Methods


      /**
       * Implements the Manager interface, direct call to processExpires
       */
      public void backgroundProcess() {
          count = (count + 1) % processExpiresFrequency;
          if (count == 0)
              processExpires();
      }

      /**
       * Invalidate all sessions that have expired.
       */
      public void processExpires() {

          long timeNow = System.currentTimeMillis();
          Session sessions[] = findSessions();
          int expireHere = 0 ;
          
          if(log.isDebugEnabled())
              log.debug("Start expire sessions " + getName() + " at " + timeNow + " sessioncount " + sessions.length);
          for (int i = 0; i < sessions.length; i++) {
              if (sessions[i]!=null && !sessions[i].isValid()) {
                  expireHere++;
              }
          }
          long timeEnd = System.currentTimeMillis();
          if(log.isDebugEnabled())
               log.debug("End expire sessions " + getName() + " processingTime " + (timeEnd - timeNow) + " expired sessions: " + expireHere);
          processingTime += ( timeEnd - timeNow );

      }

      public void destroy() {
          if( oname != null )
              Registry.getRegistry(null, null).unregisterComponent(oname);
          if (randomIS!=null) {
              try {
                  randomIS.close();
              } catch (IOException ioe) {
                  log.warn("Failed to close randomIS.");
              }
              randomIS=null;
          }

          initialized=false;
          oname = null;
      }
      
      public void init() {
          if( initialized ) return;
          initialized=true;        
          
          log = LogFactory.getLog(InfinispanSessionManagerBase.class);
          
          if( oname==null ) {
              try {
                  StandardContext ctx=(StandardContext)this.getContainer();
                  Engine eng=(Engine)ctx.getParent().getParent();
                  domain=ctx.getEngineName();
                  //ispn is distributable everytime
                  distributable = true;
                  StandardHost hst=(StandardHost)ctx.getParent();
                  String path = ctx.getPath();
                  if (path.equals("")) {
                      path = "/";
                  }   
                  oname=new ObjectName(domain + ":type=Manager,path="
                  + path + ",host=" + hst.getName());
                  Registry.getRegistry(null, null).registerComponent(this, oname, null );
              } catch (Exception e) {
                  log.error("Error registering ",e);
              }
          }
          
          // Initialize random number generation
          getRandomBytes(new byte[16]);
          
          if(log.isDebugEnabled())
              log.debug("Registering " + oname );
                 
      }

      

      /**
       * Add this Session to the set of active Sessions for this Manager.
       *
       * @param session Session to be added
       */
      public void add(Session session) {
          Map<String, Session> sessions = this.getSessions();
          sessions.put(session.getIdInternal(), session);
          int size = sessions.size();
          if( size > maxActive ) {
              synchronized(maxActiveUpdateLock) {
                  if( size > maxActive ) {
                      maxActive = size;
                  }
              }
          }
      }


      /**
       * Add a property change listener to this component.
       *
       * @param listener The listener to add
       */
      public void addPropertyChangeListener(PropertyChangeListener listener) {

          support.addPropertyChangeListener(listener);

      }


      /**
       * Construct and return a new session object, based on the default
       * settings specified by this Manager's properties.  The session
       * id will be assigned by this method, and available via the getId()
       * method of the returned session.  If a new session cannot be created
       * for any reason, return <code>null</code>.
       * 
       * @exception IllegalStateException if a new session cannot be
       *  instantiated for any reason
       * @deprecated
       */
      public Session createSession() {
          return createSession(null);
      }
      
      
      /**
       * Construct and return a new session object, based on the default
       * settings specified by this Manager's properties.  The session
       * id specified will be used as the session id.  
       * If a new session cannot be created for any reason, return 
       * <code>null</code>.
       * 
       * @param sessionId The session id which should be used to create the
       *  new session; if <code>null</code>, a new session id will be
       *  generated
       * @exception IllegalStateException if a new session cannot be
       *  instantiated for any reason
       */
      public Session createSession(String sessionId) {
         
          if (sessionId == null) {
              sessionId = generateSessionId();
              
              while (this.existsSessionId(sessionId)){
                  sessionId = generateSessionId();
              }
          };
          
          // Recycle or create a Session instance
          Session session = createEmptySession(sessionId);

          // Initialize the properties of the new session and return it
          session.setNew(true);
          session.setValid(true);
          session.setCreationTime(System.currentTimeMillis());
          session.setMaxInactiveInterval(this.maxInactiveInterval);
         
          // FIXME WHy we need no duplication check?
          /*         
               synchronized (sessions) {
                  while (sessions.get(sessionId) != null) { // Guarantee
                      // uniqueness
                      duplicates++;
                      sessionId = generateSessionId();
                  }
              }
          */
              
              // FIXME: Code to be used in case route replacement is needed
              /*
          } else {
              String jvmRoute = getJvmRoute();
              if (getJvmRoute() != null) {
                  String requestJvmRoute = null;
                  int index = sessionId.indexOf(".");
                  if (index > 0) {
                      requestJvmRoute = sessionId
                              .substring(index + 1, sessionId.length());
                  }
                  if (requestJvmRoute != null && !requestJvmRoute.equals(jvmRoute)) {
                      sessionId = sessionId.substring(0, index) + "." + jvmRoute;
                  }
              }
              */
          session.setId(sessionId);
          sessionCounter++;

          return (session);

      }

      /**
       * Return the active Session, associated with this Manager, with the
       * specified session id (if any); otherwise return <code>null</code>.
       *
       * @param id The session id for the session to be returned
       *
       * @exception IllegalStateException if a new session cannot be
       *  instantiated for any reason
       * @exception IOException if an input/output error occurs while
       *  processing this request
       */
      public Session findSession(String id) throws IOException {
        Map<String, Session> sessions = this.getSessions();
          if (id == null)
              return (null);
          Session session = (Session) sessions.get(id);
          
          if ( session == null ){
              session = this.createSessionFromCache(id);
          }
          
          return session;
      }


      /**
       * Return the set of active Sessions associated with this Manager.
       * If this Manager has no active Sessions, a zero-length array is returned.
       */
      public Session[] findSessions() {
          Map<String, Session> sessions = this.getSessions();
          return sessions.values().toArray(new Session[0]);
      }

      /**
       * Remove a property change listener from this component.
       *
       * @param listener The listener to remove
       */
      public void removePropertyChangeListener(PropertyChangeListener listener) {

          support.removePropertyChangeListener(listener);

      }


      /**
       * Change the session ID of the current session to a new randomly generated
       * session ID.
       * 
       * @param session   The session to change the session ID for
       */
      public void changeSessionId(Session session) {
          session.setId(generateSessionId());
      }
      
      
      // ------------------------------------------------------ Protected Methods


      /**
       * Get new session class to be used in the doLoad() method.
       */
      protected InfinispanStandardSession getNewSession(Cache<String, ?> cache, String sessionId ) {
          return new InfinispanStandardSession(this, cache, sessionId );
      }


      protected void getRandomBytes(byte bytes[]) {
          // Generate a byte array containing a session identifier
          if (devRandomSource != null && randomIS == null) {
              setRandomFile(devRandomSource);
          }
          if (randomIS != null) {
              try {
                  int len = randomIS.read(bytes);
                  if (len == bytes.length) {
                      return;
                  }
                  if(log.isDebugEnabled())
                      log.debug("Got " + len + " " + bytes.length );
              } catch (Exception ex) {
                  // Ignore
              }
              devRandomSource = null;
              
              try {
                  randomIS.close();
              } catch (Exception e) {
                  log.warn("Failed to close randomIS.");
              }
              
              randomIS = null;
          }
          getRandom().nextBytes(bytes);
      }


      /**
       * Generate and return a new session identifier.
       */
      protected synchronized String generateSessionId() {
          Map<String, Session> sessions = this.getSessions();
          byte random[] = new byte[16];
          String jvmRoute = getJvmRoute();
          String result = null;

          // Render the result as a String of hexadecimal digits
          StringBuffer buffer = new StringBuffer();
          do {
              int resultLenBytes = 0;
              if (result != null) {
                  buffer = new StringBuffer();
                  duplicates++;
              }

              while (resultLenBytes < this.sessionIdLength) {
                  getRandomBytes(random);
                  random = getDigest().digest(random);
                  for (int j = 0;
                  j < random.length && resultLenBytes < this.sessionIdLength;
                  j++) {
                      byte b1 = (byte) ((random[j] & 0xf0) >> 4);
                      byte b2 = (byte) (random[j] & 0x0f);
                      if (b1 < 10)
                          buffer.append((char) ('0' + b1));
                      else
                          buffer.append((char) ('A' + (b1 - 10)));
                      if (b2 < 10)
                          buffer.append((char) ('0' + b2));
                      else
                          buffer.append((char) ('A' + (b2 - 10)));
                      resultLenBytes++;
                  }
              }
              if (jvmRoute != null) {
                  buffer.append('.').append(jvmRoute);
              }
              result = buffer.toString();
          } while (sessions.containsKey(result));
          return (result);

      }


      // ------------------------------------------------------ Protected Methods


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


      // -------------------------------------------------------- Package Methods


      public void setSessionCounter(int sessionCounter) {
          this.sessionCounter = sessionCounter;
      }


      /** 
       * Total sessions created by this manager.
       *
       * @return sessions created
       */
      public int getSessionCounter() {
          return sessionCounter;
      }


      /** 
       * Number of duplicated session IDs generated by the random source.
       * Anything bigger than 0 means problems.
       *
       * @return The count of duplicates
       */
      public int getDuplicates() {
          return duplicates;
      }


      public void setDuplicates(int duplicates) {
          this.duplicates = duplicates;
      }


      /** 
       * Returns the number of active sessions
       *
       * @return number of sessions active
       */
      public int getActiveSessions() {
          Map<String, Session> sessions = this.getSessions();
          return sessions.size();
      }


      /**
       * Max number of concurrent active sessions
       *
       * @return The highest number of concurrent active sessions
       */
      public int getMaxActive() {
          return maxActive;
      }


      public void setMaxActive(int maxActive) {
          synchronized (maxActiveUpdateLock) {
              this.maxActive = maxActive;
          }
      }


      /**
       * Gets the longest time (in seconds) that an expired session had been
       * alive.
       *
       * @return Longest time (in seconds) that an expired session had been
       * alive.
       */
      public int getSessionMaxAliveTime() {
          return sessionMaxAliveTime;
      }


      /**
       * Sets the longest time (in seconds) that an expired session had been
       * alive.
       *
       * @param sessionMaxAliveTime Longest time (in seconds) that an expired
       * session had been alive.
       */
      public void setSessionMaxAliveTime(int sessionMaxAliveTime) {
          this.sessionMaxAliveTime = sessionMaxAliveTime;
      }


      /**
       * Gets the average time (in seconds) that expired sessions had been
       * alive.
       *
       * @return Average time (in seconds) that expired sessions had been
       * alive.
       */
      public int getSessionAverageAliveTime() {
          return sessionAverageAliveTime;
      }


      /**
       * Sets the average time (in seconds) that expired sessions had been
       * alive.
       *
       * @param sessionAverageAliveTime Average time (in seconds) that expired
       * sessions had been alive.
       */
      public void setSessionAverageAliveTime(int sessionAverageAliveTime) {
          this.sessionAverageAliveTime = sessionAverageAliveTime;
      }


      /** 
       * For debugging: return a list of all session ids currently active
       *
       */
      public String listSessionIds() {
          Map<String, Session> sessions = this.getSessions();
        
          StringBuffer sb=new StringBuffer();
          Iterator keys = sessions.keySet().iterator();
          while (keys.hasNext()) {
              sb.append(keys.next()).append(" ");
          }
          return sb.toString();
      }


      /** 
       * For debugging: get a session attribute
       *
       * @param sessionId
       * @param key
       * @return The attribute value, if found, null otherwise
       */
      public String getSessionAttribute( String sessionId, String key ) {
          Map<String, Session> sessions = this.getSessions();
          Session s = (Session) sessions.get(sessionId);
          if( s==null ) {
              if(log.isInfoEnabled())
                  log.info("Session not found " + sessionId);
              return null;
          }
          Object o=s.getSession().getAttribute(key);
          if( o==null ) return null;
          return o.toString();
      }


      /**
       * Returns information about the session with the given session id.
       * 
       * <p>The session information is organized as a HashMap, mapping 
       * session attribute names to the String representation of their values.
       *
       * @param sessionId Session id
       * 
       * @return HashMap mapping session attribute names to the String
       * representation of their values, or null if no session with the
       * specified id exists, or if the session does not have any attributes
       */
      public HashMap getSession(String sessionId) {
          Map<String, Session> sessions = this.getSessions();
          Session s = (Session) sessions.get(sessionId);
          if (s == null) {
              if (log.isInfoEnabled()) {
                  log.info("Session not found " + sessionId + "Test if session exists in distributed cache.");
              }
              
              //try get session from cache and instantiate if such cache exists
              s = createSessionFromCache(sessionId);
              if ( s == null ){
                  if (log.isInfoEnabled()){
                      log.info("Session not found in distributed cache. SessionId: " + sessionId);
                  }
                  return null;
              }
          }

          Enumeration ee = s.getSession().getAttributeNames();
          if (ee == null || !ee.hasMoreElements()) {
              return null;
          }

          HashMap map = new HashMap();
          while (ee.hasMoreElements()) {
              String attrName = (String) ee.nextElement();
              map.put(attrName, getSessionAttribute(sessionId, attrName));
          }

          return map;
      }


      public void expireSession( String sessionId ) {
          Map<String, Session> sessions = this.getSessions();
          Session s=(Session)sessions.get(sessionId);
          if( s==null ) {
              if(log.isInfoEnabled())
                  log.info("Session not found " + sessionId);
              return;
          }
          s.expire();
      }

      public long getLastAccessedTimestamp( String sessionId ) {
          Map<String, Session> sessions = this.getSessions();
          Session s=(Session)sessions.get(sessionId);
          if(s== null)
              return -1 ;
          return s.getLastAccessedTime();
      }
    
      public String getLastAccessedTime( String sessionId ) {
          Map<String, Session> sessions = this.getSessions();
          Session s=(Session)sessions.get(sessionId);
          if( s==null ) {
              if(log.isInfoEnabled())
                  log.info("Session not found " + sessionId);
              return "";
          }
          return new Date(s.getLastAccessedTime()).toString();
      }

      public String getCreationTime( String sessionId ) {
          Map<String, Session> sessions = this.getSessions();
          Session s=(Session)sessions.get(sessionId);
          if( s==null ) {
              if(log.isInfoEnabled())
                  log.info("Session not found " + sessionId);
              return "";
          }
          return new Date(s.getCreationTime()).toString();
      }

      public long getCreationTimestamp( String sessionId ) {
          Map<String, Session> sessions = this.getSessions();
          Session s=(Session)sessions.get(sessionId);
          if(s== null)
              return -1 ;
          return s.getCreationTime();
      }

      // -------------------- JMX and Registration  --------------------
      protected String domain;
      protected ObjectName oname;
      protected MBeanServer mserver;

      public ObjectName getObjectName() {
          return oname;
      }

      public String getDomain() {
          return domain;
      }

      public ObjectName preRegister(MBeanServer server,
                                    ObjectName name) throws Exception {
          oname=name;
          mserver=server;
          domain=name.getDomain();
          return name;
      }

      public void postRegister(Boolean registrationDone) {
      }

      public void preDeregister() throws Exception {
      }

      public void postDeregister() {
      }

      /**
       * return sessions
       * @return
       */
      abstract Map<String, Session> getSessions();
      
      /**
       * Remove this Session from the active Sessions for this Manager.
       *
       * @param session Session to be removed
       */
      abstract public void remove(Session session);
      
      abstract Session createEmptySession(String sessionId);
      
      /**
       * Remove jvmRoute suffix from session id.
       * 
       * @param sessionId id with jvmRoute suffix
       * @return session id without jvmRoute siffix, same value if jvmRoute suffix is not used as part of session id
       */
      abstract protected String stripJvmRoute(String sessionId);
      
      /**
       * Remove any suffix starting with dot (.)
       * 
       * @param sessionId session id with possible . suffix
       * 
       * @return sessionId without any dot suffix
       */
      abstract protected String stripDotSuffix(String sessionId);
      
      /**
       * Get session if exists in distributed cache or null
       * set session to local sessions if exist in cache
       * 
       * @param sessionId requested sessionId ( including jvmRoute if specified )
       * @return
       */
      abstract protected Session createSessionFromCache(String sessionId);
      
      /**
       * Check if session id is used in cluster
       * @param sessionId
       * @return true if sessionId already used in cluster
       */
      abstract protected boolean existsSessionId(String sessionId);
  }
