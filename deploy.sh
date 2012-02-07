#!/bin/bash
echo "deploy sessionManager"
DEV_PATH=~/devel/myProjects/tomcatInfinispanSessionManager
#TOMCAT_PATH=/home/zvrablik/ismart/testLBTomcat
TOMCAT_PATH=/home/zvrablik/ismart/ismartHEAD

echo "devPath: $DEV_PATH"
echo "tomcatPath: $TOMCAT_PATH"

cp $DEV_PATH/ispnSM/build/libs/ispnSM-prototype.jar $TOMCAT_PATH/lib/ispnSM.jar
cp $DEV_PATH/tomcatJBossTM/build/libs/tomcatJBossTM-prototype.jar $TOMCAT_PATH/lib/jbossTM/tomcatJbossTM.jar
cp $DEV_PATH/memoryAgent/build/libs/memoryAgent-prototype.jar $TOMCAT_PATH/lib/memoryAgent.jar
cp $DEV_PATH/tests/testInfinispanCache/build/libs/tests/testInfinispanCache-prototype.jar $TOMCAT_PATH/lib/testInfinispanCache.jar

echo "deploy test app"
echo "remove old"
rm -rf $TOMCAT_PATH/webapps/testLB
rm -rf $TOMCAT_PATH/webapps/testLB2
echo "deploy"
cp $DEV_PATH/tests/testLB/build/libs/tests/testLB-prototype.war $TOMCAT_PATH/webapps/testLB.war
cp $DEV_PATH/tests/testLB/build/libs/tests/testLB-prototype.war $TOMCAT_PATH/webapps/testLB2.war
sleep 5
ls -la $TOMCAT_PATH/webapps/ |grep testLB
