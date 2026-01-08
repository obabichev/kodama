package com.obabichev.kodama.compiler.generator.contexts

import com.obabichev.kodama.compiler.data.QueryCombinationInfo
import com.obabichev.kodama.compiler.data.TableInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates correlated subquery helper methods for WHERE contexts.
 *
 * These methods allow building EXISTS, NOT EXISTS, and scalar subqueries
 * that reference the outer query's tables.
 *
 * Example output for Person WHERE context:
 * ```kotlin
 * class WhereContext_Person(...) {
 *     // ... existing table accessors ...
 *
 *     // Correlated EXISTS
 *     inline fun <reified InnerBuilder : Any> existsWith(
 *         crossinline condition: (PersonAccessor, InnerBuilder) -> Expression
 *     ): Expression {
 *         // Implementation that builds subquery with access to both person and inner table
 *     }
 * }
 * ```
 */
class CorrelatedWhereMethodsGenerator(
    private val combination: QueryCombinationInfo,
    private val innerTable: TableInfo,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        val outerContextParams = combination.tables.joinToString(", ") { table ->
            "${table.camelCaseName}: ${table.capitalizedName}Accessor"
        }

        // Generate existsWith method
        appendLine("    /**")
        appendLine("     * Build a correlated EXISTS subquery with ${innerTable.capitalizedName}.")
        appendLine("     * The lambda receives outer context (${combination.tables.joinToString { it.camelCaseName }}) and inner table (${innerTable.camelCaseName}).")
        appendLine("     */")
        appendLine("    inline fun existsWith${innerTable.capitalizedName}(")
        appendLine("        crossinline condition: ($outerContextParams, ${innerTable.capitalizedName}Accessor) -> com.obabichev.kodama.components.expression.Expression")
        appendLine("    ): com.obabichev.kodama.components.expression.Expression {")
        appendLine("        val innerQuery = from($schemaPackage.${innerTable.capitalizedName})")
        appendLine("            .select { ${innerTable.camelCaseName}.id }")  // Dummy select
        appendLine("            .where {")
        appendLine("                val inner${innerTable.capitalizedName} = ${innerTable.camelCaseName}")
        combination.tables.forEach { outerTable ->
            appendLine("                val outer${outerTable.capitalizedName} = this@${combination.whereContextClassName}.${outerTable.camelCaseName}")
        }
        appendLine("                condition(${combination.tables.joinToString { "outer${it.capitalizedName}" }}, inner${innerTable.capitalizedName})")
        appendLine("            }")
        appendLine("            .build()")
        appendLine("        return com.obabichev.kodama.components.expression.ExistsExpression(innerQuery)")
        appendLine("    }")
        appendLine()
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.components.expression.Expression",
            "com.obabichev.kodama.components.expression.ExistsExpression",
            "com.obabichev.kodama.query.from"
        )
    }
}
