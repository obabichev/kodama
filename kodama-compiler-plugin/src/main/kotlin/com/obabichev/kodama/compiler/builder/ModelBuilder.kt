package com.obabichev.kodama.compiler.builder

import com.obabichev.kodama.compiler.SelectionPattern
import com.obabichev.kodama.compiler.model.*

/**
 * Builder interface for constructing domain models.
 *
 * Builders transform raw scanned data into structured domain models.
 * This separation allows:
 * - Complex model construction logic to be isolated
 * - Easy testing with different inputs
 * - Reuse of building logic
 *
 * @param TInput The raw input type (e.g., scanner results)
 * @param TOutput The domain model type
 */
interface ModelBuilder<TInput, TOutput> {
    /**
     * Build a domain model from raw input data.
     *
     * @param input The raw data to transform
     * @return The constructed domain model
     */
    fun build(input: TInput): TOutput
}

/**
 * Input data for table model building.
 * Contains raw information extracted from table definitions.
 */
data class TableBuildInput(
    val tableName: String,
    val properties: List<String>,
    val propertyTypes: Map<String, String>,
    val propertyNullability: Map<String, Boolean>,
    val schemaPackage: String
)

/**
 * Builds TableModel from scanned table information.
 */
class TableModelBuilder : ModelBuilder<TableBuildInput, TableModel> {

    override fun build(input: TableBuildInput): TableModel {
        val columns = input.properties.map { propName ->
            ColumnModel(
                name = propName,
                kotlinType = input.propertyTypes[propName] ?: "Any",
                isNullable = input.propertyNullability[propName] ?: true,
                isPrimaryKey = false  // TODO: Detect primary keys
            )
        }

        return TableModel(
            name = input.tableName,
            columns = columns,
            schemaPackage = input.schemaPackage
        )
    }
}

/**
 * Input data for insert method building.
 */
data class InsertMethodBuildInput(
    val table: TableModel,
    val schemaPackage: String
)

/**
 * Builds InsertMethodModel from a table model.
 */
class InsertMethodModelBuilder : ModelBuilder<InsertMethodBuildInput, InsertMethodModel> {

    override fun build(input: InsertMethodBuildInput): InsertMethodModel {
        // First parameter is always transaction
        val parameters = mutableListOf(
            ParameterModel(
                name = "transaction",
                type = "com.obabichev.kodama.execute.JdbcTransaction",
                isNullable = false
            )
        )

        // Add a parameter for each column
        input.table.columns.forEach { column ->
            parameters.add(
                ParameterModel(
                    name = column.name,
                    type = column.kotlinType,
                    isNullable = column.isNullable
                )
            )
        }

        return InsertMethodModel(
            tableName = input.table.name,
            parameters = parameters,
            schemaPackage = input.schemaPackage
        )
    }
}

/**
 * Input data for context model building.
 */
data class ContextBuildInput(
    val tables: List<String>,
    val contextType: ContextType,
    val schemaPackage: String,
    val generatedPackage: String
)

/**
 * Builds ContextModel instances.
 */
class ContextModelBuilder : ModelBuilder<ContextBuildInput, ContextModel> {

    override fun build(input: ContextBuildInput): ContextModel {
        val className = buildClassName(input.tables, input.contextType)

        return ContextModel(
            type = input.contextType,
            className = className,
            tables = input.tables,
            schemaPackage = input.schemaPackage,
            generatedPackage = input.generatedPackage
        )
    }

    private fun buildClassName(tables: List<String>, type: ContextType): String {
        val prefix = when (type) {
            ContextType.JOIN -> "JoinContext"
            ContextType.SELECT -> "SelectContext"
            ContextType.WHERE -> "WhereContext"
            ContextType.ORDER_BY -> "OrderByContext"
        }

        val tablesPart = tables.joinToString("_") { it.replaceFirstChar { c -> c.uppercase() } }
        return "${prefix}_$tablesPart"
    }
}

/**
 * Input for building the complete code generation model.
 */
data class CodeGenerationBuildInput(
    val tables: List<TableModel>,
    val queryPatterns: Map<List<String>, Set<List<String>>>,  // Table combo -> selection patterns
    val selectionPatterns: Map<List<String>, Set<SelectionPattern>>,
    val schemaPackage: String,
    val generatedPackage: String
)

/**
 * Builds the complete CodeGenerationModel.
 * This is the main orchestrator that combines all sub-builders.
 */
