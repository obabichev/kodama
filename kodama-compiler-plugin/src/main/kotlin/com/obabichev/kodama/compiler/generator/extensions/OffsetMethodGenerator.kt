package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the .offset(n) extension method for skipping rows in query results.
 *
 * The offset() method adds an OFFSET clause to the query, skipping the specified
 * number of rows before returning results.
 *
 * Example output for Person+Order combination:
 * ```
 * fun <PersonSel, OrderSel, AC : AggCount>
 * AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC>.offset(
 *     count: Int
 * ): AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC> {
 *     state._offset = count
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
 *     .limit(10)
 *     .offset(20)  // Skip first 20, return next 10
 * ```
 *
 * Typically used for pagination:
 * - Page 1: .limit(10).offset(0)
 * - Page 2: .limit(10).offset(10)
 * - Page 3: .limit(10).offset(20)
 *
 * Notes:
 * - Multiple offset() calls will overwrite the previous offset
 * - OFFSET without ORDER BY gives unpredictable results
 * - OFFSET is applied after WHERE filtering
 */
class OffsetMethodGenerator(
    private val combination: QueryCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Build type parameters
        val selectionParams = combination.tables.joinToString(", ") { "${it.capitalizedName}Sel" }
        val allParams = "$selectionParams, AC : AggCount, JP : JoinPattern"

        appendLine("/**")
        appendLine(" * Skip a number of rows before returning results.")
        appendLine(" */")
        appendLine("fun <$allParams>")
        appendLine("${combination.builderClassName}<$selectionParams, AC, JP>.offset(")
        appendLine("    count: Int")
        appendLine("): ${combination.builderClassName}<$selectionParams, AC, JP> {")
        appendLine("    state._offset = count")
        appendLine("    return ${combination.builderClassName}(state)")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.AggCount",
            "com.obabichev.kodama.query.JoinPattern"
        )
    }
}
