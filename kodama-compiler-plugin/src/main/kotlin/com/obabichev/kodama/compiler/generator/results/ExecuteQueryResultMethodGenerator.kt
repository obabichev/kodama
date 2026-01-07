package com.obabichev.kodama.compiler.generator.results

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates specialized execute() method for queries that select all columns.
 *
 * This overload is used when all tables have AllColumnsSelected and there are
 * no aggregates (NoAggregates). It returns QueryResult_* with full table access.
 *
 * Example output for Person+Order combination:
 * ```
 * fun AfterFromQueryBuilder_Person_Order<AllColumnsSelected, AllColumnsSelected, NoAggregates>.execute(
 *     transaction: Transaction
 * ): List<QueryResult_Person_Order> {
 *     val query = this.build()
 *     val sql = query.toSQL()
 *     val resultSet = transaction.executeQuery(sql)
 *
 *     val results = mutableListOf<QueryResult_Person_Order>()
 *     while (resultSet.next()) {
 *         val row = QueryResult_Person_Order(
 *             person = PersonResultAccessor_All(resultSet, state.relations),
 *             order = OrderResultAccessor_All(resultSet, state.relations)
 *         )
 *         results.add(row)
 *     }
 *     return results
 * }
 * ```
 *
 * Type constraints:
 * - All table selection parameters must be AllColumnsSelected
 * - Aggregate count must be NoAggregates
 * - This is the most specific execute() overload
 *
 * Usage:
 * ```
 * val results = from(Person)
 *     .join(Order) { order.userName eq person.name }
 *     .selectAll(Person)
 *     .selectAll(Order)
 *     .execute(transaction)  // Returns List<QueryResult_Person_Order>
 *
 * results.forEach { row ->
 *     println("${row.person.name} ordered ${row.order.product}")
 * }
 * ```
 */
class ExecuteQueryResultMethodGenerator(
    private val combination: QueryCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        val allColumnsSelected = combination.tables.joinToString(", ") { "AllColumnsSelected" }

        // Include join pattern in result class name for pattern-specific classes
        val resultClassName = if (combination.joinPattern.isEmpty()) {
            "QueryResult_" + combination.tables.joinToString("_") { it.capitalizedName }
        } else {
            "QueryResult_" + combination.tables.joinToString("_") { it.capitalizedName } + "_" + combination.joinPattern
        }

        // JP type constraint to match the specific join pattern
        val jpConstraint = combination.joinPatternTypeName

        // Generate unique JVM name to avoid signature clashes
        val jvmName = "execute_${combination.builderClassName}_AllColumns_${combination.joinPattern.replace("_", "")}"

        appendLine("/**")
        appendLine(" * Execute query with all columns selected (no aggregates) (join pattern: ${combination.joinPattern.ifEmpty { "NONE" }}).")
        appendLine(" */")
        appendLine("@JvmName(\"$jvmName\")")
        appendLine("fun ${combination.builderClassName}<$allColumnsSelected, NoAggregates, $jpConstraint>.execute(")
        appendLine("    transaction: com.obabichev.kodama.execute.JdbcTransaction")
        appendLine("): com.obabichev.kodama.query.QueryResultIterable<$resultClassName> {")
        appendLine("    val query = this.build()")
        appendLine("    val resultSet = transaction.execute(query)")
        appendLine("    return com.obabichev.kodama.query.QueryResultIterable(resultSet, query.relations) { rs, relations ->")
        appendLine("        $resultClassName(")
        appendLine("            resultSet = rs,")
        appendLine("            relations = relations,")
        appendLine("            selectedColumns = query.select")
        appendLine("        )")
        appendLine("    }")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.QueryResultIterable",
            "com.obabichev.kodama.execute.JdbcTransaction",
            "com.obabichev.kodama.query.NoAggregates",
            "com.obabichev.kodama.query.AllColumnsSelected"
        )
    }
}
