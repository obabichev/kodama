package com.obabichev.kodama.components

import java.sql.ResultSet
import java.sql.Types

interface ColumnType<T> {
    fun readValue(resultSet: ResultSet, index: Int): T

    /**
     * Sets a value in a PreparedStatement. Accepts nullable values to support
     * columns marked as nullable.
     */
    fun setValue(preparedStatement: java.sql.PreparedStatement, index: Int, value: Any?) {
        if (value == null) {
            // For null values, use setNull with appropriate SQL type
            preparedStatement.setNull(index, getSqlType())
        } else {
            preparedStatement.setObject(index, value)
        }
    }

    /**
     * Returns the SQL type constant from java.sql.Types for this column type.
     * Used when setting NULL values in prepared statements.
     * Override this if the default (OTHER) is not appropriate.
     */
    fun getSqlType(): Int = Types.OTHER
}