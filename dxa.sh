#!/bin/bash
echo "deploy sessionManager"
DEV_PATH=/home/zvrablik/devel/myProjects/tomcatInfinispanSessionManager
TOMCAT_PATH=/home/zvrablik/tomcat/tomcatXATest


echo "devPath: $DEV_PATH"
echo "tomcatPath: $TOMCAT_PATH"


#cp $DEV_PATH/tomcatJBossTM/build/libs/tomcatJBossTM-.jar $TOMCAT_PATH/lib/jbossTM/tomcatJbossTM.jar
cp $DEV_PATH/testInfinispanCache/build/libs/testInfinispanCache-1.0-SNAPSHOT.jar $TOMCAT_PATH/lib/testInfinispanCache.jar
cp $DEV_PATH/testDbOracle/build/libs/testDbOracle-1.0-SNAPSHOT.jar $TOMCAT_PATH/lib/testDbOracle.jar

echo "deploy test app"
echo "remove old"
rm -rf $TOMCAT_PATH/webapps/xaTest

echo "deploy"
cp $DEV_PATH/xaTest/build/libs/xaTest-1.0-SNAPSHOT.war $TOMCAT_PATH/webapps/xaTest.war

sleep 2 
ls -la $TOMCAT_PATH/webapps/ |grep xaTest
