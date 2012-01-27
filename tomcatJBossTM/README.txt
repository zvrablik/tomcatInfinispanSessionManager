#
# JBoss, Home of Professional Open Source
# Copyright 2008, Red Hat Middleware LLC, and individual contributors
# as indicated by the @author tags.
# See the copyright.txt in the distribution for a
# full listing of individual contributors.
# This copyrighted material is made available to anyone wishing to use,
# modify, copy, or redistribute it subject to the terms and conditions
# of the GNU Lesser General Public License, v. 2.1.
# This program is distributed in the hope that it will be useful, but WITHOUT A
# WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
# PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
# You should have received a copy of the GNU Lesser General Public License,
# v.2.1 along with this distribution; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
# MA  02110-1301, USA.
#
# (C) 2008,
# @author JBoss Inc.
#
# @author Jonathan Halliday jonathan.halliday@redhat.com
# @since 2008-05
#

This is the README file for integration of JBossTS JTA 4.3 and Tomcat 6.
ZHenek: Updated to work with JBossTS JTA 4.16.0

It outlines how to use JBossTS to provide support for XA transactions to
web applications running inside Tomcat.

Install steps:

  Edit build.xml to set the locations of JBossTS and Tomcat.

  Run 'ant' to build and install the transaction integration module and test app.

  Run 'ant install-jbossts-to-tomcat' to copy the JBossTS code and config files to tomcat.

  Edit tomcat's conf/server.xml to include the lifecycle listener for transaction service
  startup and shutdown:

  <Server ...>
    ...
    <Listener className="org.jboss.jbossts.tomcat.TransactionLifecycleListener"/>
    ...
  </Server>

  Edit tomcat's conf/server.xml to include the test XADataSource, with params appropriate to your db.
  Such Resource must not be placed directly in a webapps's context, but rather declared globally
  and then linked to the webapp via ResourceLink.

  <GlobalNamingResources>
        ...
        <Resource name="jdbc/TestDBGlobal" auth="Container"
            type="javax.sql.DataSource"
            factory="org.jboss.jbossts.tomcat.TransactionalResourceFactory"
            XADataSourceImpl="oracle.jdbc.xa.client.OracleXADataSource"
            xa.setUser="username"
            xa.setPassword="password"
            xa.setURL="jdbc:oracle:thin:@hostname:1521:SID"
            />
        ...
  </GlobalNamingResources>

  Now link to webapp via conf/server.xml or webapps' META-INF/context.xml
  Also add the UserTransaction to the webapps' JNDI via a Transaction element.
  This context setup is already done for you in the case of the demo app.

  <Context ...>
    ...
    <Transaction factory="com.arjuna.ats.internal.jta.transaction.arjunacore.UserTransactionImple"/>

    <ResourceLink name="jdbc/TestDB"
            global="jdbc/TestDBGlobal"
            type="javax.sql.DataSource"/>
    ...
  </Context>

  Copy the database drivers for your datasource into tomcat's lib dir.

  Invoke the test servlet to try running a transaction:
    http://localhost:8080/jbossts-tomcat/test

  Note: If you are using a db other than oracle, you may need to edit the servlet src to use an appropriate SQL dialect.


You can now use the supplied context.xml and Servlet code as a starting point for transactions in your own apps.


Brief explanation of the integration points:

  Integration of JBossTS and Tomcat requires the following:

  Startup and shutdown of the background processes of the transaction manager.
  To achieve this we use a server lifecycle listener (TransactionLifecycleListener)

  Exposing the UserTransaction implementation to the webapps.
  That's what the <Transaction ...> element in the context.xml is for.

  (Optionally, but very useful) Automatically enrolling transactional connections.
  JDBC connections obtained from regular datasources don't support XA Transactions.
  That rules out using the normal connection pooling JDBC Resources that tomcat supports.
  User applications may use tomcat's Resource elements to wire up a db driver's XADataSource
  directly to JNDI, but a) they don't always implement ObjectFactory and
  b) they don't manage the association between connections and transactions for you.

  For ease of use we want something that looks to the user app like it's a regular DataSource
  but which is XA aware and does all the resource association etc automatically.
  JBossTS already has the TransactionalDriver (see JBossTS JTA docs for details).
  The TransactionalResourceFactory provides a wrapper around the TransactionalDriver,
  allowing it to play nice with JNDI and to manage a driver's XADataSource implementation.
  Use the properties XADataSourceImpl (for the driver's XADataSource classname) and
  xa.someMethodName to invoke methods on that class to set properties e.g. db URL.
  See the javadoc of TransactionalResourceFactory for details.
  This class manages the JNDI refs in such a way that TransactionalDrivers's crash recovery
  support will work transparently as long as the Resource is in the server's global JNDI,
  not the webapps. Hence the use of GlobalNamingResources and ResourceLink.

  Note that the XAPool project (http://xapool.experlog.com/) provides similar functionality
  to the TransactionalResourceFactory/TransactionalDriver. It wraps a vendor's XA aware
  driver rather than an XADataSource.  However, it's not recommended for
  two reasons: It does not integrate with our crash recovery support, so users would have to
  provide their own hooks. Nor does it understand that the transaction context of a thread
  may change at any time (e.g. background timeout+rollback) so it does not have the appropriate
  checks for this.


But I want transactions without proper XA.

  Right now db connections fall into two groups: the ones from tomcat's regular, non-transaction
  aware datasources, on which you must call begin/commit/rollback directly though the Connection,
  plus XA aware ones from a db driver's XADataSource via the TransactionalResourceFactory,
  which participate in XA transaction automatically and are committed or rolled back via UserTransaction.

  However, there is a third group: regular, non-XA connections that you want to commit/rollback via
  UserTransaction. This model provides ease of use in apps that require multiple databases but don't
  actually need the transactional guarantees that two phase commit provides.

  Because the transaction manager works only in terms of XAResources, the way to achieve this is to
  provide it with a fake XAResource implementation for each non-XA connection. The prepare method
  calls commit on the connection, whilst the commit on the resource does nothing. This is a variation
  on the technique used for the last resource gambit, but relaxes the transactional guarantees further.

  The bad news is, we don't currently provide a DataSource wrapper that does this magic for you.


If you don't want to integrate JBossTS with tomcat, you could also consider:

  http://www.jboss.org/auth/jbossas/  JBossAS, which has tomcat, JBossTS and a JCA already integrated.
  http://jotm.objectweb.org/current/jotm/doc/howto-tomcat-jotm.html (JOTM)
  http://www.onjava.com/pub/a/onjava/2003/07/30/jotm_transactions.html
  http://www.jguru.com/faq/view.jsp?EID=531070 (Tyrex)
  http://wiki.atomikos.org/Documentation/TomcatIntegration (Atomikos)

