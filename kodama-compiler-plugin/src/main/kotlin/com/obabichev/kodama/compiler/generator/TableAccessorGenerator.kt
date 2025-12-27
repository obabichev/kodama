package com.obabichev.kodama.compiler.generator

import com.obabichev.kodama.compiler.model.TableModel

/**
 * Generates table accessor classes.
 *
 * These classes provide type-safe access to table columns in query contexts.
 * Example:
 * ```kotlin
 * class PersonAccessor(private val tableAccessor: TableAccessor) {
 *     fun all() = PersonAllMarker(tableAccessor.table)
 *     val name: TypedColumn<String, PersonTable, Name>
 *         get() = TypedColumn(com.example.Person.name)
 *     val age: TypedColumn<Int, PersonTable, Age>
 *         get() = TypedColumn(com.example.Person.age)
 * }
 * ```
 */
class TableAccessorGenerator(
    private val schemaPackage: String
) : CodeGenerator<TableModel> {

    override fun generate(model: TableModel): String = buildString {
        generateAccessorClass(model)
        appendLine()
        generateAllMarkerClass(model)
    }

    private fun StringBuilder.generateAccessorClass(model: TableModel) {
        appendLine("/**")
        appendLine(" * Type-safe accessor for ${model.name} table.")
        appendLine(" * Provides access to columns and all() marker.")
        appendLine(" */")
        appendLine("class ${model.pascalCaseName}Accessor(")
        appendLine("    private val tableAccessor: TableAccessor")
        appendLine(") {")

        // all() method
        appendLine("    fun all() = ${model.pascalCaseName}AllMarker(tableAccessor.table)")
        appendLine("    // Allow using accessor itself as AllMarker")
        appendLine("    operator fun invoke() = ${model.pascalCaseName}AllMarker(tableAccessor.table)")
        appendLine()

        // Column properties
        model.columns.forEach { column ->
            appendLine("    val ${column.name}: com.obabichev.kodama.components.TypedColumn<${column.kotlinType}${if (column.isNullable) "?" else ""}, ${model.pascalCaseName}Table, ${column.pascalCaseName}>")
            appendLine("        get() = com.obabichev.kodama.components.TypedColumn($schemaPackage.${model.pascalCaseName}.${column.name})")
            appendLine()
        }

        appendLine("}")
    }

    private fun StringBuilder.generateAllMarkerClass(model: TableModel) {
        appendLine("/**")
        appendLine(" * Marker class for .all() selection on ${model.name} table.")
        appendLine(" */")
        appendLine("class ${model.pascalCaseName}AllMarker(val table: com.obabichev.kodama.schema.Table) : AllMarker")
    }
}

/**
 * Generates ORDER BY accessor classes for tables.
 *
 * These provide access to columns with .asc()/.desc() methods.
 */
class TableOrderByAccessorGenerator(
    private val schemaPackage: String
) : CodeGenerator<TableModel> {

    override fun generate(model: TableModel): String = buildString {
        appendLine("/**")
        appendLine(" * Type-safe accessor for ${model.name} table in ORDER BY context.")
        appendLine(" */")
        appendLine("class ${model.pascalCaseName}OrderByAccessor(")
        appendLine("    private val tableAccessor: TableAccessor")
        appendLine(") {")

        model.columns.forEach { column ->
            appendLine("    val ${column.name}: com.obabichev.kodama.components.TypedColumn<${column.kotlinType}${if (column.isNullable) "?" else ""}, ${model.pascalCaseName}Table, ${column.pascalCaseName}>")
            appendLine("        get() = com.obabichev.kodama.components.TypedColumn($schemaPackage.${model.pascalCaseName}.${column.name})")
            appendLine()
        }

        appendLine("}")
    }
}
