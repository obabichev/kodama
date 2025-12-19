package com.obabichev.kodama.components.types

import com.obabichev.kodama.components.ColumnType
import java.sql.ResultSet
import java.sql.Types

/**
 * Column type for PostgreSQL DOUBLE PRECISION type.
 * Maps to Kotlin Double.
 */
object DoubleColumnType : ColumnType<Double> {
    override fun readValue(resultSet: ResultSet, index: Int): Double {
        return resultSet.getDouble(index)
    }

    override fun getSqlType(): Int = Types.DOUBLE
}
