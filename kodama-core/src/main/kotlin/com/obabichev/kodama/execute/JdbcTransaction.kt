package com.obabichev.kodama.execute

import com.obabichev.kodama.components.ColumnType
import com.obabichev.kodama.insert.InsertResult
import com.obabichev.kodama.insert.InsertStatement
import com.obabichev.kodama.query.Query
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class JdbcTransaction(
    private val url: String,
    private val user: String,
    private val password: String
) : Transaction {
    /**
     * The JDBC connection used by this transaction.
     * Exposed for use with EntitySession and other low-level database operations.
     */
    val connection: Connection = DriverManager.getConnection(url, user, password).apply {
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

    /**
     * Execute an UPDATE/INSERT/DELETE with parameters using a prepared statement.
     * This is safe from SQL injection and efficient.
     *
     * @param sql SQL statement with ? placeholders
     * @param params Parameters to bind to the placeholders
     * @return Number of rows affected
     */
    fun executeUpdate(sql: String, vararg params: Any?): Int {
        val preparedStatement = connection.prepareStatement(sql)
        params.forEachIndexed { index, param ->
            preparedStatement.setObject(index + 1, param)
        }
        val result = preparedStatement.executeUpdate()
        preparedStatement.close()
        return result
    }

    /**
     * Execute an INSERT statement and return the result with generated keys.
     *
     * @param insert The INSERT statement to execute
     * @return InsertResult with rows affected and any generated keys
     */
    fun executeInsert(insert: InsertStatement): InsertResult {
        val (sql, values) = insert.sql()

        // Prepare statement with RETURN_GENERATED_KEYS to capture auto-generated IDs
        val preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)

        // Set parameter values
        values.forEachIndexed { index, value ->
            val column = insert.columns[index]
            @Suppress("UNCHECKED_CAST")
            val columnType = column.type as ColumnType<Any?>
            columnType.setValue(preparedStatement, index + 1, value)
        }

        // Execute the insert
        val rowsAffected = preparedStatement.executeUpdate()

        // Retrieve generated keys (if any)
        val generatedKeys = mutableMapOf<String, Any>()
        val generatedKeysResultSet = preparedStatement.generatedKeys
        if (generatedKeysResultSet.next()) {
            // Try to map generated keys back to column names
            // Note: Some JDBC drivers return column names, others return positions
            val metaData = generatedKeysResultSet.metaData
            for (i in 1..metaData.columnCount) {
                val columnName = metaData.getColumnName(i)
                val value = generatedKeysResultSet.getObject(i)
                if (value != null) {
                    generatedKeys[columnName] = value
                }
            }
        }

        preparedStatement.close()

        return InsertResult(
            rowsAffected = rowsAffected,
            generatedKeys = generatedKeys
        )
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