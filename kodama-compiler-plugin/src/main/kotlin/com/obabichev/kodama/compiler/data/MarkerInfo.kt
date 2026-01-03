package com.obabichev.kodama.compiler.data

/**
 * SQL alias naming style for markers.
 * Determines how property names are converted to SQL column aliases.
 */
enum class SqlAliasStyle {
    /**
     * camelCase: propertyName → "propertyName"
     * Used for aggregates and expressions
     */
    CAMEL_CASE,

    /**
     * snake_case: propertyName → "property_name"
     * Used for column selections
     */
    SNAKE_CASE
}

/**
 * Metadata for a selection marker interface.
 *
 * Selection markers enable type-safe named selections:
 * ```
 * .selectAs(TotalRevenue) { sum(order.cost) }
 * ```
 *
 * Example:
 * ```
 * MarkerInfo(
 *     interfaceName = "TotalRevenue",
 *     propertyName = "totalRevenue",
 *     packageName = "com.example.generated",
 *     resultType = "Number",
 *     sqlAliasStyle = SqlAliasStyle.CAMEL_CASE
 * )
 * ```
 */
data class MarkerInfo(
    /**
     * Interface name in PascalCase (e.g., "TotalRevenue", "OrderCount")
     */
    val interfaceName: String,

    /**
     * Corresponding property name in camelCase (e.g., "totalRevenue", "orderCount")
     * Used for result accessors: `row.totalRevenue`
     */
    val propertyName: String,

    /**
     * Package where the marker interface is defined or will be generated
     */
    val packageName: String,

    /**
     * The actual result type returned by selections using this marker.
     * Default is "Number" for aggregates, but can be any type.
     */
    val resultType: String = "Number",

    /**
     * SQL alias naming style for this marker.
     * Determines how the property name is converted to SQL alias.
     */
    val sqlAliasStyle: SqlAliasStyle = SqlAliasStyle.SNAKE_CASE
) {
    /**
     * SelectionResult class name for this marker.
     * Example: "SelectionResult_totalRevenue"
     */
    val selectionResultClassName: String
        get() = "SelectionResult_$propertyName"

    /**
     * Phantom type marker for this selection.
     * Example: "SelectionSet_totalRevenue"
     */
    val phantomTypeMarker: String
        get() = "SelectionSet_$propertyName"

    /**
     * SQL alias for this marker based on the naming style.
     * Example: "totalRevenue" (CAMEL_CASE) or "total_revenue" (SNAKE_CASE)
     */
    val sqlAlias: String
        get() = when (sqlAliasStyle) {
            SqlAliasStyle.CAMEL_CASE -> propertyName
            SqlAliasStyle.SNAKE_CASE -> propertyName.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
        }
}
