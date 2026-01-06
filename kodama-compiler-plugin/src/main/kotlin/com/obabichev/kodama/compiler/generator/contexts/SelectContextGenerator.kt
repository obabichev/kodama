package com.obabichev.kodama.compiler.generator.contexts

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates a SelectContext class for a query combination.
 *
 * SelectContext provides typed access to all tables in a query for selection operations.
 * Each table becomes a property that returns a typed accessor.
 *
 * Example output for Person + Order combination:
 * ```
 * class SelectContext_Person_Order(
 *     private val state: com.obabichev.kodama.query.QueryState
 * ) {
 *     val person: PersonAccessor
 *         get() = PersonAccessor(state.relations.tableAccessor(com.example.Person))
 *
 *     val order: OrderAccessor
 *         get() = OrderAccessor(state.relations.tableAccessor(com.example.Order))
 * }
 * ```
 *
 * Usage in queries:
 * ```
 * .select { person.name }
 * //        ^^^^^^ SelectContext_Person_Order provides this
 * ```
 */
class SelectContextGenerator(
    private val combination: QueryCombinationInfo,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("class ${combination.contextClassName}(")
        appendLine("    private val state: com.obabichev.kodama.query.QueryState")
        appendLine(") : com.obabichev.kodama.query.SelectContext() {")

        // Generate property for each table/subquery
        combination.tables.forEach { table ->
            appendLine("    val ${table.camelCaseName}: ${table.capitalizedName}Accessor")

            // Different handling for subqueries vs regular tables
            if (table.isSubquery) {
                appendLine("        get() = ${table.capitalizedName}Accessor(TableAccessor(SubqueryRegistry.getOrCreate<${table.capitalizedName}>(), state.relations))")
            } else {
                appendLine("        get() = ${table.capitalizedName}Accessor(TableAccessor($schemaPackage.${table.capitalizedName}, state.relations))")
            }
            appendLine()
        }

        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.QueryState",
            "com.obabichev.kodama.query.TableAccessor",
            "com.obabichev.kodama.query.SelectContext"
        )
    }
}
