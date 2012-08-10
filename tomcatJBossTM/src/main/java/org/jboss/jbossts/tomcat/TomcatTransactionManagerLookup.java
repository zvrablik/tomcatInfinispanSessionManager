/* --------------------------------------------------------------*\
| Copyright (C) e-Spatial Solutions Limited, All rights reserved. |
\* --------------------------------------------------------------*/
package org.jboss.jbossts.tomcat;

import com.arjuna.ats.jta.common.jtaPropertyManager;
import org.infinispan.transaction.lookup.TransactionManagerLookup;
import org.infinispan.transaction.tm.DummyTransactionManager;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.transaction.TransactionManager;

/**
 * TomcatTransactionManagerLookup JBossTM manager lookup for Tomcat
 *
 * User: zvrablikhenek
 * Since: 2/2/12
 */
public class TomcatTransactionManagerLookup implements TransactionManagerLookup {

    public static final String JNDI_TRANSACTION_MANAGER_NAME = "java:comp/TransactionManager";

    private static final Log log = LogFactory.getLog(TomcatTransactionManagerLookup.class);
    
    private static TransactionManager tm;

    public TomcatTransactionManagerLookup(){
        log.info("TomcatTransactionManagerLookup instantiated.");
    }

    @Override
    public TransactionManager getTransactionManager() throws Exception {
        synchronized ( JNDI_TRANSACTION_MANAGER_NAME ){
            if (tm == null) {
                try{
                    Context ctx = new InitialContext();
                    try{
                        Object tmObj = ctx.lookup(JNDI_TRANSACTION_MANAGER_NAME);

                        if ( tmObj instanceof TransactionManager){
                            tm = (TransactionManager)tmObj;
                        }
                    } catch ( Exception e ){
                        //jndi not found
                        log.debug("TransactionManager not found in JNDI context. Context: " + JNDI_TRANSACTION_MANAGER_NAME);
                        tm = jtaPropertyManager.getJTAEnvironmentBean().getTransactionManager();

                        if ( tm != null ){
                            log.debug("TransactionManager initialized through jtaPropertyManager.");
                        }
                    }

                } catch (Exception e){
                    log.error("Failed to get transaction manager. Use dummy transaction manager provided by Infinispan. Don't use dummy transaction manager in production!", e);
                    tm = DummyTransactionManager.getInstance();
                    log.fallingBackToDummyTm();
                }

                if ( tm == null ){
                    log.error("Transaction manager is null. Use dummy transaction manager provided by Infinispan. Don't use dummy transaction manager in production!");
                    tm = DummyTransactionManager.getInstance();
                    log.fallingBackToDummyTm();
                }
            }
        }
        
        return tm;
    }
}
