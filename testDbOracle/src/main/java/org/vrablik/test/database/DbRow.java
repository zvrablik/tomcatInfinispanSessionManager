/* --------------------------------------------------------------*\
| Copyright (C) e-Spatial Solutions Limited, All rights reserved. |
\* --------------------------------------------------------------*/
package org.vrablik.test.database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DbRow
 * User: zvrablikhenek
 * Since: 2/15/13
 */
public class DbRow {

    List<Object> values;

    public DbRow(int capacity){
        values = new ArrayList<>(capacity);
    }

    public void addValue(Object value){
        values.add(value);
    }

    public List<Object> getValues(){
        return Collections.unmodifiableList(this.values);
    }

    public String toString(){
        return values.toString();
    }
}
