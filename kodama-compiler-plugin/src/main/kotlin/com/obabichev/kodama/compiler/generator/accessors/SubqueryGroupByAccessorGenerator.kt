package com.obabichev.kodama.compiler.generator.accessors

import com.obabichev.kodama.compiler.data.SubqueryInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates a GroupBy accessor class for a subquery.
 *
 * Provides typed access to subquery columns for GROUP BY clauses.
 * Returns raw columns directly (not wrapped), matching the table pattern.
 *
 * Example output for ExpensiveOrders subquery:
 * ```
 * class ExpensiveOrdersGroupByAccessor(
 *     private val tableAccessor: TableAccessor
 * ) {
 *     val userName: Column<String>
 *         get() = tableAccessor.table.allColumns().find { it.name == "user_name" }!! as Column<String>
 *
 *     val product: Column<String>
 *         get() = tableAccessor.table.allColumns().find { it.name == "product" }!! as Column<String>
 * }
 * ```
 */
class SubqueryGroupByAccessorGenerator(
    private val subqueryInfo: SubqueryInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("class ${subqueryInfo.name}GroupByAccessor(")
        appendLine("    private val tableAccessor: TableAccessor")
        appendLine(") {")

        // Generate column properties - return raw columns (not wrapped)
        subqueryInfo.columns.forEach { column ->
            val nullMarker = if (column.isNullable) "?" else ""
            appendLine("    val ${column.propertyName}: Column<${column.kotlinType}$nullMarker>")
            appendLine("        get() = tableAccessor.table.allColumns().find { it.name == \"${column.sqlColumnName}\" }!! as Column<${column.kotlinType}$nullMarker>")
            appendLine()
        }

        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.components.Column",
            "com.obabichev.kodama.query.TableAccessor"
        )
    }
}
