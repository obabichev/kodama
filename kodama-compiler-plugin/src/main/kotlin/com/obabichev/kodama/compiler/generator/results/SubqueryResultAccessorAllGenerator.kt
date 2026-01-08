package com.obabichev.kodama.compiler.generator.results

import com.obabichev.kodama.compiler.data.SubqueryInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates result accessor class for subquery results with ALL columns selected.
 *
 * This is the _All variant used when selectAll() is called on a subquery.
 * It provides access to all columns from the subquery.
 *
 * Example output for ExpensiveOrders subquery:
 * ```
 * class ExpensiveOrdersResultAccessor_All(
 *     private val resultSet: ResultSet,
 *     private val relations: RelationsContainer
 * ) {
 *     val id: Int
 *         get() {
 *             val table = relations.relation(ExpensiveOrders)
 *             return resultSet.getInt("${table.alias}.id")
 *         }
 *
 *     val userName: String
 *         get() {
 *             val table = relations.relation(ExpensiveOrders)
 *             return resultSet.getString("${table.alias}.user_name")
 *         }
 *
 *     val product: String
 *         get() {
 *             val table = relations.relation(ExpensiveOrders)
 *             return resultSet.getString("${table.alias}.product")
 *         }
 *
 *     val cost: Int
 *         get() {
 *             val table = relations.relation(ExpensiveOrders)
 *             return resultSet.getInt("${table.alias}.cost")
 *         }
 * }
 * ```
 *
 * Usage:
 * ```
 * val results = fromAliased(ExpensiveOrders)
 *     .selectAll { expensiveOrders }  // Selects ALL columns
 *     .execute(transaction)
 *
 * results.forEach { row ->
 *     // All subquery columns available
 *     val id = row.expensiveOrders.id
 *     val userName = row.expensiveOrders.userName
 *     val product = row.expensiveOrders.product
 *     val cost = row.expensiveOrders.cost
 * }
 * ```
 *
 * This variant is generated for AllColumnsSelected selection state.
 */
class SubqueryResultAccessorAllGenerator(
    private val subqueryInfo: SubqueryInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        val className = "${subqueryInfo.name}ResultAccessor_All"

        appendLine("/**")
        appendLine(" * Result accessor for ${subqueryInfo.name} subquery with all columns.")
        appendLine(" */")
        appendLine("class $className(")
        appendLine("    private val resultSet: ResultSet,")
        appendLine("    private val relations: RelationsContainer")
        appendLine(") {")

        subqueryInfo.columns.forEach { column ->
            val getterMethod = when (column.kotlinType) {
                "String" -> "getString"
                "Int" -> "getInt"
                "Long" -> "getLong"
                "Boolean" -> "getBoolean"
                "Double" -> "getDouble"
                "BigDecimal" -> "getBigDecimal"
                else -> "getObject"
            }

            // All subquery properties are nullable to support LEFT/RIGHT/FULL OUTER JOINs
            appendLine("    val ${column.propertyName}: ${column.kotlinType}?")
            appendLine("        get() {")
            appendLine("            return resultSet.$getterMethod(\"${column.sqlColumnName}\")")
            appendLine("        }")
            appendLine()
        }

        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "java.sql.ResultSet",
            "com.obabichev.kodama.query.RelationsContainer"
        )
    }
}
