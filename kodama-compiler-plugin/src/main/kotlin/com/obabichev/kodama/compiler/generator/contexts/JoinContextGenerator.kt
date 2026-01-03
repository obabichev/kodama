package com.obabichev.kodama.compiler.generator.contexts

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.data.TableInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates a JoinContext class for join operations.
 *
 * JoinContext provides typed access to all tables involved in a join,
 * including the table being joined.
 *
 * Example output for Person joining Order:
 * ```
 * class JoinContext_Person_Order(
 *     private val state: com.obabichev.kodama.query.QueryState,
 *     table: com.example.Order
 * ) {
 *     val person: PersonAccessor
 *         get() = PersonAccessor(state.relations.tableAccessor(com.example.Person))
 *
 *     val order: OrderAccessor
 *         get() = OrderAccessor(state.relations.tableAccessor(table))
 * }
 * ```
 *
 * Usage in queries:
 * ```
 * .join(Order) { order.userName eq person.name }
 * //             ^^^^^ ^^^^^^      ^^^^^^ JoinContext provides all of these
 * ```
 */
class JoinContextGenerator(
    private val fromCombination: QueryCombinationInfo,
    private val joiningTable: TableInfo,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        val allTables = fromCombination.tables + joiningTable
        val className = "JoinContext_" + allTables.joinToString("_") { it.capitalizedName }

        appendLine("class $className(")
        appendLine("    private val state: com.obabichev.kodama.query.QueryState,")

        // Different parameter type for subqueries vs regular tables
        if (joiningTable.isSubquery) {
            appendLine("    private val table: com.obabichev.kodama.schema.Table")
        } else {
            appendLine("    private val table: $schemaPackage.${joiningTable.capitalizedName}")
        }

        appendLine(") {")

        // Generate properties for existing tables/subqueries
        fromCombination.tables.forEach { table ->
            appendLine("    val ${table.camelCaseName}: ${table.capitalizedName}Accessor")

            // Different handling for subqueries vs regular tables
            if (table.isSubquery) {
                appendLine("        get() = ${table.capitalizedName}Accessor(TableAccessor(SubqueryRegistry.getOrCreate<${table.capitalizedName}>(), state.relations))")
            } else {
                appendLine("        get() = ${table.capitalizedName}Accessor(TableAccessor($schemaPackage.${table.capitalizedName}, state.relations))")
            }
            appendLine()
        }

        // Generate property for the joining table (uses 'table' parameter)
        appendLine("    val ${joiningTable.camelCaseName}: ${joiningTable.capitalizedName}Accessor")
        appendLine("        get() = ${joiningTable.capitalizedName}Accessor(TableAccessor(table, state.relations))")

        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.QueryState",
            "com.obabichev.kodama.query.TableAccessor"
        )
    }
}
