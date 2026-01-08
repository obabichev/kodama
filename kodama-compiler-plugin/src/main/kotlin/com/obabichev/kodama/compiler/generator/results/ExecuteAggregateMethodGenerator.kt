package com.obabichev.kodama.compiler.generator.results

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.data.MarkerCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates specialized execute() methods for queries with a specific marker combination.
 *
 * When a query uses selectAs() with marker interfaces (aggregates), this generator
 * creates an overloaded execute() method that returns SelectionResult_* for that
 * specific combination instead of QueryResult_*.
 *
 * Example output for TotalRevenue + OrderCount combination in Person+Order query:
 * ```
 * fun <PersonSel, OrderSel>
 * AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, Has2Aggregates>.execute(
 *     transaction: JdbcTransaction
 * ): List<SelectionResult_TotalRevenue_OrderCount> {
 *     val query = this.build()
 *     val resultSet = transaction.execute(query)
 *
 *     val results = mutableListOf<SelectionResult_TotalRevenue_OrderCount>()
 *     while (resultSet.next()) {
 *         val row = SelectionResult_TotalRevenue_OrderCount(
 *             totalRevenue = resultSet.getBigDecimal("totalrevenue"),
 *             orderCount = resultSet.getLong("ordercount")
 *         )
 *         results.add(row)
 *     }
 *     return results
 * }
 * ```
 *
 * Key features:
 * - Constrains AC parameter to specific aggregate count (Has1Aggregate, Has2Aggregates, etc.)
 * - Returns SelectionResult_* for the specific marker combination
 * - Maps result set columns to marker properties
 * - Type-safe access to aggregate values
 *
 * Each unique marker combination gets its own execute() method for each query combination.
 */
class ExecuteAggregateMethodGenerator(
    private val queryCombination: QueryCombinationInfo,
    private val markerCombination: MarkerCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        val selectionParams = queryCombination.tables.joinToString(", ") { "${it.capitalizedName}Sel" }
        val markers = markerCombination.markers
        val phantomType = "SelectionSet_" + markers.joinToString("_") { it.interfaceName }

        // JP type constraint to match the specific join pattern (makes this MORE specific than generic execute)
        val jpConstraint = queryCombination.joinPatternTypeName

        // Generate unique JVM name to avoid signature clashes
        val jvmName = "execute_${queryCombination.builderClassName}_${phantomType}"

        appendLine("/**")
        appendLine(" * Execute query with ${markers.size} aggregate selection(s): ${markers.joinToString(", ") { it.interfaceName }}.")
        appendLine(" */")
        appendLine("@JvmName(\"$jvmName\")")
        appendLine("fun <$selectionParams>")
        appendLine("${queryCombination.builderClassName}<$selectionParams, $phantomType, $jpConstraint>.execute(")
        appendLine("    transaction: com.obabichev.kodama.execute.JdbcTransaction")
        appendLine("): List<${markerCombination.resultClassName}> {")
        appendLine("    val query = this.build()")
        appendLine("    val resultSet = transaction.execute(query)")
        appendLine("    ")
        appendLine("    val results = mutableListOf<${markerCombination.resultClassName}>()")
        appendLine("    while (resultSet.next()) {")
        appendLine("        val row = ${markerCombination.resultClassName}(")
        markers.forEachIndexed { index, marker ->
            val comma = if (index < markers.size - 1) "," else ""
            // Use the marker's SQL alias (respects the configured naming style)
            appendLine("            ${marker.propertyName} = resultSet.getObject(\"${marker.sqlAlias}\") as ${marker.resultType}$comma")
        }
        appendLine("        )")
        appendLine("        results.add(row)")
        appendLine("    }")
        appendLine("    return results")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.execute.JdbcTransaction"
        )
    }
}
