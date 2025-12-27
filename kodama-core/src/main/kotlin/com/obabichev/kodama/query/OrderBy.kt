package com.obabichev.kodama.query

import com.obabichev.kodama.components.Column

/**
 * Represents a column with ordering direction
 */
data class OrderByClause(
    val column: Column<*>,
    val direction: OrderDirection
)

/**
 * ORDER BY direction
 */
enum class OrderDirection {
    ASC,
    DESC;

    fun toSql(): String = name
}

/**
 * Extension receiver for columns in ORDER BY context
 * Provides asc() and desc() methods
 */
class OrderByColumn<T>(private val column: Column<T>) {
    fun asc(): OrderByClause = OrderByClause(column, OrderDirection.ASC)
    fun desc(): OrderByClause = OrderByClause(column, OrderDirection.DESC)
}

/**
 * Base context for ORDER BY clause
 * Subclasses will provide type-safe table accessors
 *
 * Usage:
 * ```
 * .orderBy { person.name.asc() }
 * .orderBy { person.age.desc() }
 * ```
 *
 * Each orderBy call returns exactly one OrderByClause and can be chained.
 */
abstract class OrderByContext
