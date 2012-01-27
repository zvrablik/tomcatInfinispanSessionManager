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

import javax.naming.spi.ObjectFactory;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;

import java.lang.reflect.Method;
import java.util.Map;
import javax.naming.Name;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.RefAddr;
import javax.sql.XADataSource;


/**
 * TransactionalResourceFactory instances are the integration point to Tomcat 6.
 * By configuring a Resource using this as the factory, we can expose a
 * db driver's native XADataSource to tomcat via the TransactionalDriver, thereby
 * ensuring connections are enlisted in the transaction correctly and that
 * recovery works.
 * <p/>
 * Usually wired up in Tomcat 6 via an entry
 * in the <GlobalNamingResources> section of conf/server.xml
 * Don't put it in a webapps local META-INF/context.xml directly, it needs the
 * server's classloader and global JNDI for recovery to work.
 * <p/>
 * <Resource name="jdbc/TestDBGlobal" auth="Container"
 * type="javax.sql.DataSource"
 * factory="org.jboss.jbossts.tomcat.TransactionalResourceFactory"
 * XADataSourceImpl="oracle.jdbc.xa.client.OracleXADataSource"
 * xa.setUser="username"
 * xa.setPassword="password"
 * xa.setURL="jdbc:oracle:thin:@hostname:1521:SID"
 * />
 *
 * @author Jonathan Halliday jonathan.halliday@redhat.com
 * @author Zdenek Henek vrablik@gmail.com
 *
 * @link tomcat.apache.org/tomcat-6.0-doc/config/globalresources.html
 * @since 2008-05
 */
public class TransactionalResourceFactory implements ObjectFactory
{
    /*
        Implementation Note: many methods here do kludgy things to integrate with tomcat.
        They are protected so that subclasses can override to use alternative kludges if desired.
     */


    /**
     * Create a new XADataSource instance.
     *
     * @param obj The reference object describing the DataSource configuration
     */
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                    Hashtable environment) throws Exception
    {
        Reference ref = (Reference) obj;
        Enumeration addrs = ref.getAll();
        HashMap<String, String> params = new HashMap();

        System.out.println("TransactionalResourceFactory ");

        // convert the Addresses into easier to handle String key/values
        while (addrs.hasMoreElements())
        {
            RefAddr addr = (RefAddr) addrs.nextElement();
            String attrName = addr.getType();
            String attrValue = (String) addr.getContent();
            params.put(attrName, attrValue);
        }

        String classname = params.get("XADataSourceImpl");
        if (classname == null)
        {
            throw new NamingException("No XADataSourceImpl value specified");
        }

        // instantiate the underlying implementation and configure it with the supplied params
        XADataSource xaDataSource = loadXADataSource(classname);
        processParameters(xaDataSource, params);

        // instantiate and return a wrapper that will intercept subsequent calls to the object.
        String fullJNDIName = convertName(name.toString());
        return new XADataSourceWrapper(fullJNDIName, xaDataSource);
    }

    /**
     * Prefix with java: to get correct jndi name
     *
     * @param name datasource name without java: prefix
     *
     * @return add java:$datasourceName$  unless java: present
     */
    protected String convertName(String name)
    {
        //use jdbc as part of global resource name, it is not needed here!
        if ( !name.startsWith("java:") ){
            name = "java:" + name;
        }

        return name;
    }

    /**
     * Take an unconfigured XADataSource and configure it
     * according to the supplied parameters.
     * <p/>
     * Since each vendor's XADataSource implementation has a different API,
     * this is done via. reflection in the following manner:
     * Parameters with names beginning "xa." have this prefix stripped off their name,
     * the remainder of which is regarded as a method name on the XADataSource.
     * The value is passed though to the method call unmodified.
     * e.g. "xa.setUsername"=>"myName"  maps to xaDataSource.setUsername("myName");
     *
     * @param xaDataSource
     * @param params
     * @throws NamingException
     */
    protected void processParameters(XADataSource xaDataSource, Map<String, String> params) throws NamingException
    {
        for (String key : params.keySet())
        {
            if ("factory".equals(key) || "scope".equals(key) || "auth".equals(key) || "XADataSourceImpl".equals(key))
            {
                continue;
            }

            if (!key.startsWith("xa."))
            {
                // TODO log warning of unknown param?
                continue;
            }

            callMethod(xaDataSource, key.substring(3), params.get(key));
        }
    }

    /**
     * Use reflection to call the named method on an xaDataSource implementation.
     * Note: subclass and override this if you need handling of non-String values.
     *
     * @param xaDataSource
     * @param name
     * @param value
     * @throws NamingException
     */
    protected void callMethod(XADataSource xaDataSource, String name, String value) throws NamingException
    {
        try
        {
            Method method = xaDataSource.getClass().getMethod(name, new Class[]{java.lang.String.class});
            method.invoke(xaDataSource, value);
        }
        catch (Exception e)
        {
            NamingException ex = new NamingException("Unable to invoke " + name + " on " + xaDataSource.getClass().getName());
            ex.initCause(e);
            throw ex;
        }
    }

    /**
     * Load and instantiate the given XADataSource implementation class.
     *
     * @param classname
     * @return
     * @throws NamingException
     */
    protected XADataSource loadXADataSource(String classname) throws NamingException
    {

        Class clazz = null;

        try
        {
            clazz = Thread.currentThread().getContextClassLoader().loadClass(classname);
        }
        catch (Exception e)
        {
            NamingException ex = new NamingException("Unable to load " + classname);
            ex.initCause(e);
            throw ex;
        }

        XADataSource xaDataSource = null;

        try
        {
            xaDataSource = (XADataSource) clazz.newInstance();
        }
        catch (Exception e)
        {
            NamingException ex = new NamingException("Unable to instantiate " + classname);
            ex.initCause(e);
            throw ex;
        }

        return xaDataSource;
    }
}

