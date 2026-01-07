package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.data.TableInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the .selectAll(Table) extension method for selecting all columns from a table.
 *
 * This is the direct variant where you pass the table object as a parameter,
 * as opposed to the lambda variant `.selectAll { person }`.
 *
 * Example output for Person table in Person+Order combination:
 * ```
 * fun <PersonSel, OrderSel, AC : AggCount>
 * AfterFromQueryBuilder_Person_Order<PersonSel, OrderSel, AC>.selectAll(
 *     table: Person
 * ): AfterFromQueryBuilder_Person_Order<AllColumnsSelected, OrderSel, AC> {
 *     val allMarker = PersonAllMarker(table)
 *     state._selectExpressions.addAll(allMarker.columns())
 *     return AfterFromQueryBuilder_Person_Order(state)
 * }
 * ```
 *
 * Type state changes:
 * - The selection type for the specified table changes from any state to `AllColumnsSelected`
 * - Other tables' selection states are preserved
 * - Aggregate count is preserved
 *
 * Usage:
 * ```
 * from(Person)
 *     .join(Order) { ... }
 *     .selectAll(Person)  // Person becomes AllColumnsSelected
 *     .selectAll(Order)   // Order becomes AllColumnsSelected
 * ```
 */
class SelectAllDirectMethodGenerator(
    private val combination: QueryCombinationInfo,
    private val targetTable: TableInfo,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Build type parameters: before selection (source)
        val sourceSelParams = combination.tables.joinToString(", ") { "${it.capitalizedName}Sel" }
        val sourceAllParams = "$sourceSelParams, AC : AggCount, JP : JoinPattern"

        // Build type parameters: after selection (target)
        val targetSelParams = combination.tables.joinToString(", ") {
            if (it.name == targetTable.name) "AllColumnsSelected"
            else "${it.capitalizedName}Sel"
        }
        val targetAllParams = "$targetSelParams, AC, JP"

        appendLine("/**")
        appendLine(" * Select all columns from ${targetTable.capitalizedName}.")
        appendLine(" */")
        appendLine("fun <$sourceAllParams>")
        appendLine("${combination.builderClassName}<$sourceSelParams, AC, JP>.selectAll(")

        // Different parameter type for subqueries vs regular tables
        if (targetTable.isSubquery) {
            appendLine("    table: com.obabichev.kodama.schema.Table")
        } else {
            appendLine("    table: $schemaPackage.${targetTable.capitalizedName}")
        }

        appendLine("): ${combination.builderClassName}<$targetAllParams> {")
        appendLine("    val allMarker = ${targetTable.capitalizedName}AllMarker(table)")
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
