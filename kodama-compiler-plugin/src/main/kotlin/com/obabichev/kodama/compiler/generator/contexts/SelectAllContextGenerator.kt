package com.obabichev.kodama.compiler.generator.contexts

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates a SelectAllContext class for selectAll { } lambda operations.
 *
 * SelectAllContext provides typed access to all tables for the .selectAll { table } syntax.
 * It returns AllMarker objects instead of accessors.
 *
 * Example output for Person + Order combination:
 * ```
 * class SelectAllContext_Person_Order(
 *     private val state: com.obabichev.kodama.query.QueryState
 * ) {
 *     val person: PersonAllMarker
 *         get() = PersonAllMarker(state.relations.relation(com.example.Person).table)
 *
 *     val order: OrderAllMarker
 *         get() = OrderAllMarker(state.relations.relation(com.example.Order).table)
 * }
 * ```
 *
 * Usage in queries:
 * ```
 * .selectAll { person }
 * //           ^^^^^^ SelectAllContext provides this, returns PersonAllMarker
 * ```
 */
class SelectAllContextGenerator(
    private val combination: QueryCombinationInfo,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        val className = "SelectAllContext_" + combination.tables.joinToString("_") { it.capitalizedName }

        appendLine("class $className(")
        appendLine("    private val state: com.obabichev.kodama.query.QueryState")
        appendLine(") {")

        // Generate property for each table returning AllMarker
        combination.tables.forEach { table ->
            appendLine("    val ${table.camelCaseName}: ${table.capitalizedName}AllMarker")

            // Different handling for subqueries vs regular tables
            if (table.isSubquery) {
                appendLine("        get() = ${table.capitalizedName}AllMarker(SubqueryRegistry.getOrCreate<${table.capitalizedName}>())")
            } else {
                appendLine("        get() = ${table.capitalizedName}AllMarker($schemaPackage.${table.capitalizedName})")
            }
            appendLine()
        }

        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.QueryState"
        )
    }
}
