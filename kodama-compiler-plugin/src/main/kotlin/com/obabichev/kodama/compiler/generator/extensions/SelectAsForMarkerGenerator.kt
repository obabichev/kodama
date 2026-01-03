package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.data.MarkerCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates .selectAs() method for a specific marker combination.
 *
 * This generator creates type-safe selectAs() methods that track which specific
 * markers are selected using phantom types, replacing the aggregate count approach.
 *
 * Example: For a 2-marker combination [TotalRevenue, OrderCount], generates:
 * ```
 * // First marker: NoAggregates → SelectionSet_TotalRevenue
 * inline fun <PersonSel>
 * AfterFromQueryBuilder_Person<PersonSel, NoAggregates>.selectAs(
 *     marker: TotalRevenue,
 *     crossinline expression: SelectContext_Person.() -> Expression
 * ): AfterFromQueryBuilder_Person<PersonSel, SelectionSet_TotalRevenue> {
 *     val context = SelectContext_Person(state)
 *     val expr = context.expression()
 *     val alias = "totalRevenue"
 *     state._selectables.add(ExpressionSelectable(alias, expr))
 *     return AfterFromQueryBuilder_Person(state)
 * }
 *
 * // Second marker: SelectionSet_TotalRevenue → SelectionSet_TotalRevenue_OrderCount
 * inline fun <PersonSel>
 * AfterFromQueryBuilder_Person<PersonSel, SelectionSet_TotalRevenue>.selectAs(
 *     marker: OrderCount,
 *     crossinline expression: SelectContext_Person.() -> Expression
 * ): AfterFromQueryBuilder_Person<PersonSel, SelectionSet_TotalRevenue_OrderCount> {
 *     val context = SelectContext_Person(state)
 *     val expr = context.expression()
 *     val alias = "orderCount"
 *     state._selectables.add(ExpressionSelectable(alias, expr))
 *     return AfterFromQueryBuilder_Person(state)
 * }
 * ```
 *
 * Type progression:
 * - NoAggregates → SelectionSet_{FirstMarker}
 * - SelectionSet_{M1} → SelectionSet_{M1}_{M2}
 * - SelectionSet_{M1}_{M2} → SelectionSet_{M1}_{M2}_{M3}
 * - etc.
 */
class SelectAsForMarkerGenerator(
    private val queryCombination: QueryCombinationInfo,
    private val markerCombination: MarkerCombinationInfo,
    private val markerIndex: Int  // Which marker in the combination (0 = first, 1 = second, etc.)
) : CodeGenerator {

    override fun generate(): String = buildString {
        val marker = markerCombination.markers[markerIndex]
        val selectionParams = queryCombination.tables.joinToString(", ") { "${it.capitalizedName}Sel" }
        val contextClassName = "SelectContext_" + queryCombination.tables.joinToString("_") { it.capitalizedName }

        // Determine from/to types based on position
        val fromType = if (markerIndex == 0) {
            "NoSelections"
        } else {
            // SelectionSet of all previous markers
            "SelectionSet_" + markerCombination.markers.take(markerIndex).joinToString("_") { it.interfaceName }
        }

        val toType = "SelectionSet_" + markerCombination.markers.take(markerIndex + 1).joinToString("_") { it.interfaceName }

        // Generate unique JVM name to avoid signature clashes
        val jvmName = "selectAs_${queryCombination.builderClassName}_${fromType}_${marker.interfaceName}"

        appendLine("/**")
        appendLine(" * Select ${marker.interfaceName} (marker ${markerIndex + 1} of ${markerCombination.markers.size}).")
        appendLine(" * Type transition: $fromType → $toType")
        appendLine(" */")
        appendLine("@JvmName(\"$jvmName\")")
        appendLine("inline fun <$selectionParams>")
        appendLine("${queryCombination.builderClassName}<$selectionParams, $fromType>.selectAs(")
        appendLine("    marker: ${marker.interfaceName}<*>,")
        appendLine("    crossinline selection: $contextClassName.() -> Any")
        appendLine("): ${queryCombination.builderClassName}<$selectionParams, $toType> {")
        appendLine("    val context = $contextClassName(state)")
        appendLine("    val result = context.selection()")
        appendLine("    val alias = \"${marker.sqlAlias}\"")
        appendLine("    ")
        appendLine("    // Handle different selection types")
        appendLine("    when (result) {")
        appendLine("        is TypedColumn<*, *, *> -> {")
        appendLine("            state._selectables.add(ColumnSelectable(alias, result.column))")
        appendLine("        }")
        appendLine("        is AggregateFunction<*> -> {")
        appendLine("            state._selectables.add(AggregateSelectable(alias, result))")
        appendLine("        }")
        appendLine("        is Expression -> {")
        appendLine("            state._selectables.add(ExpressionSelectable(alias, result))")
        appendLine("        }")
        appendLine("        else -> throw IllegalArgumentException(\"Invalid selection type: \${result::class}\")")
        appendLine("    }")
        appendLine("    ")
        appendLine("    return ${queryCombination.builderClassName}(state)")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        val imports = mutableSetOf(
            "com.obabichev.kodama.query.ExpressionSelectable",
            "com.obabichev.kodama.query.ColumnSelectable",
            "com.obabichev.kodama.query.AggregateSelectable",
            "com.obabichev.kodama.query.AggregateFunction",
            "com.obabichev.kodama.components.expression.Expression",
            "com.obabichev.kodama.components.TypedColumn"
        )

        if (markerIndex == 0) {
            imports.add("com.obabichev.kodama.query.NoSelections")
        }

        return imports
    }
}
