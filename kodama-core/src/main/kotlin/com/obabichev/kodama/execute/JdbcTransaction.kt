package com.obabichev.kodama.execute

import com.obabichev.kodama.components.ColumnType
import com.obabichev.kodama.query.Query
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class JdbcTransaction(
    private val url: String,
    private val user: String,
    private val password: String
) : Transaction {
    private val connection: Connection = DriverManager.getConnection(url, user, password).apply {
        autoCommit = false
    }

    override fun execute(sql: Query): ResultSet {
        val sqlString = sql.sql()
        val arguments = sql.arguments()

        val preparedStatement = connection.prepareStatement(sqlString)

        // Set parameters from arguments
        arguments.forEachIndexed { index, argument ->
            @Suppress("UNCHECKED_CAST")
            val columnType = argument.columnType as ColumnType<Any?>
            columnType.setValue(preparedStatement, index + 1, argument.value)
        }

        return preparedStatement.executeQuery()
    }

    // Note: The old execute(query, klass) method has been removed.
    // Use the new type-safe execute() method on query builders instead.
    // Example: queryBuilder.execute(transaction).forEach { row -> ... }

    fun executeUpdate(sql: String): Int {
        val statement = connection.createStatement()
        return statement.executeUpdate(sql)
    }

    override fun rollback() {
        connection.rollback()
    }

    override fun commit() {
        connection.commit()
    }

    fun close() {
        connection.close()
    }
}