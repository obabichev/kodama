package com.obabichev.kodama.compiler.generator.accessors

import com.obabichev.kodama.compiler.data.SubqueryInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates an accessor class for a subquery.
 *
 * Similar to TableAccessorGenerator but for subquery results.
 * Provides typed access to subquery columns.
 *
 * Example output for ExpensiveOrders subquery:
 * ```
 * class ExpensiveOrdersAccessor(
 *     private val tableAccessor: TableAccessor
 * ) {
 *     val userName: com.obabichev.kodama.components.TypedColumn<String, ExpensiveOrders, OrderUserName<String>>
 *         get() = com.obabichev.kodama.components.TypedColumn(tableAccessor.table.allColumns().find { it.name == "user_name" }!!)
 *
 *     val product: com.obabichev.kodama.components.TypedColumn<String, ExpensiveOrders, OrderProduct<String>>
 *         get() = com.obabichev.kodama.components.TypedColumn(tableAccessor.table.allColumns().find { it.name == "product" }!!)
 * }
 * ```
 */
class SubqueryAccessorGenerator(
    private val subqueryInfo: SubqueryInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("class ${subqueryInfo.accessorClassName}(")
        appendLine("    private val tableAccessor: TableAccessor")
        appendLine(") {")

        // Generate typed column properties
        subqueryInfo.columns.forEach { column ->
            val nullMarker = if (column.isNullable) "?" else ""
            val columnMarker = "${column.capitalizedName}<${column.kotlinType}$nullMarker>"
            appendLine("    val ${column.propertyName}: com.obabichev.kodama.components.TypedColumn<${column.kotlinType}$nullMarker, ${subqueryInfo.name}, $columnMarker>")
            appendLine("        get() = com.obabichev.kodama.components.TypedColumn(tableAccessor.table.allColumns().find { it.name == \"${column.sqlColumnName}\" }!! as Column<${column.kotlinType}$nullMarker>)")
            appendLine()
        }

        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.components.TypedColumn",
            "com.obabichev.kodama.components.Column",
            "com.obabichev.kodama.query.TableAccessor"
        )
    }
}
