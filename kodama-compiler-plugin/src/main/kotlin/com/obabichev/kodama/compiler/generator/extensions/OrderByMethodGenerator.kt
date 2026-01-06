package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the .orderBy { } extension method for sorting query results.
 *
 * The orderBy() method adds an ORDER BY clause to the query. The lambda can
 * specify multiple ordering expressions by calling .asc() or .desc() on columns.
 *
 * Example output for Person+Order combination:
 * ```
 * inline fun <PersonSel, OrderSel, AC : AggCount>
 * AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC>.orderBy(
 *     crossinline builder: OrderByContext_Person_Order.() -> Unit
 * ): AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC> {
 *     val context = OrderByContext_Person_Order(state)
 *     context.builder()
 *     return AfterFromQueryBuilder_Person_Order(state)
 * }
 * ```
 *
 * Type state:
 * - All type parameters are preserved
 * - Returns the same builder type for further chaining
 *
 * The OrderByContext provides typed accessors for all tables. Each column
 * has .asc() and .desc() methods that add OrderByClause entries to the state.
 *
 * Usage:
 * ```
 * from(Person)
 *     .selectAll(Person)
 *     .orderBy {
 *         person.age.desc()   // First sort by age descending
 *         person.name.asc()   // Then by name ascending
 *     }
 * ```
 *
 * Multiple orderBy() calls can be chained (they accumulate in the state).
 */
class OrderByMethodGenerator(
    private val combination: QueryCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Build type parameters
        val selectionParams = combination.tables.joinToString(", ") { "${it.capitalizedName}Sel" }
        val allParams = "$selectionParams, AC : AggCount"

        val contextClassName = "OrderByContext_" + combination.tables.joinToString("_") { it.capitalizedName }

        appendLine("/**")
        appendLine(" * Add ORDER BY clause to sort results.")
        appendLine(" */")
        appendLine("inline fun <$allParams>")
        appendLine("${combination.builderClassName}<$selectionParams, AC>.orderBy(")
        appendLine("    crossinline block: $contextClassName.() -> com.obabichev.kodama.query.OrderByClause")
        appendLine("): ${combination.builderClassName}<$selectionParams, AC> {")
        appendLine("    val context = $contextClassName(state)")
        appendLine("    val clause = context.block()")
        appendLine("    state._orderBy.add(clause)")
        appendLine("    return ${combination.builderClassName}(state)")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.AggCount",
            "com.obabichev.kodama.query.OrderByClause"
        )
    }
}
