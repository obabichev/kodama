package com.obabichev.kodama.components.types

import com.obabichev.kodama.components.ColumnType
import java.sql.ResultSet
import java.sql.Types

/**
 * Column type for PostgreSQL REAL type.
 * Maps to Kotlin Float.
 */
object FloatColumnType : ColumnType<Float> {
    override fun readValue(resultSet: ResultSet, index: Int): Float {
        return resultSet.getFloat(index)
    }

    override fun getSqlType(): Int = Types.REAL
}
