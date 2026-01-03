package com.obabichev.kodama.compiler.generator.markers

import com.obabichev.kodama.compiler.data.MarkerInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates a selection marker interface with companion object.
 *
 * Selection markers enable type-safe named selections in queries:
 * ```
 * .selectAs(TotalRevenue) { sum(order.cost) }
 * ```
 *
 * Example output:
 * ```
 * /**
 *  * Marker interface for marker-based selections
 *  * Use companion object as parameter: .selectAs(TotalRevenue) { expr }
 *  */
 * interface TotalRevenue<out T> {
 *     companion object : TotalRevenue<Number>
 * }
 * ```
 *
 * The companion object allows the marker to be used directly as a parameter.
 */
class SelectionMarkerGenerator(
    private val markerInfo: MarkerInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Marker interface for marker-based selections")
        appendLine(" * Use companion object as parameter: .selectAs(${markerInfo.interfaceName}) { expr }")
        appendLine(" */")
        appendLine("interface ${markerInfo.interfaceName}<out T> {")
        appendLine("    companion object : ${markerInfo.interfaceName}<${markerInfo.resultType}>")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        // No imports needed for marker interfaces
        return emptySet()
    }
}
