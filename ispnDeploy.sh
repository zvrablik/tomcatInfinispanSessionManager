#!/bin/bash
echo "deploy sessionManager"
DEV_PATH=~/devel/myProjects/tomcatInfinispanSessionManager
#TOMCAT_PATH=/home/zvrablik/ismart/testLBTomcat
TOMCAT_PATH=~/tomcat/ispnTest

echo "devPath: $DEV_PATH"
echo "tomcatPath: $TOMCAT_PATH"

cp $DEV_PATH/tomcatDistributedSessionManager/build/libs/tomcatDistributedSessionManager-1.0-SNAPSHOT.jar $TOMCAT_PATH/lib/tomcatDistributedSessionManager.jar
#don't use transaction manager yet
#cp $DEV_PATH/tomcatJBossTM/build/libs/tomcatJBossTM-.jar $TOMCAT_PATH/lib/jbossTM/tomcatJbossTM.jar
cp $DEV_PATH/memoryAgent/build/libs/memoryAgent-1.0-SNAPSHOT.jar $TOMCAT_PATH/lib/memoryAgent.jar
cp $DEV_PATH/testInfinispanCache/build/libs/testInfinispanCache-1.0-SNAPSHOT.jar $TOMCAT_PATH/lib/testInfinispanCache.jar
cp $DEV_PATH/testDbOracle/build/libs/testDbOracle-1.0-SNAPSHOT.jar $TOMCAT_PATH/lib/testDbOracle.jar

echo "deploy test app"
echo "remove old"
rm -rf $TOMCAT_PATH/webapps/testLB
rm -rf $TOMCAT_PATH/webapps/testLB2
echo "deploy"
cp $DEV_PATH/testLB/build/libs/testLB-1.0-SNAPSHOT.war $TOMCAT_PATH/webapps/testLB.war
cp $DEV_PATH/testLB/build/libs/testLB-1.0-SNAPSHOT.war $TOMCAT_PATH/webapps/testLB2.war
sleep 5
ls -la $TOMCAT_PATH/webapps/ |grep testLB
