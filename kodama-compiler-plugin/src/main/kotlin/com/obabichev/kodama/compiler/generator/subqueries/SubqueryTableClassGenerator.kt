package com.obabichev.kodama.compiler.generator.subqueries

import com.obabichev.kodama.compiler.data.SubqueryInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates SubqueryTable class implementations for subquery types.
 *
 * A SubqueryTable wraps a Query object and exposes it as a Table that can be
 * used in FROM and JOIN clauses. This enables using subqueries as if they were
 * regular tables.
 *
 * Example output for ExpensiveOrders subquery:
 * ```
 * class SubqueryTable_ExpensiveOrders(
 *     private val query: Query,
 *     override val alias: String = "expensive_orders"
 * ) : Table("subquery"), ExpensiveOrders {
 *
 *     override fun sqlName(): String = "(${query.toSQL()})"
 *
 *     val userName = varchar("user_name", 255)
 *     val cost = integer("cost")
 *
 *     override fun columns(): List<Column<*>> {
 *         return listOf(userName, cost)
 *     }
 * }
 * ```
 *
 * Key features:
 * - Implements the subquery marker interface (ExpensiveOrders)
 * - Extends Table to work with existing query infrastructure
 * - Defines columns matching the subquery's output
 * - sqlName() returns the subquery SQL wrapped in parentheses
 * - Can be used in FROM/JOIN just like regular tables
 *
 * Usage:
 * ```
 * // Create subquery table
 * val expensiveOrdersQuery = from(Order)
 *     .selectAll(Order)
 *     .where { order.cost gt 1000 }
 *     .build()
 * val subqueryTable = SubqueryTable_ExpensiveOrders(expensiveOrdersQuery)
 *
 * // Use in query
 * from(Person)
 *     .join(subqueryTable) { ... }
 * ```
 *
 * The SubqueryTable bridges the gap between Query objects and Table objects,
 * enabling SQL subquery syntax like:
 * ```sql
 * SELECT * FROM person
 * JOIN (SELECT * FROM order WHERE cost > 1000) AS expensive_orders
 *   ON person.name = expensive_orders.user_name
 * ```
 */
class SubqueryTableClassGenerator(
    private val subqueryInfo: SubqueryInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        val className = "SubqueryTable_${subqueryInfo.name}"

        appendLine("/**")
        appendLine(" * SubqueryTable for ${subqueryInfo.name}.")
        appendLine(" */")
        appendLine("class $className(")
        appendLine("    query: Query,")
        appendLine("    override val alias: String = \"${subqueryInfo.sqlAlias}\"")
        appendLine(") : SubqueryTable(alias, query), ${subqueryInfo.name} {")
        appendLine()

        // Generate column properties
        subqueryInfo.columns.forEach { column ->
            val columnType = when (column.kotlinType) {
                "String" -> "varchar(\"${column.sqlColumnName}\", 255)"
                "Int" -> "integer(\"${column.sqlColumnName}\")"
                "Long" -> "bigint(\"${column.sqlColumnName}\")"
                "Boolean" -> "boolean(\"${column.sqlColumnName}\")"
                "Double" -> "doublePrecision(\"${column.sqlColumnName}\")"
                "BigDecimal" -> "decimal(\"${column.sqlColumnName}\", 19, 4)"
                "Number" -> "bigint(\"${column.sqlColumnName}\")" // For aggregate results
                else -> "varchar(\"${column.sqlColumnName}\", 255)"
            }
            appendLine("    val ${column.propertyName} = $columnType")
        }
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.SubqueryTable",
            "com.obabichev.kodama.schema.Column",
            "com.obabichev.kodama.query.Query"
        )
    }
}
