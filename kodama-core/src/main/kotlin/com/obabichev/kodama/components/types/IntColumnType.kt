package com.obabichev.kodama.components.types

import com.obabichev.kodama.components.ColumnType
import java.sql.ResultSet

object IntColumnType : ColumnType<Int> {
    override fun readValue(resultSet: ResultSet, index: Int): Int {
        return resultSet.getInt(index)
    }
}