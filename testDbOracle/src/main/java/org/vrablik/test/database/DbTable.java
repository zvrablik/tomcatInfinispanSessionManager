/* --------------------------------------------------------------*\
| Copyright (C) e-Spatial Solutions Limited, All rights reserved. |
\* --------------------------------------------------------------*/
package org.vrablik.test.database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DbTable
 * User: zvrablikhenek
 * Since: 2/15/13
 */
public class DbTable {
    List<DbRow> table;
    private List<String> columnNames;
    private String tableName;

    public DbTable(String tableName, List<String> columnNames){
        this.tableName = tableName;
        this.columnNames = Collections.unmodifiableList(new ArrayList<>(columnNames));
        this.table = new ArrayList<>();
    }

    public void addRow(Long id, DbRow row){
        this.table.add(row);
    }

    public List<DbRow> getRows(){
        return Collections.unmodifiableList(this.table);
    }

    public String getTableName(){
        return this.tableName;
    }

    public List<String> getColumnNames(){
        return this.columnNames;
    }
}
