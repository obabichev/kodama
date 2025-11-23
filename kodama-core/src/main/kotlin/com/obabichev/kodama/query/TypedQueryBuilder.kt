package com.obabichev.kodama.query

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.components.Join
import com.obabichev.kodama.components.JoinType
import com.obabichev.kodama.components.Relation
import com.obabichev.kodama.components.expression.Expression
import com.obabichev.kodama.schema.Table

/**
 * Helper to expose all columns from a table
 * Now works directly with Table objects and their column properties
 */
class TableAccessor(
    val table: Table,
    val relationsContainer: RelationsContainer
) {
    /**
     * Get all columns from this table
     */
    fun all(): List<Column<*>> = table.allColumns()

    /**
     * Get the relation for this table
     */
    val relation: Relation
        get() = table.relation
}

/**
 * Context for building SELECT clause with type-safe column access
 */
abstract class SelectContext {
    val selectedColumns = mutableListOf<Column<*>>()

    /**
     * Add a column to the SELECT clause
     */
    operator fun Column<*>.unaryPlus() {
        selectedColumns.add(this)
    }

    /**
     * Add all columns from a list
     */
    operator fun List<Column<*>>.unaryPlus() {
        selectedColumns.addAll(this)
    }
}

/**
 * Query state accessible to generated code
 */
class QueryState {
    var _from: Relation? = null
    val _joins: MutableList<Join> = mutableListOf()
    var _select: List<Column<*>>? = null
    var whereExpression: Expression? = null
    val relations = RelationsContainer()
}

/**
 * Initial query builder - only allows from()
 * Note: from() methods are provided as extensions in test/generated code
 */
class InitialQueryBuilder {
    val state = QueryState()
}

/**
 * Base interface for all query builders after from() is called
 */
interface AfterFromQueryBuilderBase {
    val state: QueryState

    /**
     * WHERE clause - provided as extension in generated code
     * Note: where() methods are provided as extensions in test/generated code
     */

    /**
     * Build the query - requires calling select first through generated extension
     */
    fun build(): Query {
        val select = state._select ?: throw IllegalStateException("SELECT clause is required. Call select() first.")
        val from = state._from ?: throw IllegalStateException("FROM clause is required.")
        return Query(select, from, state._joins.toList(), state.whereExpression, state.relations)
    }
}

/**
 * Builder after from() is called - allows join() or build()
 * This is the generic fallback that will be used when no specific typed builder matches
 * Note: join() methods are provided as extensions in test/generated code
 */
class AfterFromQueryBuilder(
    override val state: QueryState
) : AfterFromQueryBuilderBase

/**
 * Entry point for type-safe queries
 */
fun query(): InitialQueryBuilder = InitialQueryBuilder()
