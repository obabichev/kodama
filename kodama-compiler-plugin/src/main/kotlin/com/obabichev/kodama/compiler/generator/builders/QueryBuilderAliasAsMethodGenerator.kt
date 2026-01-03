package com.obabichev.kodama.compiler.generator.builders

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the .aliasAs<T>() method for creating type-safe subqueries.
 *
 * The aliasAs() method converts a query builder into a SubqueryTable that implements
 * a marker interface, enabling type-safe subquery usage.
 *
 * Example output for Person + Order combination:
 * ```
 * inline fun <reified T : Any, PersonSel, OrderSel, AC : AggCount> 
 * AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC>.aliasAs(): T {
 *     val markerClass = T::class
 *     val query = this.build()
 *     return SubqueryRegistry.createSubquery(markerClass, query) as T
 * }
 * ```
 *
 * Usage in queries:
 * ```
 * val subquery = from(Order)
 *     .selectAs(UserName) { order.userName }
 *     .build()
 *     .aliasAs<ExpensiveOrders>()
 * //         ^^^^^^^^^^^^^^^ Type-safe subquery marker
 * ```
 *
 * The SubqueryRegistry uses the marker class to instantiate the correct SubqueryTable implementation.
 */
class QueryBuilderAliasAsMethodGenerator(
    private val combination: QueryCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Build generic type parameters
        val selectionTypeParams = combination.tables.joinToString(", ") { 
            "${it.capitalizedName}Sel" 
        }
        val allTypeParams = "reified T : Any, $selectionTypeParams, AC : AggCount"
        val wildcards = selectionTypeParams.split(", ").joinToString(", ") { "*" }

        appendLine("/**")
        appendLine(" * Create a type-safe subquery with marker interface T.")
        appendLine(" * Returns T (marker interface) - SubqueryTable classes implement their markers!")
        appendLine(" */")
        appendLine("inline fun <$allTypeParams> ${combination.builderClassName}<$wildcards, AC>.aliasAs(): T {")
        appendLine("    val markerClass = T::class")
        appendLine("    val query = this.build()")
        appendLine("    return SubqueryRegistry.createSubquery(markerClass, query) as T")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.AggCount"
        )
    }
}