class CodeGenerationModelBuilder : ModelBuilder<CodeGenerationBuildInput, CodeGenerationModel> {

    private val insertMethodBuilder = InsertMethodModelBuilder()
    private val contextBuilder = ContextModelBuilder()

    override fun build(input: CodeGenerationBuildInput): CodeGenerationModel {
        // Build query builders
        val queryBuilders = buildQueryBuilders(input)

        // Build selection patterns models
        val selectionPatternModels = buildSelectionPatternModels(input)

        // Build result accessors
        val resultAccessors = buildResultAccessors(input)

        // Build contexts
        val contexts = buildContexts(input)

        // Build insert methods
        val insertMethods = input.tables.map { table ->
            insertMethodBuilder.build(
                InsertMethodBuildInput(
                    table = table,
                    schemaPackage = input.schemaPackage
                )
            )
        }

        // Build execute methods (placeholder for now)
        val executeMethods = emptyList<ExecuteMethodModel>()

        return CodeGenerationModel(
            tables = input.tables,
            queryBuilders = queryBuilders,
            selectionPatterns = selectionPatternModels,
            resultAccessors = resultAccessors,
            contexts = contexts,
            insertMethods = insertMethods,
            executeMethods = executeMethods,
            schemaPackage = input.schemaPackage,
            generatedPackage = input.generatedPackage
        )
    }

    private fun buildQueryBuilders(input: CodeGenerationBuildInput): List<QueryBuilderModel> {
        return input.queryPatterns.keys.map { tables ->
            val className = "AfterFromQueryBuilder_" + tables.joinToString("_") {
                it.replaceFirstChar { c -> c.uppercase() }
            }

            QueryBuilderModel(
                tables = tables,
                className = className,
                generatedPackage = input.generatedPackage
            )
        }
    }

    private fun buildSelectionPatternModels(input: CodeGenerationBuildInput): List<SelectionPatternModel> {
        return input.selectionPatterns.flatMap { (tables, patterns) ->
            patterns.map { pattern ->
                SelectionPatternModel(
                    tables = tables,
                    selections = pattern.selections,
                    columnSelections = pattern.columnSelections
                )
            }
        }
    }

    private fun buildResultAccessors(input: CodeGenerationBuildInput): List<ResultAccessorModel> {
        val accessors = mutableListOf<ResultAccessorModel>()

        // Build multi-column accessors from selection patterns (3+ columns)
        input.selectionPatterns.forEach { (tables, patterns) ->
            patterns.forEach { pattern ->
                // Group selections by table
                val columnSelectionsByTable = pattern.columnSelections

                columnSelectionsByTable.forEach { (tableName, columns) ->
                    // Only generate for 3+ column combinations
                    if (columns.size >= 3 && !columns.contains("All")) {
                        val table = input.tables.find { it.name == tableName } ?: return@forEach

                        val properties = columns.mapNotNull { columnName ->
                            val column = table.columns.find {
                                it.pascalCaseName == columnName
                            }
                            column?.let {
                                PropertyModel(
                                    name = it.name,
                                    kotlinType = it.kotlinType,
                                    isNullable = it.isNullable,
                                    columnReference = "${input.schemaPackage}.${table.pascalCaseName}.${it.name}"
                                )
                            }
                        }

                        if (properties.isNotEmpty()) {
                            val className = "${table.pascalCaseName}ResultAccessor_" + columns.joinToString("_")
                            accessors.add(
                                ResultAccessorModel(
                                    className = className,
                                    tableName = tableName,
                                    properties = properties,
                                    schemaPackage = input.schemaPackage,
                                    generatedPackage = input.generatedPackage
                                )
                            )
                        }
                    }
                }
            }
        }

        return accessors
    }

    private fun buildContexts(input: CodeGenerationBuildInput): List<ContextModel> {
        val contexts = mutableListOf<ContextModel>()

        // Generate contexts for each table combination
        input.queryPatterns.keys.forEach { tables ->
            // Generate all 4 types of contexts for each combination
            ContextType.values().forEach { contextType ->
                contexts.add(
                    contextBuilder.build(
                        ContextBuildInput(
                            tables = tables,
                            contextType = contextType,
                            schemaPackage = input.schemaPackage,
                            generatedPackage = input.generatedPackage
                        )
                    )
                )
            }
        }

        return contexts
    }
}
