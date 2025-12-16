package com.obabichev.kodama.compiler

/**
 * Pluggable scanner for discovering selection patterns in test code.
 * Each scanner is responsible for one type of selectable (aggregates, constants, subqueries, etc.)
 */
interface SelectionPatternScanner {
    /**
     * Scan a file and extract selection patterns
     */
    fun scanFile(content: String): List<SelectionPattern>
}

/**
 * Represents a discovered pattern of selections in a query
 */
data class SelectionPattern(
    val tables: List<String>,           // Tables involved in the query (e.g., ["order"])
    val selections: List<Selection>,    // Selections in order (e.g., [totalRevenue, orderCount])
    val columnSelections: Map<String, List<String>> = emptyMap()  // Table -> List of selected columns (e.g., {"order": ["cost"]})
)

/**
 * Represents a single selection (aggregate, constant, subquery, etc.)
 */
data class Selection(
    val alias: String,                   // The alias used to access this selection (e.g., "totalRevenue")
    val type: SelectionType,             // Type of selection
    val kotlinType: String = "Any"       // Kotlin type for the accessor (e.g., "Number", "Int", "String")
)

/**
 * Type of selection - mirrors SelectableType from core
 */
enum class SelectionType {
    AGGREGATE,
    CONSTANT,
    SUBQUERY,
    WINDOW_FUNCTION,
    METADATA,
    COMPUTED;

    fun toCoreType(): String = "com.obabichev.kodama.query.SelectableType.$name"
}
