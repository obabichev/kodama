package com.obabichev.kodama.components

import java.sql.ResultSet

interface ColumnType<T> {
    fun readValue(resultSet: ResultSet, index: Int): T

    fun setValue(preparedStatement: java.sql.PreparedStatement, index: Int, value: T) =
        preparedStatement.setObject(index, value)
}