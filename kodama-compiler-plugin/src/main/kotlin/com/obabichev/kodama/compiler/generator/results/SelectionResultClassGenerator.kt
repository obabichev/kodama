package com.obabichev.kodama.compiler.generator.results

import com.obabichev.kodama.compiler.data.MarkerCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the SelectionResult data class for a specific marker combination.
 *
 * When using selectAs() with marker interfaces, the result contains named properties
 * that match the marker names for type-safe access to computed values.
 *
 * Example output for TotalRevenue + OrderCount combination:
 * ```
 * data class SelectionResult_TotalRevenue_OrderCount(
 *     val totalRevenue: Number,
 *     val orderCount: Number
 * )
 * ```
 *
 * Usage:
 * ```
 * val results: List<SelectionResult_TotalRevenue_OrderCount> = from(Order)
 *     .selectAs(TotalRevenue) { sum(order.cost) }
 *     .selectAs(OrderCount) { count(order.id) }
 *     .execute(transaction)
 *
 * results.forEach { row ->
 *     val revenue = row.totalRevenue   // Type: Number
 *     val count = row.orderCount       // Type: Number
 *     println("Total: $$revenue from $count orders")
 * }
 * ```
 *
 * Key features:
 * - One property per selectAs() call in the combination
 * - Property names match marker interface names (camelCase)
 * - Property types match marker result types (usually Number for aggregates)
 * - Type-safe access prevents typos and wrong type assumptions
 *
 * Each unique marker combination gets its own SelectionResult class.
 */
class SelectionResultClassGenerator(
    private val combination: MarkerCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        val markers = combination.markers

        appendLine("/**")
        appendLine(" * Result class for query with selections: ${markers.joinToString(", ") { it.interfaceName }}.")
        appendLine(" */")
        appendLine("data class ${combination.resultClassName}(")
        markers.forEachIndexed { index, marker ->
            val comma = if (index < markers.size - 1) "," else ""
            appendLine("    val ${marker.propertyName}: ${marker.resultType}$comma")
        }
        appendLine(")")
    }

    override fun requiredImports(): Set<String> {
        return emptySet()
    }
}
