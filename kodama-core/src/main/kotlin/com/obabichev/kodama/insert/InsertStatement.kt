package com.obabichev.kodama.insert

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.schema.Table

/**
 * Represents an INSERT statement with columns and values.
 *
 * Example:
 * ```kotlin
 * val insert = InsertStatement(
 *     table = Order,
 *     columns = listOf(Order.id, Order.userName, Order.product, Order.cost),
 *     values = listOf(1, "kodama", "Laptop", 1000)
 * )
 * ```
 *
 * Generates SQL: INSERT INTO "order" (id, user_name, product, cost) VALUES (?, ?, ?, ?)
 */
data class InsertStatement(
    val table: Table,
    val columns: List<Column<*>>,
    val values: List<Any?>
) {
    init {
        require(columns.size == values.size) {
            "Number of columns (${columns.size}) must match number of values (${values.size})"
        }
        require(columns.isNotEmpty()) {
            "Cannot insert with zero columns"
        }
    }

    /**
     * Generate the SQL INSERT statement.
     * Returns pair of (SQL string, list of parameter values).
     */
    fun sql(): Pair<String, List<Any?>> {
        val tableName = table.tableName
        val columnNames = columns.joinToString(", ") { it.name }
        val placeholders = columns.joinToString(", ") { "?" }

        val sql = """INSERT INTO "$tableName" ($columnNames) VALUES ($placeholders)"""
        return sql to values
    }
}
