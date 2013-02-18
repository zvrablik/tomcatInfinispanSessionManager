/* --------------------------------------------------------------*\
| Copyright (C) e-Spatial Solutions Limited, All rights reserved. |
\* --------------------------------------------------------------*/
package org.vrablik.test.database;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    
    public void set(String key, String value) throws RuntimeException{

        //each pool should point to different schema or better to different database
        setToTable(key, value, "testjta1", "sqlPool1");

        setToTable(key, value, "testjta2", "sqlPool2");
    }

    /**
     * Get content of both db tables
     * @return
     * @throws SQLException
     */
    public DbContent getDbContent() throws SQLException {

        DbContent database = new DbContent();

        DbTable table = getTable("sqlPool1", "testjta1");
        database.addTable(table);

        DbTable table2 = getTable("sqlPool2", "testjta2");
        database.addTable(table2);

        return database;
    }

    private DbTable getTable(String poolName, String tableName) throws SQLException {
        List<String> columnNames = new ArrayList<>(3);
        columnNames.add("my_id");
        columnNames.add("key");
        columnNames.add("value");

        DbTable table = new DbTable(tableName, columnNames);

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try{
            con = this.getConnection(poolName);
            String sql = "select my_id, key, value from " + tableName;
            ps = con.prepareStatement(sql);
            rs = ps.executeQuery();

            while (rs.next()){
                DbRow row = new DbRow(3);
                row.addValue(rs.getString(2));
                row.addValue(rs.getString(3));
                table.addRow(rs.getLong("my_id"), row);
            }
        } finally {
            this.cleanup(con, ps, rs);
        }
        return table;
    }

    private void setToTable(String key, String value, String tableName, String poolName) {
        Connection con = null;
        PreparedStatement ps = null;
        try {
            con = this.getConnection(poolName);
            String sql = "insert into " + tableName + " values ( ?, ?, ?)";
            ps = con.prepareStatement(sql);
            ps.setInt(1, counter.getAndIncrement());
            ps.setString(2, key);
            ps.setString(3, value);
            ps.execute();

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            this.cleanup(con, ps);
        }
    }

    private void cleanup(Connection con, PreparedStatement ps) {
        this.cleanup(con, ps, null);
    }

    private void cleanup(Connection con, PreparedStatement ps, ResultSet rs) {

        if (rs != null){
            try{
                rs.close();
            } catch (SQLException e){
                e.printStackTrace();
            }
        }

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

            boolean standardDataSource = datasourceObj instanceof DataSource;
            boolean xaDataSource = datasourceObj instanceof XADataSource;

            if ( standardDataSource ) {
                con = ((DataSource) datasourceObj).getConnection();
                System.out.println(" from Datasource Connection class name: " + con.getClass().getName() );
            } else if (xaDataSource) {
                //TODO is this valid, it returns physical connection not the XA connection!
                XADataSource xaDS = (XADataSource) datasourceObj;
                XAConnection xaConnection = xaDS.getXAConnection();
                con = xaConnection.getConnection();
                System.out.println(" from XADatasource Connection class name: " + con.getClass().getName() );
            } else {
                throw new RuntimeException( "No datasource for poolName: " + poolName );
            }
        } catch ( Exception e ){
            throw new RuntimeException( e ); 
        } finally {
            if ( envContext != null ){
                try {
                    envContext.close();
                } catch (NamingException e) {
                    //LEFT EMTPY Tomcat doesn't like closing context
                }
            }
            if ( initial != null){
                try {
                    initial.close();
                } catch (NamingException e) {
                    //LEFT EMTPY Tomcat doesn't like closing context
                }
            }
        }

        return con;
    }
}
