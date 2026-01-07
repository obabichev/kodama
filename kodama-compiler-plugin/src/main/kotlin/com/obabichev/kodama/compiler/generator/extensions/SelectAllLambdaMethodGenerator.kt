package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.data.TableInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the .selectAll { table } extension method for lambda-based selection.
 *
 * This variant uses a lambda that receives a SelectAllContext and returns an AllMarker.
 * The lambda provides typed access to table accessors.
 *
 * Example output for Person+Order combination:
 * ```
 * inline fun <PersonSel, OrderSel, AC : AggCount, T : Any>
 * AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC>.selectAll(
 *     crossinline selector: SelectAllContext_Person_Order.() -> AllMarker<T>
 * ): AfterFromQueryBuilder_Person_Order<AllColumnsSelected, AllColumnsSelected, AC> {
 *     val context = SelectAllContext_Person_Order(state)
 *     val allMarker = context.selector()
 *     state._selectExpressions.addAll(allMarker.columns())
 *     return AfterFromQueryBuilder_Person_Order(state)
 * }
 * ```
 *
 * Note: This generator creates ONE method per combination that works for ANY table
 * in the combination. The type system tracks which table was selected via the return type.
 *
 * Usage:
 * ```
 * from(Person)
 *     .join(Order) { ... }
 *     .selectAll { person }  // Returns PersonAllMarker
 *     .selectAll { order }   // Returns OrderAllMarker
 * ```
 *
 * Type state: The current implementation marks ALL tables as AllColumnsSelected,
 * which is a simplification. A more precise implementation would track per-table.
 */
class SelectAllLambdaMethodGenerator(
    private val combination: QueryCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Build type parameters
        val selectionParams = combination.tables.joinToString(", ") { "${it.capitalizedName}Sel" }
        val sourceAllParams = "$selectionParams, AC : AggCount, JP : JoinPattern"

        // For simplification, mark all as AllColumnsSelected after selectAll
        val targetSelParams = combination.tables.joinToString(", ") { "AllColumnsSelected" }
        val targetAllParams = "$targetSelParams, AC, JP"

        val contextClassName = "SelectAllContext_" + combination.tables.joinToString("_") { it.capitalizedName }

        appendLine("/**")
        appendLine(" * Select all columns from a table using lambda syntax.")
        appendLine(" */")
        appendLine("inline fun <$sourceAllParams>")
        appendLine("${combination.builderClassName}<$selectionParams, AC, JP>.selectAll(")
        appendLine("    crossinline selector: $contextClassName.() -> AllColumnsMarker")
        appendLine("): ${combination.builderClassName}<$targetAllParams> {")
        appendLine("    val context = $contextClassName(state)")
        appendLine("    val allMarker = context.selector()")
        appendLine("    state.applySelection(allMarker.asTableAllSelection())")
        appendLine("    return ${combination.builderClassName}(state)")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.AggCount",
            "com.obabichev.kodama.query.JoinPattern",
            "com.obabichev.kodama.query.AllColumnsSelected"
        )
    }
}
