package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.data.SubqueryInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the .leftJoinAliased() extension method for adding inline subqueries with LEFT JOIN.
 *
 * Similar to joinAliased() but uses LEFT OUTER JOIN instead of INNER JOIN.
 * It accepts a marker and a lambda that builds the subquery inline.
 *
 * Example output for Person + OrderCounts subquery:
 * ```
 * inline fun <reified T, PersonSel, AC : AggCount>
 * AfterFromQueryBuilder_Person<PersonSel, AC>.leftJoinAliased(
 *     marker: T,
 *     queryBuilder: () -> Query,
 *     crossinline condition: JoinContext_Person_OrderCounts.() -> Expression
 * ): AfterFromQueryBuilder_Person_OrderCounts<PersonSel, NoColumnsSelected, AC>
 *     where T : OrderCounts {
 *     val query = queryBuilder()
 *     val subqueryTable = SubqueryRegistry.createSubquery(T::class, query) as Table
 *     val join = Join(
 *         type = JoinType.LEFT,
 *         relation = state.relations.relation(subqueryTable),
 *         condition = {
 *             val context = JoinContext_Person_OrderCounts(state, subqueryTable)
 *             context.condition()
 *         }()
 *     )
 *     state._joins.add(join)
 *     return AfterFromQueryBuilder_Person_OrderCounts(state)
 * }
 * ```
 *
 * Usage:
 * ```
 * from(Person)
 *     .leftJoinAliased(OrderCounts) {
 *         from(Order)
 *             .selectAs(OrderCount) { count(order.id) }
 *             .groupBy { order.userName }
 *             .build()
 *     } { person.name eq orderCounts.orderUserName }
 * ```
 */
class LeftJoinAliasedMethodGenerator(
    private val fromCombination: QueryCombinationInfo,
    private val subquery: SubqueryInfo,
    private val toCombination: QueryCombinationInfo  // The target combination with subquery
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Build type parameters for source builder
        val sourceSelParams = fromCombination.tables.joinToString(", ") { "${it.capitalizedName}Sel" }
        val sourceAllParams = "$sourceSelParams, AC : AggCount, SourceJP : JoinPattern"

        // Build type parameters for target builder (with subquery)
        val targetSelParams = toCombination.tables.joinToString(", ") {
            if (it.name == subquery.name) "NoColumnsSelected"
            else "${it.capitalizedName}Sel"
        }
        // Target JP is determined by the join pattern of toCombination
        val targetJP = toCombination.joinPatternTypeName
        val targetAllParams = "$targetSelParams, AC"

        val targetBuilderName = toCombination.builderClassName
        val contextClassName = "JoinContext_" + toCombination.tables.joinToString("_") { it.capitalizedName }
        val subqueryTableClassName = subquery.subqueryTableClassName

        // Generate unique JVM name to avoid overload ambiguity
        val jvmName = "leftJoinAliased_${fromCombination.builderClassName}_${subquery.name}"

        appendLine("/**")
        appendLine(" * LEFT OUTER JOIN inline subquery ${subquery.name}.")
        appendLine(" * Use: .leftJoinAliased(query.aliasAs<${subquery.name}>()) { condition }")
        appendLine(" */")
        appendLine("@JvmName(\"$jvmName\")")
        appendLine("inline fun <T, $sourceAllParams>")
        appendLine("${fromCombination.builderClassName}<$sourceSelParams, AC, SourceJP>.leftJoinAliased(")
        appendLine("    subquery: T,")
        appendLine("    crossinline condition: $contextClassName.() -> Expression")
        appendLine("): $targetBuilderName<$targetAllParams, $targetJP>")
        appendLine("    where T : ${subquery.name} {")
        appendLine("    val subqueryTable = subquery as $subqueryTableClassName")
        appendLine("    state._subqueryTables[subqueryTable.alias] = subqueryTable")
        appendLine("    val join = Join(")
        appendLine("        type = JoinType.LEFT,")
        appendLine("        relation = state.relations.relation(subqueryTable),")
        appendLine("        condition = {")
        appendLine("            val context = $contextClassName(state, subqueryTable)")
        appendLine("            context.condition()")
        appendLine("        }()")
        appendLine("    )")
        appendLine("    state._joins.add(join)")
        appendLine("    return $targetBuilderName(state)")
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
