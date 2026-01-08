package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the .execute() extension method for executing queries and returning results.
 *
 * The execute() method builds the SQL query, executes it against the database,
 * and returns type-safe result objects.
 *
 * Example output for Person+Order combination:
 * ```
 * fun <PersonSel, OrderSel, AC : AggCount>
 * AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC>.execute(
 *     transaction: Transaction
 * ): List<QueryResult_Person_Order> {
 *     val query = this.build()
 *     val sql = query.toSQL()
 *     val resultSet = transaction.executeQuery(sql)
 *
 *     val results = mutableListOf<QueryResult_Person_Order>()
 *     while (resultSet.next()) {
 *         val accessor = QueryResult_Person_Order(
 *             person = PersonResultAccessor_All(resultSet, state.relations.relation(Person)),
 *             order = OrderResultAccessor_All(resultSet, state.relations.relation(Order))
 *         )
 *         results.add(accessor)
 *     }
 *     return results
 * }
 * ```
 *
 * Type safety:
 * - Result type varies based on selection state (AllColumnsSelected, specific columns, aggregates)
 * - The generic type parameters determine which result accessor is used
 * - Each table's selection state maps to the corresponding result accessor variant
 *
 * Usage:
 * ```
 * withConnection { transaction ->
 *     val results = from(Person)
 *         .join(Order) { order.userName eq person.name }
 *         .selectAll(Person)
 *         .selectAll(Order)
 *         .execute(transaction)
 *
 *     results.forEach { row ->
 *         println("${row.person.name} ordered ${row.order.product}")
 *     }
 * }
 * ```
 *
 * Note: This is a simplified version. The actual implementation needs to handle:
 * - Different selection patterns (all columns, specific columns, aggregates)
 * - Proper result accessor selection based on generic types
 * - NULL handling for outer joins
 */
class ExecuteMethodGenerator(
    private val combination: QueryCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Build type parameters
        val selectionParams = combination.tables.joinToString(", ") { "${it.capitalizedName}Sel" }
        val allParams = "$selectionParams, AC : AggCount"

        // Include join pattern in result class name for pattern-specific classes
        val resultClassName = if (combination.joinPattern.isEmpty()) {
            "QueryResult_" + combination.tables.joinToString("_") { it.capitalizedName }
        } else {
            "QueryResult_" + combination.tables.joinToString("_") { it.capitalizedName } + "_" + combination.joinPattern
        }

        // JP type constraint to match the specific join pattern
        val jpConstraint = combination.joinPatternTypeName

        // Generate unique JVM name to avoid signature clashes
        val jvmName = "execute_${combination.builderClassName}_Generic_${combination.joinPattern.replace("_", "")}"

        appendLine("/**")
        appendLine(" * Execute the query and return results (join pattern: ${combination.joinPattern.ifEmpty { "NONE" }}).")
        appendLine(" */")
        appendLine("@JvmName(\"$jvmName\")")
        appendLine("fun <$allParams>")
        appendLine("${combination.builderClassName}<$selectionParams, AC, $jpConstraint>.execute(")
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
            "com.obabichev.kodama.query.AggCount",
            "com.obabichev.kodama.query.QueryResultIterable",
            "com.obabichev.kodama.execute.JdbcTransaction"
        )
    }
}
