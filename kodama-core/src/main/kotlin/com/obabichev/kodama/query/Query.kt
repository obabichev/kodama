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
    val groupBy: List<Column<*>> = emptyList()
) {
    fun sql(): String {
        val tableName = from.name

        // Build SELECT clause with both columns and aggregates
        val selectItems = mutableListOf<String>()

        // Add regular columns
        selectItems.addAll(select.map { "\"${it.relation.name}\".${it.name}" })

        // Add aggregate functions
        selectItems.addAll(aggregates.map { agg ->
            val funcName = agg.functionName
            val columnRef = if (agg.column != null) {
                "\"${agg.column.relation.name}\".${agg.column.name}"
            } else {
                "*" // For COUNT(*)
            }
            "$funcName($columnRef) AS ${agg.accessorName}"
        })

        val columns = selectItems.joinToString(", ")

        val baseQuery = buildString {
            append("SELECT $columns FROM \"$tableName\"")

            // Add JOIN clauses
            joins.forEach { join ->
                val joinType = when (join.type) {
                    com.obabichev.kodama.components.JoinType.INNER -> "INNER JOIN"
                    com.obabichev.kodama.components.JoinType.LEFT -> "LEFT JOIN"
                    com.obabichev.kodama.components.JoinType.RIGHT -> "RIGHT JOIN"
                }
                val (leftColumn, rightColumn) = join.condition
                // Quote table names to handle SQL keywords and qualify column names to avoid ambiguity
                append(" $joinType \"${join.relation.name}\" ON \"${leftColumn.relation.name}\".${leftColumn.name} = \"${rightColumn.relation.name}\".${rightColumn.name}")
            }
        }

        val queryWithWhere = if (whereExpression != null) {
            "$baseQuery WHERE ${whereExpression.toSql()}"
        } else {
            baseQuery
        }

        val queryWithGroupBy = if (groupBy.isNotEmpty()) {
            val groupByClause = groupBy.joinToString(", ") { "\"${it.relation.name}\".${it.name}" }
            "$queryWithWhere GROUP BY $groupByClause"
        } else {
            queryWithWhere
        }

        return if (orderBy.isNotEmpty()) {
            val orderByClause = orderBy.joinToString(", ") { "\"${it.column.relation.name}\".${it.column.name} ${it.direction.toSql()}" }
            "$queryWithGroupBy ORDER BY $orderByClause"
        } else {
            queryWithGroupBy
        }
    }

    fun arguments(): List<QueryArgument<*>> {
        return whereExpression?.arguments() ?: emptyList()
    }
}
