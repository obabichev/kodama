package com.obabichev.kodama.compiler.generator.builders

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the build() method for a query builder.
 *
 * The build() method converts the builder's internal state into an immutable Query object
 * that can be executed or used as a subquery.
 *
 * Example output for Person + Order combination:
 * ```
 * override fun build(): Query {
 *     return Query(
 *         from = state.from,
 *         joins = state._joins.toList(),
 *         where = state._where.firstOrNull(),
 *         orderBy = state._orderBy.toList(),
 *         groupBy = state._groupBy.toList(),
 *         limit = state._limit,
 *         offset = state._offset,
 *         selectExpressions = state._selectExpressions.toList()
 *     )
 * }
 * ```
 *
 * This method is essential for:
 * - Converting builders to queries for execution
 * - Using queries as subqueries in other queries
 * - Testing query construction without execution
 */
class QueryBuilderBuildMethodGenerator(
    private val combination: QueryCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Build generic type parameters
        val selectionTypeParams = combination.tables.joinToString(", ") {
            "${it.capitalizedName}Sel"
        }
        val allTypeParams = "$selectionTypeParams, AC : AggCount, JP : JoinPattern"

        appendLine("fun <$allTypeParams> ${combination.builderClassName}<$selectionTypeParams, AC, JP>.build(): Query {")
        appendLine("    if (state._selectedColumns.isEmpty() && state._aggregateSelections.isEmpty() && state._selectables.isEmpty()) {")
        appendLine("        error(\"No columns selected. Use .selectAs() or .selectAll() to specify columns to retrieve.\")")
        appendLine("    }")
        appendLine("    val from = state._from ?: error(\"FROM clause is required.\")")
        appendLine()
        appendLine("    // When mixing columns with aggregates, automatically add selected columns to GROUP BY")
        appendLine("    val groupBy = if (state._aggregateSelections.isNotEmpty() && state._selectedColumns.isNotEmpty()) {")
        appendLine("        state._selectedColumns.toList()")
        appendLine("    } else {")
        appendLine("        state._groupBy.toList()")
        appendLine("    }")
        appendLine()
        appendLine("    return Query(")
        appendLine("        state._selectedColumns.toList(),")
        appendLine("        from,")
        appendLine("        state._joins.toList(),")
        appendLine("        state.whereExpression,")
        appendLine("        state._orderBy.toList(),")
        appendLine("        state.relations,")
        appendLine("        state._aggregateSelections.toList(),")
        appendLine("        groupBy,")
        appendLine("        state._selectables.toList(),")
        appendLine("        state._limit,")
        appendLine("        state._offset")
        appendLine("    )")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.Query",
            "com.obabichev.kodama.query.AggCount"
        )
    }
}
