package com.obabichev.kodama.components.types

import com.obabichev.kodama.components.ColumnType
import java.sql.ResultSet
import java.sql.Types

object StringColumnType : ColumnType<String> {
    override fun readValue(resultSet: ResultSet, index: Int): String {
        return resultSet.getString(index)
    }

    override fun getSqlType(): Int = Types.VARCHAR
}