package com.obabichev.kodama.compiler.generator.markers

import com.obabichev.kodama.compiler.data.SubqueryInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates a subquery marker interface with companion object.
 *
 * Subquery markers enable type-safe subquery definitions:
 * ```
 * fromAliased(ExpensiveOrders) { ... }
 * ```
 *
 * Example output:
 * ```
 * /**
 *  * Marker interface and companion for subquery: ExpensiveOrders
 *  * Use in queries: fromAliased(ExpensiveOrders) { ... }
 *  */
 * interface ExpensiveOrders : com.obabichev.kodama.schema.SubqueryType {
 *     companion object : ExpensiveOrders
 * }
 * ```
 */
class SubqueryMarkerGenerator(
    private val subqueryInfo: SubqueryInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Marker interface and companion for subquery: ${subqueryInfo.name}")
        appendLine(" * Use in queries: fromAliased(${subqueryInfo.name}) { ... }")
        appendLine(" */")
        appendLine("interface ${subqueryInfo.name} : com.obabichev.kodama.schema.SubqueryType {")
        appendLine("    companion object : ${subqueryInfo.name} {")
        appendLine("        override val alias: String = \"${subqueryInfo.sqlAlias}\"")
        appendLine("        override fun allColumns(): List<Column<*>> {")
        appendLine("            // Delegate to the actual subquery table from registry")
        appendLine("            val table = SubqueryRegistry.getOrCreate<${subqueryInfo.name}>()")
        appendLine("            return table.allColumns()")
        appendLine("        }")
        appendLine("    }")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.schema.SubqueryType",
            "com.obabichev.kodama.schema.Column",
            "com.obabichev.kodama.query.SubqueryRegistry"
        )
    }
}
