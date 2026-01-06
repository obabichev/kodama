package com.obabichev.kodama.compiler.generator.extensions

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.data.SubqueryInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates the .joinAliased() extension method for adding inline subqueries to a query.
 *
 * The joinAliased() method extends an existing builder to add a subquery with INNER JOIN.
 * It accepts a marker and a lambda that builds the subquery inline.
 *
 * Example output for Person + UsersWithOrders subquery:
 * ```
 * inline fun <reified T, PersonSel, AC : AggCount>
 * AfterFromQueryBuilder_Person<PersonSel, AC>.joinAliased(
 *     marker: T,
 *     queryBuilder: () -> Query,
 *     crossinline condition: JoinContext_Person_UsersWithOrders.() -> Expression
 * ): AfterFromQueryBuilder_Person_UsersWithOrders<PersonSel, NoColumnsSelected, AC>
 *     where T : UsersWithOrders {
 *     val query = queryBuilder()
 *     val subqueryTable = SubqueryRegistry.createSubquery(T::class, query) as Table
 *     val join = Join(
 *         type = JoinType.INNER,
 *         relation = state.relations.relation(subqueryTable),
 *         condition = {
 *             val context = JoinContext_Person_UsersWithOrders(state, subqueryTable)
 *             context.condition()
 *         }()
 *     )
 *     state._joins.add(join)
 *     return AfterFromQueryBuilder_Person_UsersWithOrders(state)
 * }
 * ```
 *
 * Usage:
 * ```
 * from(Person)
 *     .joinAliased(UsersWithOrders) {
 *         from(Order)
 *             .selectAs(OrderUserName) { order.userName }
 *             .build()
 *     } { person.name eq usersWithOrders.orderUserName }
 * ```
 */
class JoinAliasedMethodGenerator(
    private val fromCombination: QueryCombinationInfo,
    private val subquery: SubqueryInfo,
    private val toCombination: QueryCombinationInfo  // The target combination with subquery
) : CodeGenerator {

    override fun generate(): String = buildString {
        // Build type parameters for source builder
        val sourceSelParams = fromCombination.tables.joinToString(", ") { "${it.capitalizedName}Sel" }
        val sourceAllParams = "$sourceSelParams, AC : AggCount"

        // Build type parameters for target builder (with subquery)
        val targetSelParams = toCombination.tables.joinToString(", ") {
            if (it.name == subquery.name) "NoColumnsSelected"
            else "${it.capitalizedName}Sel"
        }
        val targetAllParams = "$targetSelParams, AC"

        val targetBuilderName = toCombination.builderClassName
        val contextClassName = "JoinContext_" + toCombination.tables.joinToString("_") { it.capitalizedName }
        val subqueryTableClassName = subquery.subqueryTableClassName

        // Generate unique JVM name to avoid overload ambiguity
        val jvmName = "joinAliased_${fromCombination.builderClassName}_${subquery.name}"

        appendLine("/**")
        appendLine(" * INNER JOIN inline subquery ${subquery.name}.")
        appendLine(" * Use: .joinAliased(query.aliasAs<${subquery.name}>()) { condition }")
        appendLine(" */")
        appendLine("@JvmName(\"$jvmName\")")
        appendLine("inline fun <T, $sourceAllParams>")
        appendLine("${fromCombination.builderClassName}<$sourceSelParams, AC>.joinAliased(")
        appendLine("    subquery: T,")
        appendLine("    crossinline condition: $contextClassName.() -> Expression")
        appendLine("): $targetBuilderName<$targetAllParams>")
        appendLine("    where T : ${subquery.name} {")
        appendLine("    val subqueryTable = subquery as $subqueryTableClassName")
        appendLine("    state._subqueryTables[subqueryTable.alias] = subqueryTable")
        appendLine("    val join = Join(")
        appendLine("        type = JoinType.INNER,")
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
