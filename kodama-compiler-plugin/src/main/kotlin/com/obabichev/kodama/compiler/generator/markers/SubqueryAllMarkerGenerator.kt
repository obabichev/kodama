package com.obabichev.kodama.compiler.generator.markers

import com.obabichev.kodama.compiler.data.SubqueryInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates an AllMarker class for a subquery.
 *
 * AllMarker is used with selectAll() to select all columns from a subquery.
 * Extends AllColumnsMarker (generated in TableMetadata.kt) to work with the sealed class hierarchy.
 *
 * Example output for ExpensiveOrders subquery:
 * ```
 * class ExpensiveOrdersAllMarker(table: Table) : AllColumnsMarker(table)
 * ```
 */
class SubqueryAllMarkerGenerator(
    private val subqueryInfo: SubqueryInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("class ${subqueryInfo.name}AllMarker(table: Table) : AllColumnsMarker(table)")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.schema.Table"
        )
    }
}
