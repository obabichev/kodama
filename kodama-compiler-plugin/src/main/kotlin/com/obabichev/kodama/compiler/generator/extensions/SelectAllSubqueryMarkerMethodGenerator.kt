package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.data.SubqueryInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the .selectAll(SubqueryMarker) extension method for selecting all columns from a subquery.
 *
 * This variant accepts the marker companion object directly (e.g., UserTotalsNew) and
 * retrieves the table from SubqueryRegistry.
 *
 * Example output for UserTotalsNew subquery:
 * ```
 * inline fun <reified T : UserTotalsNew, UserTotalsNewSel, AC : AggCount>
 * AfterFromQueryBuilder_UserTotalsNew<UserTotalsNewSel, AC>.selectAll(
 *     marker: T
 * ): AfterFromQueryBuilder_UserTotalsNew<AllColumnsSelected, AC> {
 *     val table = SubqueryRegistry.getOrCreate<T>()
 *     val allMarker = UserTotalsNewAllMarker(table)
 *     state.applySelection(allMarker.asTableAllSelection())
 *     return AfterFromQueryBuilder_UserTotalsNew(state)
 * }
 * ```
 *
 * Usage:
 * ```
 * fromAliased(UserTotalsNew) { ... }
 *     .selectAll(UserTotalsNew)  // Pass marker directly
 * ```
 */
class SelectAllSubqueryMarkerMethodGenerator(
    private val combination: QueryCombinationInfo,
    private val subquery: SubqueryInfo
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Build type parameters: before selection (source)
        val sourceSelParams = combination.tables.joinToString(", ") { "${it.capitalizedName}Sel" }
        val sourceAllParams = "$sourceSelParams, AC : AggCount, JP : JoinPattern"

        // Build type parameters: after selection (target)
        val targetSelParams = combination.tables.joinToString(", ") {
            if (it.name == subquery.name) "AllColumnsSelected"
            else "${it.capitalizedName}Sel"
        }
        val targetAllParams = "$targetSelParams, AC, JP"

        // Generate unique JVM name to avoid conflicts
        val jvmName = "selectAll_${combination.builderClassName}_${subquery.name}_marker"

        appendLine("/**")
        appendLine(" * Select all columns from ${subquery.name} subquery using marker.")
        appendLine(" */")
        appendLine("@JvmName(\"$jvmName\")")
        appendLine("inline fun <reified T : ${subquery.name}, $sourceAllParams>")
        appendLine("${combination.builderClassName}<$sourceSelParams, AC, JP>.selectAll(")
        appendLine("    marker: T")
        appendLine("): ${combination.builderClassName}<$targetAllParams> {")
        appendLine("    // Get the table from the subquery tables map (it was stored by fromAliased or joinAliased)")
        appendLine("    val table = state._subqueryTables[\"${subquery.sqlAlias}\"] ?: throw IllegalStateException(\"Subquery table ${subquery.name} not found in state\")")
        appendLine("    val allMarker = ${subquery.name}AllMarker(table)")
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
