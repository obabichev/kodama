package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the .where { condition } extension method for filtering query results.
 *
 * The where() method adds a WHERE clause to the query. It provides a type-safe
 * context with access to all tables in the query for building filter conditions.
 *
 * Example output for Person+Order combination:
 * ```
 * inline fun <PersonSel, OrderSel, AC : AggCount>
 * AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC>.where(
 *     crossinline condition: WhereContext_Person_Order.() -> BooleanExpression
 * ): AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC> {
 *     val context = WhereContext_Person_Order(state)
 *     val expr = context.condition()
 *     state._where.add(expr)
 *     return AfterFromQueryBuilder_Person_Order(state)
 * }
 * ```
 *
 * Type state:
 * - All type parameters are preserved (selection states, aggregate count)
 * - Returns the same builder type for further chaining
 *
 * The WhereContext provides typed accessors for all tables, enabling
 * compile-time verified column references.
 *
 * Usage:
 * ```
 * from(Person)
 *     .join(Order) { ... }
 *     .selectAll(Person)
 *     .where { person.age eq 25 }  // WhereContext has person and order
 * ```
 *
 * Multiple where() calls can be chained (they are AND-ed together in the state).
 */
class WhereMethodGenerator(
    private val combination: QueryCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Build type parameters
        val selectionParams = combination.tables.joinToString(", ") { "${it.capitalizedName}Sel" }
        val allParams = "$selectionParams, AC : AggCount, JP : JoinPattern"

        val contextClassName = "WhereContext_" + combination.tables.joinToString("_") { it.capitalizedName }

        appendLine("/**")
        appendLine(" * Add a WHERE clause to filter results.")
        appendLine(" */")
        appendLine("inline fun <$allParams>")
        appendLine("${combination.builderClassName}<$selectionParams, AC, JP>.where(")
        appendLine("    crossinline condition: $contextClassName.() -> Expression")
        appendLine("): ${combination.builderClassName}<$selectionParams, AC, JP> {")
        appendLine("    val context = $contextClassName(state)")
        appendLine("    val expr = context.condition()")
        appendLine("    state.whereExpression = expr")
        appendLine("    return ${combination.builderClassName}(state)")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.AggCount",
            "com.obabichev.kodama.query.JoinPattern",
            "com.obabichev.kodama.components.expression.Expression"
        )
    }
}
