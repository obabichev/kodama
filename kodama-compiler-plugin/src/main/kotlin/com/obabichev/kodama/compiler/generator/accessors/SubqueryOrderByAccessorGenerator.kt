package com.obabichev.kodama.compiler.generator.accessors

import com.obabichev.kodama.compiler.data.SubqueryInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates an OrderBy accessor class for a subquery.
 *
 * Provides typed access to subquery columns for ORDER BY clauses.
 *
 * Example output for ExpensiveOrders subquery:
 * ```
 * class ExpensiveOrdersOrderByAccessor(
 *     private val tableAccessor: TableAccessor
 * ) {
 *     val userName: com.obabichev.kodama.query.OrderByColumn<String>
 *         get() = com.obabichev.kodama.query.OrderByColumn(tableAccessor.table.allColumns().find { it.name == "user_name" }!! as Column<String>)
 *
 *     val product: com.obabichev.kodama.query.OrderByColumn<String>
 *         get() = com.obabichev.kodama.query.OrderByColumn(tableAccessor.table.allColumns().find { it.name == "product" }!! as Column<String>)
 * }
 * ```
 */
class SubqueryOrderByAccessorGenerator(
    private val subqueryInfo: SubqueryInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("class ${subqueryInfo.name}OrderByAccessor(")
        appendLine("    private val tableAccessor: TableAccessor")
        appendLine(") {")

        // Generate OrderByColumn properties
        subqueryInfo.columns.forEach { column ->
            val nullMarker = if (column.isNullable) "?" else ""
            appendLine("    val ${column.propertyName}: com.obabichev.kodama.query.OrderByColumn<${column.kotlinType}$nullMarker>")
            appendLine("        get() = com.obabichev.kodama.query.OrderByColumn(tableAccessor.table.allColumns().find { it.name == \"${column.sqlColumnName}\" }!! as Column<${column.kotlinType}$nullMarker>)")
            appendLine()
        }

        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.OrderByColumn",
            "com.obabichev.kodama.components.Column",
            "com.obabichev.kodama.query.TableAccessor"
        )
    }
}
