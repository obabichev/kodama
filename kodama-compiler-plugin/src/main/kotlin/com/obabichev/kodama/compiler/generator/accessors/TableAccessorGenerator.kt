package com.obabichev.kodama.compiler.generator.accessors

import com.obabichev.kodama.compiler.data.TableInfo
import com.obabichev.kodama.compiler.generator.CodeGenerator

/**
 * Generates a table accessor class with typed column accessors.
 *
 * Table accessors provide type-safe access to table columns in query contexts.
 * Each column becomes a typed property that carries table and column marker types.
 *
 * Example output for Person table:
 * ```
 * /**
 *  * Type-safe accessor for Person table
 *  */
 * class PersonAccessor(
 *     private val tableAccessor: TableAccessor
 * ) {
 *     fun all() = PersonAllMarker(tableAccessor.table)
 *     operator fun invoke() = PersonAllMarker(tableAccessor.table)
 *
 *     val name: com.obabichev.kodama.components.TypedColumn<String, PersonTable, Name>
 *         get() = com.obabichev.kodama.components.TypedColumn(com.example.Person.name)
 *
 *     val age: com.obabichev.kodama.components.TypedColumn<Int, PersonTable, Age>
 *         get() = com.obabichev.kodama.components.TypedColumn(com.example.Person.age)
 * }
 * ```
 *
 * Usage in queries:
 * ```
 * .select { person.name }  // person is PersonAccessor, name is TypedColumn
 * ```
 */
class TableAccessorGenerator(
    private val table: TableInfo,
    private val schemaPackage: String
) : CodeGenerator {

    override fun generate(): String = buildString {
        appendLine("/**")
        appendLine(" * Type-safe accessor for ${table.capitalizedName} table")
        appendLine(" */")
        appendLine("class ${table.capitalizedName}Accessor(")
        appendLine("    private val tableAccessor: TableAccessor")
        appendLine(") {")
        appendLine("    fun all() = ${table.capitalizedName}AllMarker(tableAccessor.table)")
        appendLine("    // Allow using accessor itself as AllMarker (for selectAll { person } instead of selectAll { person.all() })")
        appendLine("    operator fun invoke() = ${table.capitalizedName}AllMarker(tableAccessor.table)")
        appendLine()

        // Generate typed column properties
        table.columns.forEach { column ->
            val nullMarker = if (column.isNullable) "?" else ""
            appendLine("    val ${column.propertyName}: com.obabichev.kodama.components.TypedColumn<${column.kotlinType}$nullMarker, ${table.capitalizedName}Table, ${column.capitalizedName}>")
            appendLine("        get() = com.obabichev.kodama.components.TypedColumn($schemaPackage.${table.capitalizedName}.${column.propertyName})")
            appendLine()
        }

        appendLine("}")
    }

    override fun requiredImports(): Set<String> {
        return setOf(
            "com.obabichev.kodama.components.TypedColumn",
            "com.obabichev.kodama.query.TableAccessor"
        )
    }
}
