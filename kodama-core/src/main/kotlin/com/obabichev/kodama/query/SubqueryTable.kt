package com.obabichev.kodama.query

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.components.ColumnType
import com.obabichev.kodama.components.Relation
import com.obabichev.kodama.schema.Table
import kotlin.reflect.KClass

/**
 * Represents a subquery that can be used as a table in FROM or JOIN clauses.
 *
 * Subqueries are treated as derived tables with known column schemas.
 * The alias is used as the table name in the generated SQL.
 *
 * Example:
 * ```kotlin
 * val expensiveOrders = subquery_ExpensiveOrders {
 *     query()
 *         .from(Order)
 *         .select { order.userName }
 *         .select_totalCost { sum(order.cost) }
 *         .where { order.cost gt 100 }
 *         .build()
 * }
 *
 * query()
 *     .from(expensiveOrders)
 *     .join(Person) { person.name eq expensiveOrders.userName }
 *     .select { expensiveOrders.userName }
 *     .select { expensiveOrders.totalCost }
 * ```
 */
open class SubqueryTable(
    alias: String,
    val subquery: Query,
    val markerClass: KClass<*>? = null
) : Table(alias) {

    /**
     * Returns true to indicate this is a subquery, not a regular table.
     * Used by SQL generation to wrap the query in parentheses.
     */
    fun isSubquery(): Boolean = true
}

/**
 * Represents a column from a subquery result.
 *
 * Subquery columns are created based on the columns selected in the subquery:
 * - Regular columns from select { column }
 * - Named aggregates from select_name { aggregate }
 *
 * The column name matches the accessor name that will be used in the result.
 */
class SubqueryColumn<T>(
    name: String,
    relation: Relation,
    type: ColumnType<T>,
    nullable: Boolean = false
) : Column<T>(name, relation, type, nullable)
