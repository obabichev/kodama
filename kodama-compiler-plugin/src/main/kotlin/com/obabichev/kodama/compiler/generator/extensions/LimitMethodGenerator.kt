package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the .limit(n) extension method for limiting the number of results.
 *
 * The limit() method adds a LIMIT clause to the query, restricting the maximum
 * number of rows returned.
 *
 * Example output for Person+Order combination:
 * ```
 * fun <PersonSel, OrderSel, AC : AggCount>
 * AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC>.limit(
 *     count: Int
 * ): AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC> {
 *     state._limit = count
 *     return AfterFromQueryBuilder_Person_Order(state)
 * }
 * ```
 *
 * Type state:
 * - All type parameters are preserved
 * - Returns the same builder type for further chaining
 *
 * Usage:
 * ```
 * from(Person)
 *     .selectAll(Person)
 *     .orderBy { person.age.desc() }
 *     .limit(10)  // Return only top 10
 * ```
 *
 * Typically used with:
 * - ORDER BY for predictable results
 * - OFFSET for pagination
 *
 * Notes:
 * - Multiple limit() calls will overwrite the previous limit
 * - Common pattern: .limit(pageSize).offset(pageNumber * pageSize)
 */
class LimitMethodGenerator(
    private val combination: QueryCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Build type parameters
        val selectionParams = combination.tables.joinToString(", ") { "${it.capitalizedName}Sel" }
        val allParams = "$selectionParams, AC : AggCount"

        appendLine("/**")
        appendLine(" * Limit the number of results returned.")
        appendLine(" */")
        appendLine("fun <$allParams>")
        appendLine("${combination.builderClassName}<$selectionParams, AC>.limit(")
        appendLine("    count: Int")
        appendLine("): ${combination.builderClassName}<$selectionParams, AC> {")
        appendLine("    state._limit = count")
        appendLine("    return ${combination.builderClassName}(state)")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.AggCount"
        )
    }
}
