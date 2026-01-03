package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the .select { column } extension method for selecting individual columns.
 *
 * This method allows selecting exactly ONE column or expression at a time.
 * Multiple columns require multiple .select{} calls.
 *
 * Example output for Person+Order combination:
 * ```
 * inline fun <PersonSel, OrderSel, AC : AggCount, TTable, TCol>
 * AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC>.select(
 *     crossinline selector: SelectContext_Person_Order.() -> TypedColumn<*, TTable, TCol>
 * ): AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC> {
 *     val context = SelectContext_Person_Order(state)
 *     val column = context.selector()
 *     state._selectExpressions.add(SelectExpression.ColumnSelect(column.column))
 *     return AfterFromQueryBuilder_Person_Order(state)
 * }
 * ```
 *
 * Type parameters:
 * - `TTable`: The table marker type (PersonTable, OrderTable)
 * - `TCol`: The column marker type (Name, Age, Cost)
 * - Selection states are preserved (no type-level tracking of individual columns yet)
 * - Aggregate count is preserved
 *
 * The lambda receives a SelectContext with typed access to all table accessors.
 *
 * Usage:
 * ```
 * from(Person)
 *     .select { person.name }   // Select one column
 *     .select { person.age }    // Select another column
 *     .where { person.name eq "kodama" }
 * ```
 */
class SelectMethodGenerator(
    private val combination: QueryCombinationInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Build type parameters
        val selectionParams = combination.tables.joinToString(", ") { "${it.capitalizedName}Sel" }
        val allParams = "$selectionParams, AC : AggCount, TTable, TCol"

        val contextClassName = "SelectContext_" + combination.tables.joinToString("_") { it.capitalizedName }

        appendLine("/**")
        appendLine(" * Select a single column or expression.")
        appendLine(" */")
        appendLine("inline fun <$allParams>")
        appendLine("${combination.builderClassName}<$selectionParams, AC>.select(")
        appendLine("    crossinline selector: $contextClassName.() -> TypedColumn<*, TTable, TCol>")
        appendLine("): ${combination.builderClassName}<$selectionParams, AC> {")
        appendLine("    val context = $contextClassName(state)")
        appendLine("    val column = context.selector()")
        appendLine("    state._selectedColumns.add(column.column)")
        appendLine("    return ${combination.builderClassName}(state)")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.AggCount",
            "com.obabichev.kodama.components.TypedColumn"
        )
    }
}
