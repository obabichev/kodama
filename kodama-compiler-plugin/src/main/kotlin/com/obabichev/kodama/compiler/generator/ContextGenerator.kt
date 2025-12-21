package com.obabichev.kodama.compiler.generator

import com.obabichev.kodama.compiler.model.ContextModel
import com.obabichev.kodama.compiler.model.ContextType

/**
 * Generates context classes for query building.
 *
 * Context classes provide type-safe access to tables in different query phases:
 * - JoinContext: Access to tables when defining join conditions
 * - SelectContext: Access to tables when selecting columns
 * - WhereContext: Access to tables when defining WHERE conditions
 * - OrderByContext: Access to tables when defining ORDER BY clauses
 *
 * Example:
 * ```kotlin
 * class JoinContext_Person_Order(private val state: QueryState) {
 *     val person = PersonAccessor(TableAccessor(Person, state.relations))
 *     val order = OrderAccessor(TableAccessor(Order, state.relations))
 * }
 * ```
 */
class ContextGenerator(
    private val schemaPackage: String
) : CodeGenerator<ContextModel> {

    override fun generate(model: ContextModel): String = buildString {
        generateClassHeader(model)
        generateTableAccessors(model)
        appendLine("}")
    }

    private fun StringBuilder.generateClassHeader(model: ContextModel) {
        appendLine("/**")
        appendLine(" * ${model.type.displayName} context for tables: ${model.tables.joinToString(", ")}")
        appendLine(" */")

        val baseClass = when (model.type) {
            ContextType.ORDER_BY -> " : com.obabichev.kodama.query.OrderByContext()"
            else -> ""
        }

        appendLine("class ${model.className}(")
        appendLine("    private val state: QueryState")
        appendLine(")$baseClass {")
    }

    private fun StringBuilder.generateTableAccessors(model: ContextModel) {
        model.tables.forEach { tableName ->
            val tableCapitalized = tableName.replaceFirstChar { it.uppercase() }
            val propertyName = tableName.replaceFirstChar { it.lowercase() }

            val accessorType = when (model.type) {
                ContextType.ORDER_BY -> "${tableCapitalized}OrderByAccessor"
                else -> "${tableCapitalized}Accessor"
            }

            appendLine("    val $propertyName = $accessorType(TableAccessor($schemaPackage.$tableCapitalized, state.relations))")
        }
    }

    private val ContextType.displayName: String
        get() = when (this) {
            ContextType.JOIN -> "Join"
            ContextType.SELECT -> "Select"
            ContextType.WHERE -> "Where"
            ContextType.ORDER_BY -> "ORDER BY"
        }
}
