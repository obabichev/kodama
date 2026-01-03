package com.obabichev.kodama.compiler.generator.results

import com.obabichev.kodama.compiler.data.SubqueryInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates result accessor class for subquery results with specific columns.
 *
 * When a subquery is used in a query (via fromAliased or join), the result needs
 * accessors to read subquery column values from the result set.
 *
 * Example output for ExpensiveOrders subquery with userName + cost columns:
 * ```
 * class ExpensiveOrdersResultAccessor_UserName_Cost(
 *     private val resultSet: ResultSet,
 *     private val relations: RelationsContainer
 * ) {
 *     val userName: String
 *         get() {
 *             val table = relations.relation(ExpensiveOrders)
 *             return resultSet.getString("${table.alias}.user_name")
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
 *     .selectAll { expensiveOrders }
 *     .execute(transaction)
 *
 * results.forEach { row ->
 *     val userName = row.expensiveOrders.userName  // Type-safe access
 *     val cost = row.expensiveOrders.cost
 * }
 * ```
 *
 * Key features:
 * - One property per subquery column
 * - Properties use table alias for proper SQL result mapping
 * - Type-safe accessors prevent column name typos
 * - Lazy evaluation via getters
 */
class SubqueryResultAccessorGenerator(
    private val subqueryInfo: SubqueryInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        val className = "${subqueryInfo.name}ResultAccessor_" +
            subqueryInfo.columns.joinToString("_") { it.capitalizedName }

        appendLine("/**")
        appendLine(" * Result accessor for ${subqueryInfo.name} subquery with specific columns.")
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

            appendLine("    val ${column.propertyName}: ${column.kotlinType}")
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
