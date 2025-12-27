package com.obabichev.kodama.compiler.generator

import com.obabichev.kodama.compiler.model.InsertMethodModel

/**
 * Generates INSERT method extensions for tables.
 *
 * These methods provide type-safe, compile-time validated insert operations.
 * All columns are required as method parameters (nullable columns accept null).
 *
 * Example:
 * ```kotlin
 * fun Person.insert(
 *     transaction: JdbcTransaction,
 *     name: String,
 *     age: Int
 * ): InsertResult {
 *     val table = this
 *     val insert = InsertStatement(
 *         table = table,
 *         columns = listOf(table.name, table.age),
 *         values = listOf(name, age)
 *     )
 *     return insert.execute(transaction)
 * }
 * ```
 */
class InsertMethodGenerator(
    private val schemaPackage: String
) : CodeGenerator<InsertMethodModel> {

    override fun generate(model: InsertMethodModel): String = buildString {
        generateDocComment(model)
        generateMethodSignature(model)
        generateMethodBody(model)
        appendLine("}")
    }

    private fun StringBuilder.generateDocComment(model: InsertMethodModel) {
        appendLine("/**")
        appendLine(" * Insert a row into the ${model.tableName} table.")
        appendLine(" * All columns are required parameters for compile-time safety.")
        appendLine(" *")
        model.parameters.forEach { param ->
            if (param.name != "transaction") {
                appendLine(" * @param ${param.name} Value for column '${param.name}'")
            }
        }
        appendLine(" * @return InsertResult with rows affected and generated keys")
        appendLine(" */")
    }

    private fun StringBuilder.generateMethodSignature(model: InsertMethodModel) {
        val tableCapitalized = model.tableName.replaceFirstChar { it.uppercase() }
        appendLine("fun $schemaPackage.$tableCapitalized.insert(")

        model.parameters.forEachIndexed { index, param ->
            val comma = if (index < model.parameters.size - 1) "," else ""
            val nullMarker = if (param.isNullable) "?" else ""
            appendLine("    ${param.name}: ${param.type}$nullMarker$comma")
        }

        appendLine("): com.obabichev.kodama.insert.InsertResult {")
    }

    private fun StringBuilder.generateMethodBody(model: InsertMethodModel) {
        val tableCapitalized = model.tableName.replaceFirstChar { it.uppercase() }

        appendLine("    val table = this")
        appendLine("    val insert = com.obabichev.kodama.insert.InsertStatement(")
        appendLine("        table = table,")

        // Generate columns list
        val columnParams = model.parameters.filter { it.name != "transaction" }
        val columnsList = columnParams.joinToString(", ") { "table.${it.name}" }
        appendLine("        columns = listOf($columnsList),")

        // Generate values list
        val valuesList = columnParams.joinToString(", ") { it.name }
        appendLine("        values = listOf($valuesList)")

        appendLine("    )")
        appendLine("    return insert.execute(transaction)")
    }
}
