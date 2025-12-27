package com.obabichev.kodama.schema

import com.obabichev.kodama.components.Column

/**
 * Common interface for table-like sources in SQL queries.
 *
 * Both physical tables and subqueries are table sources - they have:
 * - An alias (used in SQL)
 * - A list of selectable columns
 * - Can be used in FROM and JOIN clauses
 *
 * This unification allows treating tables and subqueries uniformly in the query builder,
 * while maintaining type safety through concrete implementations.
 *
 * Examples:
 * - Physical tables: `Person`, `Order` (defined as `object : Table`)
 * - Subqueries: `UserTotals`, `OrderCounts` (marker interfaces for subquery results)
 */
interface TableSource {
    /**
     * The alias used for this table source in SQL.
     *
     * For physical tables: typically the lowercase table name (e.g., "person", "order")
     * For subqueries: the alias specified by .aliasAs<T>() (e.g., "user_totals")
     */
    val alias: String

    /**
     * All columns available for selection from this table source.
     *
     * For physical tables: columns defined in the table definition
     * For subqueries: columns selected in the subquery
     */
    fun allColumns(): List<Column<*>>
}

/**
 * Marker interface for subquery types.
 *
 * Generated code creates objects that implement this interface to represent
 * typed subquery results. Each subquery marker interface becomes a TableSource
 * that can be used just like a physical table.
 *
 * Example generated code:
 * ```
 * object UserTotals : SubqueryType {
 *     override val alias = "user_totals"
 *     val userName: Column<String> = Column(...)
 *     val totalCost: Column<Number?> = Column(...)
 *     override fun allColumns() = listOf(userName, totalCost)
 * }
 * ```
 */
interface SubqueryType : TableSource
