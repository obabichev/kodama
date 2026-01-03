package com.obabichev.kodama.compiler.generator.results

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the QueryResult data class for query execution results.
 *
 * The QueryResult class encapsulates the results of a query, providing
 * type-safe access to each table's data through result accessors.
 *
 * Example output for Person+Order combination:
 * ```
 * data class QueryResult_Person_Order(
 *     val person: PersonResultAccessor_All,
 *     val order: OrderResultAccessor_All
 * )
 * ```
 *
 * Usage:
 * ```
 * val results: List<QueryResult_Person_Order> = from(Person)
 *     .join(Order) { order.userName eq person.name }
 *     .selectAll(Person)
 *     .selectAll(Order)
 *     .execute(transaction)
 *
 * results.forEach { row ->
 *     val name = row.person.name      // Type-safe access
 *     val product = row.order.product
 *     println("$name ordered $product")
 * }
 * ```
 *
 * Key features:
 * - One property per table in the query
 * - Each property is a result accessor (provides typed column access)
 * - Result accessor type varies based on selection state:
 *   - AllColumnsSelected → ResultAccessor_All
 *   - Specific columns → ResultAccessor_Name_Age
 *   - Aggregates → SelectionResult_*
 *
 * This generator creates the base result class. The actual accessor type
 * used depends on the selection state at compile time.
 */
class QueryResultClassGenerator(
    private val combination: QueryCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        val className = "QueryResult_" + combination.tables.joinToString("_") { it.capitalizedName }

        appendLine("/**")
        appendLine(" * Result class for ${combination.tables.joinToString(" + ") { it.capitalizedName }} query.")
        appendLine(" */")
        appendLine("data class $className(")
        appendLine("    override val resultSet: ResultSet,")
        appendLine("    override val relations: RelationsContainer,")
        appendLine("    override val selectedColumns: List<Column<*>>")
        appendLine(") : com.obabichev.kodama.query.QueryResult {")
        combination.tables.forEach { table ->
            appendLine("    val ${table.camelCaseName}: ${table.capitalizedName}ResultAccessor_All")

            // Different constructor args for subqueries vs regular tables
            if (table.isSubquery) {
                // Subquery result accessors only take resultSet and relations
                appendLine("        get() = ${table.capitalizedName}ResultAccessor_All(resultSet, relations)")
            } else {
                // Regular table result accessors take resultSet, relations, and selectedColumns
                appendLine("        get() = ${table.capitalizedName}ResultAccessor_All(resultSet, relations, selectedColumns)")
            }
        }
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "java.sql.ResultSet",
            "com.obabichev.kodama.query.RelationsContainer",
            "com.obabichev.kodama.components.Column",
            "com.obabichev.kodama.query.QueryResult"
        )
    }
}
