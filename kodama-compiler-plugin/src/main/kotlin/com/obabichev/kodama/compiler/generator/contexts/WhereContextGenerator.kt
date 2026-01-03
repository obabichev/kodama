package com.obabichev.kodama.compiler.generator.contexts

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates a WhereContext class for WHERE clause operations.
 *
 * WhereContext provides typed access to all tables in a query for building WHERE conditions.
 * Identical structure to SelectContext but used in a different context.
 *
 * Example output for Person + Order combination:
 * ```
 * class WhereContext_Person_Order(
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
 * .where { person.age gt 18 and order.cost gt 100 }
 * //       ^^^^^^ ^^^           ^^^^^ ^^^^ WhereContext provides these
 * ```
 */
class WhereContextGenerator(
    private val combination: QueryCombinationInfo,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("class ${combination.whereContextClassName}(")
        appendLine("    private val state: com.obabichev.kodama.query.QueryState")
        appendLine(") {")

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
            "com.obabichev.kodama.query.TableAccessor"
        )
    }
}
