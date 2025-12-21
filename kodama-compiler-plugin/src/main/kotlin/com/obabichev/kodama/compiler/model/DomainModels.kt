package com.obabichev.kodama.compiler.model

import com.obabichev.kodama.compiler.Selection
import com.obabichev.kodama.compiler.SelectionType

/**
 * Domain models for code generation.
 * These are pure data structures that can be easily constructed and tested.
 */

/**
 * Represents a database table with its metadata.
 *
 * @property name The table name (PascalCase, e.g., "TradingStrategy")
 * @property columns List of columns in the table
 * @property schemaPackage The package where the table definition lives
 */
data class TableModel(
    val name: String,
    val columns: List<ColumnModel>,
    val schemaPackage: String
) {
    val camelCaseName: String = name.replaceFirstChar { it.lowercase() }
    val pascalCaseName: String = name.replaceFirstChar { it.uppercase() }
}

/**
 * Represents a column in a table.
 *
 * @property name The property name (camelCase, e.g., "strategyName")
 * @property kotlinType The Kotlin type (e.g., "String", "Int")
 * @property isNullable Whether the column accepts null values
 * @property isPrimaryKey Whether this is a primary key column
 */
data class ColumnModel(
    val name: String,
    val kotlinType: String,
    val isNullable: Boolean,
    val isPrimaryKey: Boolean = false
) {
    val pascalCaseName: String = name.replaceFirstChar { it.uppercase() }
}

/**
 * Represents a query builder for a specific combination of tables.
 *
 * @property tables List of table names in join order (e.g., ["Person", "Order"])
 * @property className The generated builder class name (e.g., "AfterFromQueryBuilder_Person_Order")
 * @property generatedPackage The package where generated code will live
 */
data class QueryBuilderModel(
    val tables: List<String>,
    val className: String,
    val generatedPackage: String
) {
    val builderName: String
        get() = className
}

/**
 * Represents a selection pattern - what columns/aggregates are selected in a query.
 *
 * @property tables Tables involved in this pattern
 * @property selections List of individual selections (columns or aggregates)
 * @property columnSelections Map of table name to selected columns
 */
data class SelectionPatternModel(
    val tables: List<String>,
    val selections: List<Selection>,
    val columnSelections: Map<String, List<String>> = emptyMap()
)

// Selection and SelectionType are imported from SelectionPatternScanner.kt

/**
 * Represents a result accessor class that provides access to query results.
 *
 * @property className The class name (e.g., "PersonResultAccessor_Name_Age")
 * @property tableName The table this accessor is for
 * @property properties List of properties to generate
 * @property schemaPackage Package where table definitions live
 * @property generatedPackage Package where generated code lives
 */
data class ResultAccessorModel(
    val className: String,
    val tableName: String,
    val properties: List<PropertyModel>,
    val schemaPackage: String,
    val generatedPackage: String
)

/**
 * Represents a property in a result accessor.
 *
 * @property name Property name (camelCase)
 * @property kotlinType Kotlin type
 * @property isNullable Whether nullable
 * @property columnReference Reference to table column (e.g., "Person.name")
 */
data class PropertyModel(
    val name: String,
    val kotlinType: String,
    val isNullable: Boolean,
    val columnReference: String
)

/**
 * Represents a context class (Join, Select, Where, OrderBy).
 *
 * @property type Type of context
 * @property className The class name (e.g., "JoinContext_Person_Order")
 * @property tables Tables available in this context
 * @property schemaPackage Package where table definitions live
 * @property generatedPackage Package where generated code lives
 */
data class ContextModel(
    val type: ContextType,
    val className: String,
    val tables: List<String>,
    val schemaPackage: String,
    val generatedPackage: String
)

enum class ContextType {
    JOIN,
    SELECT,
    WHERE,
    ORDER_BY
}

/**
 * Represents an INSERT method for a table.
 *
 * @property tableName The table name
 * @property parameters List of method parameters
 * @property schemaPackage Package where table definitions live
 */
data class InsertMethodModel(
    val tableName: String,
    val parameters: List<ParameterModel>,
    val schemaPackage: String
)

/**
 * Represents a method parameter.
 */
data class ParameterModel(
    val name: String,
    val type: String,
    val isNullable: Boolean
)

/**
 * Represents an execute() method overload.
 *
 * @property selectionPattern The selection pattern this execute method is for
 * @property builderClassName The builder class name
 * @property resultClassName The result class name
 * @property typeParameters Type parameters for the method
 * @property jvmName The @JvmName annotation value
 */
data class ExecuteMethodModel(
    val selectionPattern: SelectionPatternModel,
    val builderClassName: String,
    val resultClassName: String,
    val typeParameters: List<String>,
    val jvmName: String
)

/**
 * Represents the entire code generation model.
 * This is the root model that contains all information needed for generation.
 *
 * @property tables All tables discovered
 * @property queryBuilders All query builders to generate
 * @property selectionPatterns All selection patterns discovered
 * @property resultAccessors All result accessor classes to generate
 * @property contexts All context classes to generate
 * @property insertMethods All insert methods to generate
 * @property executeMethods All execute method overloads to generate
 * @property schemaPackage Package where schema definitions live
 * @property generatedPackage Package where generated code will be written
 */
data class CodeGenerationModel(
    val tables: List<TableModel>,
    val queryBuilders: List<QueryBuilderModel>,
    val selectionPatterns: List<SelectionPatternModel>,
    val resultAccessors: List<ResultAccessorModel>,
    val contexts: List<ContextModel>,
    val insertMethods: List<InsertMethodModel>,
    val executeMethods: List<ExecuteMethodModel>,
    val schemaPackage: String,
    val generatedPackage: String
)
