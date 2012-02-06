/* --------------------------------------------------------------*\
| Copyright (C) e-Spatial Solutions Limited, All rights reserved. |
\* --------------------------------------------------------------*/
package org.jboss.jbossts.tomcat;

import com.arjuna.ats.jta.common.jtaPropertyManager;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.spi.ObjectFactory;
import javax.transaction.TransactionManager;
import java.util.Hashtable;

/**
 * TransactionManagerFactory
 * User: zvrablikhenek
 * Since: 2/2/12
 */
public class TransactionManagerFactory implements ObjectFactory {
    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) throws Exception {

        TransactionManager transactionManager = jtaPropertyManager.getJTAEnvironmentBean().getTransactionManager();

        return transactionManager;
    }
}
