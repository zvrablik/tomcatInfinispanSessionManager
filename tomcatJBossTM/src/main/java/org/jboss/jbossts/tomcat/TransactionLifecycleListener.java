/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 *
 * (C) 2008,
 * @author JBoss Inc.
 */

package org.jboss.jbossts.tomcat;

import com.arjuna.ats.jdbc.common.jdbcPropertyManager;
import com.arjuna.ats.jta.common.jtaPropertyManager;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleEvent;
import com.arjuna.ats.arjuna.coordinator.TransactionReaper;
import com.arjuna.ats.arjuna.recovery.RecoveryManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.TransactionManager;
import java.util.Hashtable;

/**
 * Hooks JBossTS initialization into the Tomcat 6 server lifecycle.
 * <p/>
 * At first glance it looks like you can just use
 * <Transaction factory="com.arjuna.ats.internal.jta.transaction.arjunacore.UserTransactionImple"/>
 * to configure JBossTS in Tomcat 6. However, there are a couple of issues with this simplistic approach:
 * The <Transaction...> element is valid only in a webapps <Context...>, not in the
 * server's <GlobalNamingResources ...>, wehreas the tx service should be installed globally
 * Second, we need to start recovery after the XADataSources are available (see TransactionalResourceFactory)
 * but not have it depend on any webapps starting. Hence we need to tie in to the server lifecycle explicitly.
 * <p/>
 * Reference this class from tomcat's server.xml, in the <Server ... element:
 * <Listener className="org.jboss.jbossts.tomcat.TransactionLifecycleListener"/>
 *
 * @author Jonathan Halliday jonathan.halliday@redhat.com
 * @author Zdenek Henek vrablik@gmail.com
 *
 * see http://tomcat.apache.org/tomcat-6.0-doc/config/
 *
 */
public class TransactionLifecycleListener implements LifecycleListener
{
    protected Log log = LogFactory.getLog(TransactionLifecycleListener.class);
    private static final boolean TERMINATE_NOW = false;
    RecoveryManager recoveryManager;

    public void lifecycleEvent(LifecycleEvent event)
    {
        if ("start".equals(event.getType()))
        {
            // run the reaper here so it is in the server's classloader context
            // if we started it lazily on first tx it would run with context of the webapp using the tx.
            TransactionReaper.instantiate();
            log.info("TransactionLifecycleListerner - start.");

            // recovery needs the correct JNDI settings.
            // XADataSourceWrapper sets these too as a precaution, but we may run first.
            Hashtable jndiProps = new Hashtable<String,Object>(2);
            jndiProps.put("Context.INITIAL_CONTEXT_FACTORY", System.getProperty(Context.INITIAL_CONTEXT_FACTORY) );
            jndiProps.put("Context.URL_PKG_PREFIXES", System.getProperty(Context.URL_PKG_PREFIXES) );
            
            jdbcPropertyManager.getJDBCEnvironmentBean().setJndiProperties( jndiProps);

            // a 'start' occurs after the Resources in GlobalNamingResources are instantiated,
            // so we can safely start the recovery Thread here.
            recoveryManager = RecoveryManager.manager();
            recoveryManager.startRecoveryManagerThread();

//            TransactionManager transactionManager = jtaPropertyManager.getJTAEnvironmentBean().getTransactionManager();
//            Context initial = null;
//            String jndiContextName = "java:/TransactionManager";
//            try {
//                initial = new InitialContext();
//                initial.bind( jndiContextName, transactionManager);
//            } catch (NamingException e) {
//                log.error("Can't bind transaction manager to JNDI context. Context name:" + jndiContextName, e);
//            }
        }
        else if ("stop".equals(event.getType()))
        {
            log.info("TransactionLifecycleListerner - terminated.");
            recoveryManager.terminate( TERMINATE_NOW );
            TransactionReaper.terminate( TERMINATE_NOW );
        }
    }
}