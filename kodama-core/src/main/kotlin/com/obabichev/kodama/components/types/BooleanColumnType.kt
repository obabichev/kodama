package com.obabichev.kodama.components.types

import com.obabichev.kodama.components.ColumnType
import java.sql.ResultSet
import java.sql.Types

/**
 * Column type for PostgreSQL BOOLEAN type.
 * Maps to Kotlin Boolean.
 */
object BooleanColumnType : ColumnType<Boolean> {
    override fun readValue(resultSet: ResultSet, index: Int): Boolean {
        return resultSet.getBoolean(index)
    }

    override fun getSqlType(): Int = Types.BOOLEAN
}
