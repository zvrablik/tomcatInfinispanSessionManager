A. setup loadbalancer
===============================
1) install and configure Apache httpd
2) compile and install mod_jk
3) configure Tomcat workers

B. setup distirbuted session manager
====================================
1) ensure JGroups and Infinispan are able operate in your network (firewals, multicast allowed ....)
2) configure Tomcat infinispan distributed session manager


A 1)
Install Apache httpd or any other web server. See http://httpd.apache.org/
A 2)
See http://tomcat.apache.org/connectors-doc/ for details about mod_jk.
Mod_jk is optional. Using any other load ballancer as HaProxy works without problem.
example of /etc/httpd/conf.d/jk.conf file:
    # Load mod_jk module
    LoadModule    jk_module /usr/lib64/httpd/modules/mod_jk.so
    # Where to find workers.properties
    JkWorkersFile /etc/httpd/conf/workers.properties
    # Where to put jk logs
    JkLogFile     /var/log/httpd/mod_jk.log
    # Set the jk log level [debug/error/info]
    JkLogLevel    debug
    # Select the log format
    JkLogStampFormat "[%a %b %d %H:%M:%S %Y] "
    # JkOptions indicate to send SSL KEY SIZE,
    JkOptions     +ForwardKeySize +ForwardURICompat -ForwardDirectories
    # JkRequestLogFormat set the request format
    JkRequestLogFormat      "%w %V %T"
    # Send servlet for context /docs to worker named tomcat1
    # configure apache httpd to connect only to from espatial network and me
    JkMount /jkmanager/* jkstatus
    JkMount /testLB/* router
    JkMount /testLB router

A 3)
* Engine must be configured with jvm route (sticky session mode)
TomcatHome/conf/server.xml must contain something like this:
<Engine name="Catalina" defaultHost="localhost" jvmRoute="tc1">

* Example of /etc/httpd/conf/workers.properties
    worker.list=router,jkstatus
    worker.router.type=lb
    worker.router.balance_workers=tc1,tc2
    #worker.router.sticky_session=false

    worker.jkstatus.type=status

    #template worker
    worker.template.type=ajp13
    worker.template.port=8009

    worker.tc1.reference=worker.template
    worker.tc1.host=192.168.0.101

    worker.tc2.reference=worker.template
    worker.tc2.host=192.168.0.102


B 1)
JGroups jar contains test application to ensure communication is possible. 
See http://www.jgroups.org/tutorial/html/ch01.html

B 2)
Sticky session mode:
* add valve org.apache.catalina.session.ispn.StickySessionFailOverRewriteValve to conf/server.xml into Engine configuration

<Engine name="Catalina" defaultHost="localhost" jvmRoute="tc1">         
         <Valve className="org.apache.catalina.session.ispn.StickySessionFailOverRewriteValve"/>

Both modes sticky session and non-sticky session:

* define session manager org.apache.catalina.session.ispn.InfinispanSessionManager in conf/context.xml to use this 
session manager in all war applications

<Manager className="org.apache.catalina.session.ispn.InfinispanSessionManager" /> 

* optionally create infinispan configuration file in conf directory to override default infinispan and JGroups
settings. The file name must be: sessionInfinispanConfig<appName>.xml

Application with name example must provide infinispan configuration in file: sessionInfinispanConfigexamples.xml

Example of sessionInfinispan config file:

<?xml version="1.0" encoding="UTF-8"?>
<infinispan xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="urn:infinispan:config:5.0 http://www.infinispan.org/schemas/infinispan-config-5.0.xsd"
            xmlns="urn:infinispan:config:5.0">
  <global>
    <transport clusterName="tomcatSession"/>
    <globalJmxStatistics enabled="true" allowDuplicateDomains="true"  jmxDomain="testLB"/>
  </global>
<!-- use only default cache for all caches created by session manger -->
<!-- to specify custom parameters to one cache create named cache with name _session_attrContainerName
where ContainerName is name of war application -->

  <default>
    <jmxStatistics enabled="true"/>
    <clustering mode="distribution">
      <l1 enabled="false" lifespan="600000"/>
      <hash numOwners="2" rehashRpcTimeout="6000"/>
      <sync/>
    </clustering>
    <invocationBatching enabled="true"/>
  </default>
</infinispan>
