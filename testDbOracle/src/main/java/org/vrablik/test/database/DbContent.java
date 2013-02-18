/* --------------------------------------------------------------*\
| Copyright (C) e-Spatial Solutions Limited, All rights reserved. |
\* --------------------------------------------------------------*/
package org.vrablik.test.database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DbContent
 * User: zvrablikhenek
 * Since: 2/15/13
 */
public class DbContent {
    List<DbTable> tables;

    public DbContent(){
        this.tables = new ArrayList<>();
    }

    public void addTable(DbTable table){
        this.tables.add(table);
    }

    public List<DbTable> getTables(){
        return Collections.unmodifiableList(this.tables);
    }
}
