package com.obabichev.kodama.compiler.generator.markers

import com.obabichev.kodama.compiler.data.MarkerInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates a selection marker object.
 *
 * Selection markers enable type-safe named selections in queries:
 * ```
 * .selectAs(TotalRevenue) { sum(order.cost) }
 * ```
 *
 * Example output:
 * ```
 * /**
 *  * Marker object for marker-based selections
 *  * Use as parameter: .selectAs(TotalRevenue) { expr }
 *  */
 * object TotalRevenue
 * ```
 *
 * The marker object is used as a compile-time type witness for selection tracking.
 */
class SelectionMarkerGenerator(
    private val markerInfo: MarkerInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Marker object for marker-based selections")
        appendLine(" * Use as parameter: .selectAs(${markerInfo.interfaceName}) { expr }")
        appendLine(" */")
        appendLine("object ${markerInfo.interfaceName}")
    }

    override fun requiredImports(): Set<String> {
        // No imports needed for marker interfaces
        return emptySet()
    }
}
