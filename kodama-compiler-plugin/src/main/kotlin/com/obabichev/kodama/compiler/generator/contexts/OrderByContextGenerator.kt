package com.obabichev.kodama.compiler.generator.contexts

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates an OrderByContext class for ORDER BY clause operations.
 *
 * OrderByContext provides typed access to all tables in a query for building ORDER BY clauses.
 *
 * Example output for Person + Order combination:
 * ```
 * class OrderByContext_Person_Order(
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
 * .orderBy { person.age.desc() }
 * //         ^^^^^^ ^^^ OrderByContext provides this
 * ```
 */
class OrderByContextGenerator(
    private val combination: QueryCombinationInfo,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("class ${combination.orderByContextClassName}(")
        appendLine("    private val state: com.obabichev.kodama.query.QueryState")
        appendLine(") {")

        // Generate property for each table/subquery (using OrderByAccessor for ORDER BY context)
        combination.tables.forEach { table ->
            appendLine("    val ${table.camelCaseName}: ${table.capitalizedName}OrderByAccessor")

            // Different handling for subqueries vs regular tables
            if (table.isSubquery) {
                appendLine("        get() = ${table.capitalizedName}OrderByAccessor(TableAccessor(SubqueryRegistry.getOrCreate<${table.capitalizedName}>(), state.relations))")
            } else {
                appendLine("        get() = ${table.capitalizedName}OrderByAccessor(TableAccessor($schemaPackage.${table.capitalizedName}, state.relations))")
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
