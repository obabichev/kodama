package com.obabichev.kodama.compiler.generator

import com.obabichev.kodama.compiler.model.ResultAccessorModel

/**
 * Generates result accessor classes.
 *
 * These classes provide type-safe access to selected columns in query results.
 * Example:
 * ```kotlin
 * class PersonResultAccessor_Name_Age(
 *     resultSet: ResultSet,
 *     relations: RelationsContainer,
 *     selectedColumns: List<Column<*>>
 * ) : TableResultAccessor(resultSet, relations, selectedColumns) {
 *     val name: String
 *         get() = readColumn(com.example.Person.name) as String
 *     val age: Int
 *         get() = readColumn(com.example.Person.age) as Int
 * }
 * ```
 */
class ResultAccessorGenerator : CodeGenerator<ResultAccessorModel> {

    override fun generate(model: ResultAccessorModel): String = buildString {
        generateDocComment(model)
        generateClassSignature(model)
        generateProperties(model)
        appendLine("}")
    }

    private fun StringBuilder.generateDocComment(model: ResultAccessorModel) {
        appendLine("/**")
        appendLine(" * Result accessor for ${model.tableName} table.")
        appendLine(" * Provides access to: ${model.properties.joinToString(", ") { it.name }}")
        appendLine(" */")
    }

    private fun StringBuilder.generateClassSignature(model: ResultAccessorModel) {
        appendLine("class ${model.className}(")
        appendLine("    resultSet: java.sql.ResultSet,")
        appendLine("    relations: com.obabichev.kodama.query.RelationsContainer,")
        appendLine("    selectedColumns: List<com.obabichev.kodama.components.Column<*>>")
        appendLine(") : com.obabichev.kodama.query.TableResultAccessor(resultSet, relations, selectedColumns) {")
        appendLine()
    }

    private fun StringBuilder.generateProperties(model: ResultAccessorModel) {
        model.properties.forEach { property ->
            val nullabilityMarker = if (property.isNullable) "?" else ""
            appendLine("    val ${property.name}: ${property.kotlinType}$nullabilityMarker")
            appendLine("        get() = readColumn(${property.columnReference}) as ${property.kotlinType}$nullabilityMarker")
            appendLine()
        }
    }
}

/**
 * Generates all result accessor variations for a table.
 *
 * This includes:
 * - All columns accessor (TableResultAccessor_All)
 * - Single column accessors (TableResultAccessor_Name)
 * - Two column accessors (TableResultAccessor_Name_Age)
 * - Multi-column accessors (TableResultAccessor_Name_Age_Email)
 */
class TableResultAccessorsGenerator(
    private val schemaPackage: String
) : CodeGenerator<com.obabichev.kodama.compiler.model.TableModel> {

    private val singleAccessorGenerator = ResultAccessorGenerator()

    override fun generate(model: com.obabichev.kodama.compiler.model.TableModel): String = buildString {
        // Generate "All" accessor
        val allAccessor = createAllAccessorModel(model)
        append(singleAccessorGenerator.generate(allAccessor))
        appendLine()

        // Generate single column accessors
        model.columns.forEach { column ->
            val accessor = createSingleColumnAccessorModel(model, column)
            append(singleAccessorGenerator.generate(accessor))
            appendLine()
        }

        // Generate two-column combination accessors
        if (model.columns.size >= 2) {
            for (i in 0 until model.columns.size - 1) {
                for (j in i + 1 until model.columns.size) {
                    val accessor = createTwoColumnAccessorModel(model, model.columns[i], model.columns[j])
                    append(singleAccessorGenerator.generate(accessor))
                    appendLine()
                }
            }
        }
    }

    private fun createAllAccessorModel(table: com.obabichev.kodama.compiler.model.TableModel): ResultAccessorModel {
        return ResultAccessorModel(
            className = "${table.pascalCaseName}ResultAccessor_All",
            tableName = table.name,
            properties = table.columns.map { column ->
                com.obabichev.kodama.compiler.model.PropertyModel(
                    name = column.name,
                    kotlinType = column.kotlinType,
                    isNullable = column.isNullable,
                    columnReference = "$schemaPackage.${table.pascalCaseName}.${column.name}"
                )
            },
            schemaPackage = schemaPackage,
            generatedPackage = ""  // Not used in generation
        )
    }

    private fun createSingleColumnAccessorModel(
        table: com.obabichev.kodama.compiler.model.TableModel,
        column: com.obabichev.kodama.compiler.model.ColumnModel
    ): ResultAccessorModel {
        return ResultAccessorModel(
            className = "${table.pascalCaseName}ResultAccessor_${column.pascalCaseName}",
            tableName = table.name,
            properties = listOf(
                com.obabichev.kodama.compiler.model.PropertyModel(
                    name = column.name,
                    kotlinType = column.kotlinType,
                    isNullable = column.isNullable,
                    columnReference = "$schemaPackage.${table.pascalCaseName}.${column.name}"
                )
            ),
            schemaPackage = schemaPackage,
            generatedPackage = ""
        )
    }

    private fun createTwoColumnAccessorModel(
        table: com.obabichev.kodama.compiler.model.TableModel,
        col1: com.obabichev.kodama.compiler.model.ColumnModel,
        col2: com.obabichev.kodama.compiler.model.ColumnModel
    ): ResultAccessorModel {
        return ResultAccessorModel(
            className = "${table.pascalCaseName}ResultAccessor_${col1.pascalCaseName}_${col2.pascalCaseName}",
            tableName = table.name,
            properties = listOf(
                com.obabichev.kodama.compiler.model.PropertyModel(
                    name = col1.name,
                    kotlinType = col1.kotlinType,
                    isNullable = col1.isNullable,
                    columnReference = "$schemaPackage.${table.pascalCaseName}.${col1.name}"
                ),
                com.obabichev.kodama.compiler.model.PropertyModel(
                    name = col2.name,
                    kotlinType = col2.kotlinType,
                    isNullable = col2.isNullable,
                    columnReference = "$schemaPackage.${table.pascalCaseName}.${col2.name}"
                )
            ),
            schemaPackage = schemaPackage,
            generatedPackage = ""
        )
    }
}
