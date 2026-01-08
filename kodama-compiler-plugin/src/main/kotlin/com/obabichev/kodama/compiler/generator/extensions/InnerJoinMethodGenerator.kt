package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.data.TableInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the .innerJoin() extension method for adding tables to a query.
 *
 * This is an alias for .join() to match SQL-standard naming conventions.
 * The innerJoin() method extends an existing builder to add a new table with INNER JOIN.
 * It creates a new builder with additional generic type parameters for the joined table.
 *
 * Example output for Person + Order combination:
 * ```
 * inline fun <PersonSel, AC : AggCount>
 * AfterFromQueryBuilder_Person<PersonSel, AC>.innerJoin(
 *     table: Order,
 *     crossinline condition: JoinContext_Person_Order.(OrderAccessor) -> BooleanExpression
 * ): AfterFromQueryBuilder_Person_Order<PersonSel, NoColumnsSelected, AC> {
 *     val joinClause = JoinClause(
 *         table = table,
 *         type = JoinType.INNER,
 *         condition = {
 *             val context = JoinContext_Person_Order(state)
 *             val accessor = OrderAccessor(state.relations.tableAccessor(table))
 *             context.condition(accessor)
 *         }
 *     )
 *     state._joins.add(joinClause)
 *     state.relations.addTable(table)
 *     return AfterFromQueryBuilder_Person_Order(state)
 * }
 * ```
 *
 * Type parameters:
 * - Preserves existing table selection states
 * - Adds NoColumnsSelected for the new table
 * - Preserves aggregate count
 *
 * The condition lambda receives:
 * - `this`: JoinContext with all tables
 * - Parameter: Accessor for the joining table
 */
class InnerJoinMethodGenerator(
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
        // INNER JOIN: Compute target JP by building the join pattern with all INNER joins
        val targetPattern = if (fromCombination.joinedTables.isEmpty()) {
            // Single join: Person -> Order
            "INNER"
        } else {
            // Multi-join: Person_Order_INNER -> Person_Order_Profile_INNER_INNER
            fromCombination.joinPattern + "_INNER"
        }
        val targetJP = "JoinPattern_$targetPattern"
        val targetAllParams = "$targetSelParams, AC"

        val contextClassName = "JoinContext_" + toCombination.tables.joinToString("_") { it.capitalizedName }

        appendLine("/**")
        appendLine(" * INNER JOIN ${joiningTable.capitalizedName} table (SQL-standard naming).")
        appendLine(" */")
        appendLine("fun <$sourceAllParams>")
        appendLine("${fromCombination.builderClassName}<$sourceSelParams, AC, SourceJP>.innerJoin(")

        // Different parameter type for subqueries vs regular tables
        if (joiningTable.isSubquery) {
            appendLine("    table: com.obabichev.kodama.schema.Table,")
        } else {
            appendLine("    table: $schemaPackage.${joiningTable.capitalizedName},")
        }

        appendLine("    condition: $contextClassName.() -> Expression")
        appendLine("): ${toCombination.builderClassName}<$targetAllParams, $targetJP> {")
        appendLine("    val join = Join(")
        appendLine("        type = JoinType.INNER,")
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
            "com.obabichev.kodama.query.TableAccessor",
            "com.obabichev.kodama.components.expression.Expression",
            "com.obabichev.kodama.components.Join",
            "com.obabichev.kodama.components.JoinType"
        )
    }
}
