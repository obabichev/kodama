package com.obabichev.kodama.query

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.components.expression.Expression
import com.obabichev.kodama.components.Join
import com.obabichev.kodama.components.Relation

class Query(
    val select: List<Column<*>>,
    val from: Relation,
    val joins: List<Join>,
    val whereExpression: Expression?,
    val orderBy: List<OrderByClause>,
    val relations: RelationsContainer,
    val aggregates: List<AggregateFunction<*>> = emptyList(),
    val groupBy: List<Column<*>> = emptyList(),
    val selectables: List<Selectable> = emptyList(),
    val limit: Int? = null,
    val offset: Int? = null
) {
    fun sql(): String {
        // Build SELECT clause with columns, aggregates, and selectables
        val selectItems = mutableListOf<String>()

        // Add regular columns
        selectItems.addAll(select.map { "\"${it.relation.name}\".\"${it.name}\"" })

        // Add aggregate functions (for backward compatibility)
        // Skip if selectables already contains aggregates (avoid duplicates)
        val hasAggregateSelectables = selectables.any { it is AggregateSelectable }
        if (!hasAggregateSelectables) {
            selectItems.addAll(aggregates.map { agg ->
                val funcName = agg.functionName
                val columnRef = if (agg.column != null) {
                    "\"${agg.column.relation.name}\".\"${agg.column.name}\""
                } else {
                    "*" // FOR COUNT(*)
                }
                "$funcName($columnRef) AS \"${agg.accessorName}\""
            })
        }

        // Add selectables (expressions, constants, subqueries, etc.)
        selectItems.addAll(selectables.map { selectable ->
            when (selectable) {
                is ColumnSelectable -> {
                    "\"${selectable.column.relation.name}\".\"${selectable.column.name}\" AS \"${selectable.sqlAlias}\""
                }
                is ExpressionSelectable -> {
                    "${selectable.expression.toSql()} AS \"${selectable.alias}\""
                }
                is AggregateSelectable -> {
                    val agg = selectable.function
                    val funcName = agg.functionName
                    val columnRef = if (agg.column != null) {
                        "\"${agg.column.relation.name}\".\"${agg.column.name}\""
                    } else {
                        "*"
                    }
                    "$funcName($columnRef) AS \"${selectable.alias}\""
                }
                is ConstantSelectable -> {
                    // Constants don't need to be in SELECT clause - they're computed client-side
                    // But if we want to support them in SQL, we could add them as literals
                    "'${selectable.value}' AS ${selectable.alias}"
                }
                is SubquerySelectable -> {
                    "(${selectable.query.sql()}) AS ${selectable.alias}"
                }
                else -> {
                    // Unknown selectable type - skip
                    ""
                }
            }
        }.filter { it.isNotEmpty() })

        val columns = selectItems.joinToString(", ")

        // Generate FROM clause - check if it's a subquery
        val fromClause = generateFromClause(from)

        val baseQuery = buildString {
            append("SELECT $columns FROM $fromClause")

            // Add JOIN clauses
            joins.forEach { join ->
                val joinType = when (join.type) {
                    com.obabichev.kodama.components.JoinType.INNER -> "INNER JOIN"
                    com.obabichev.kodama.components.JoinType.LEFT -> "LEFT JOIN"
                    com.obabichev.kodama.components.JoinType.RIGHT -> "RIGHT JOIN"
                }

                // Generate join target - check if it's a subquery
                val joinTarget = generateFromClause(join.relation)

                // Use Expression.toSql() for the join condition
                append(" $joinType $joinTarget ON ${join.condition.toSql()}")
            }
        }

        val queryWithWhere = if (whereExpression != null) {
            "$baseQuery WHERE ${whereExpression.toSql()}"
        } else {
            baseQuery
        }

        val queryWithGroupBy = if (groupBy.isNotEmpty()) {
            val groupByClause = groupBy.joinToString(", ") { "\"${it.relation.name}\".\"${it.name}\"" }
            "$queryWithWhere GROUP BY $groupByClause"
        } else {
            queryWithWhere
        }

        val queryWithOrderBy = if (orderBy.isNotEmpty()) {
            val orderByClause = orderBy.joinToString(", ") { "\"${it.column.relation.name}\".\"${it.column.name}\" ${it.direction.toSql()}" }
            "$queryWithGroupBy ORDER BY $orderByClause"
        } else {
            queryWithGroupBy
        }

        // Add LIMIT and OFFSET clauses
        val queryWithLimit = if (limit != null) {
            "$queryWithOrderBy LIMIT $limit"
        } else {
            queryWithOrderBy
        }

        return if (offset != null) {
            "$queryWithLimit OFFSET $offset"
        } else {
            queryWithLimit
        }
    }

    fun arguments(): List<QueryArgument<*>> {
        val args = mutableListOf<QueryArgument<*>>()

        // Add arguments from FROM subquery
        val fromTable = com.obabichev.kodama.schema.Tables.findByRelation(from)
        if (fromTable is SubqueryTable) {
            args.addAll(fromTable.subquery.arguments())
        }

        // Add arguments from JOIN subqueries and join conditions
        joins.forEach { join ->
            val joinTable = com.obabichev.kodama.schema.Tables.findByRelation(join.relation)
            if (joinTable is SubqueryTable) {
                args.addAll(joinTable.subquery.arguments())
            }
            // Add arguments from join condition expression
            args.addAll(join.condition.arguments())
        }

        // Add arguments from WHERE clause
        whereExpression?.arguments()?.let { args.addAll(it) }

        // Add arguments from selectables (e.g., ExpressionSelectable may have parameters)
        selectables.forEach { selectable ->
            when (selectable) {
                is ExpressionSelectable -> {
                    args.addAll(selectable.expression.arguments())
                }
                is SubquerySelectable -> {
                    args.addAll(selectable.query.arguments())
                }
                // AggregateSelectable and ConstantSelectable don't have arguments
            }
        }

        return args
    }

    /**
     * Generate FROM/JOIN clause for a relation.
     * If the relation belongs to a SubqueryTable, generates: (subquery SQL) AS alias
     * Otherwise, generates: "table_name"
     */
    private fun generateFromClause(relation: Relation): String {
        val table = com.obabichev.kodama.schema.Tables.findByRelation(relation)
        return if (table is SubqueryTable) {
            // It's a subquery - wrap the subquery SQL and use the alias
            "(${table.subquery.sql()}) AS \"${relation.name}\""
        } else {
            // Regular table - just quote the name
            "\"${relation.name}\""
        }
    }
}
