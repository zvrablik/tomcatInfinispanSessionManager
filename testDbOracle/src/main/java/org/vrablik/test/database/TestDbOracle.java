/* --------------------------------------------------------------*\
| Copyright (C) e-Spatial Solutions Limited, All rights reserved. |
\* --------------------------------------------------------------*/
package org.vrablik.test.database;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import java.util.concurrent.atomic.AtomicInteger;

// oracle db table create stms
//  create table testjta(
//        id number,
//        key varchar2(100),
//        value varchar2(100) )

/**
 * TestDbOracle
 * User: zvrablikhenek
 * Since: 2/7/12
 */
public class TestDbOracle {
    private AtomicInteger counter = new AtomicInteger();

    public TestDbOracle(){
        System.out.println("Instantiate TestDbOracle.");
    }
    
    public void set(String key, String value, boolean throwException) throws RuntimeException{

        Connection con = null;
        PreparedStatement ps = null;
        try {
            con = this.getConnection("sqlPool2");
            String sql = "insert into testjta values ( ?, ?, ?)";
            ps = con.prepareStatement(sql);
            ps.setInt(1, counter.getAndIncrement());
            ps.setString(2, key);
            ps.setString(3, value);
            ps.execute();
            
            if ( throwException ){
                throw new RuntimeException("TestDbOracle - exception requested.");
            }

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            this.cleanup(con, ps);
        }
    }

    private void cleanup(Connection con, PreparedStatement ps) {
        if ( ps != null ){
            try {
                ps.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        if ( con != null ){
            try {
                con.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private Connection getConnection(String poolName) {
        Connection con = null;
        Context initial = null;
        Context envContext = null;
        try{
            initial = new InitialContext();
            envContext = (Context)initial.lookup("java:/comp/env/");
            Object datasourceObj = envContext.lookup(poolName);

            if (datasourceObj instanceof DataSource) {
                con = ((DataSource) datasourceObj).getConnection();
            } else if (datasourceObj instanceof XADataSource) {
                con = ((XADataSource) datasourceObj).getXAConnection().getConnection();
                System.out.println(" Connection may not be transactional. Connection class name: " + con.getClass().getName() );
            } else {
                throw new RuntimeException( "No datasource for poolName: " + poolName );
            }
        } catch ( Exception e ){
            throw new RuntimeException( e ); 
        } finally {
            if ( envContext != null){
                try {
                    envContext.close();
                } catch (NamingException e) {
                    e.printStackTrace();
                }
            }
            if ( initial != null){
                try {
                    initial.close();
                } catch (NamingException e) {
                    e.printStackTrace();
                }
            }
        }

        return con;
    }
}
