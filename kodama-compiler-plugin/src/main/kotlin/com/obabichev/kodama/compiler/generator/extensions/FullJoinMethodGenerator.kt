package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.data.TableInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the .fullJoin() extension method for adding tables with FULL OUTER JOIN.
 *
 * Similar to JoinMethodGenerator but creates FULL OUTER JOIN clauses, which include
 * all rows from both tables even if there's no match on either side.
 *
 * Example output for Person + Order combination:
 * ```
 * inline fun <PersonSel, AC : AggCount>
 * AfterFromQueryBuilder_Person<PersonSel, AC>.fullJoin(
 *     table: Order,
 *     crossinline condition: JoinContext_Person_Order.(OrderAccessor) -> BooleanExpression
 * ): AfterFromQueryBuilder_Person_Order<PersonSel, NoColumnsSelected, AC> {
 *     val joinClause = JoinClause(
 *         table = table,
 *         type = JoinType.FULL,
 *         condition = { ... }
 *     )
 *     state._joins.add(joinClause)
 *     state.relations.addTable(table)
 *     return AfterFromQueryBuilder_Person_Order(state)
 * }
 * ```
 *
 * The only difference from join() is `JoinType.FULL` instead of `JoinType.INNER`.
 */
class FullJoinMethodGenerator(
    private val fromCombination: QueryCombinationInfo,
    private val joiningTable: TableInfo,
    private val toCombination: QueryCombinationInfo,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Build type parameters for source builder (without joining table)
        val sourceSelParams = fromCombination.tables.joinToString(", ") { "${it.capitalizedName}Sel" }
        val sourceAllParams = "$sourceSelParams, AC : AggCount, SourceJP : JoinPattern"

        // Build type parameters for target builder (with joining table)
        val targetSelParams = toCombination.tables.joinToString(", ") {
            if (it.name == joiningTable.name) "NoColumnsSelected"
            else "${it.capitalizedName}Sel"
        }
        // FULL OUTER JOIN: Compute target JP by appending FULL to source pattern
        val targetPattern = if (fromCombination.joinedTables.isEmpty()) {
            "FULL"
        } else {
            fromCombination.joinPattern + "_FULL"
        }
        val targetJP = "JoinPattern_$targetPattern"
        val targetAllParams = "$targetSelParams, AC"

        val contextClassName = "JoinContext_" + toCombination.tables.joinToString("_") { it.capitalizedName }

        appendLine("/**")
        appendLine(" * FULL OUTER JOIN ${joiningTable.capitalizedName} table.")
        appendLine(" */")
        appendLine("fun <$sourceAllParams>")
        appendLine("${fromCombination.builderClassName}<$sourceSelParams, AC, SourceJP>.fullJoin(")

        // Different parameter type for subqueries vs regular tables
        if (joiningTable.isSubquery) {
            appendLine("    table: com.obabichev.kodama.schema.Table,")
        } else {
            appendLine("    table: $schemaPackage.${joiningTable.capitalizedName},")
        }

        appendLine("    condition: $contextClassName.() -> Expression")
        appendLine("): ${toCombination.builderClassName}<$targetAllParams, $targetJP> {")
        appendLine("    val join = Join(")
        appendLine("        type = JoinType.FULL,")
        appendLine("        relation = state.relations.relation(table),")
        appendLine("        condition = {")
        appendLine("            val context = $contextClassName(state, table)")
        appendLine("            context.condition()")
        appendLine("        }()")
        appendLine("    )")
        appendLine("    state._joins.add(join)")
        appendLine("    return ${toCombination.builderClassName}(state)")
        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.query.AggCount",
            "com.obabichev.kodama.query.NoColumnsSelected",
            "com.obabichev.kodama.query.Query",
            "com.obabichev.kodama.schema.Table",
            "com.obabichev.kodama.schema.SubqueryType",
            "com.obabichev.kodama.components.expression.Expression",
            "com.obabichev.kodama.components.Join",
            "com.obabichev.kodama.components.JoinType"
        )
    }
}
