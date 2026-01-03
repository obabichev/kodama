package com.obabichev.kodama.compiler.data

/**
 * Column information specific to subquery results.
 *
 * Similar to ColumnInfo but tailored for subquery columns, which may include:
 * - Regular table columns (e.g., userName from Order)
 * - Marker-based selections (e.g., MyAlias for a calculated expression)
 * - Aggregates (e.g., sum(order.cost))
 *
 * Example:
 * ```
 * SubqueryColumnInfo(
 *     propertyName = "userName",
 *     sqlColumnName = "user_name",
 *     kotlinType = "String",
 *     isNullable = false,
 *     isMarkerBased = false
 * )
 * ```
 */
data class SubqueryColumnInfo(
    /**
     * Kotlin property name in camelCase (e.g., "userName", "totalCost")
     */
    val propertyName: String,

    /**
     * SQL column name or alias (e.g., "user_name", "total_cost")
     */
    val sqlColumnName: String,

    /**
     * Kotlin type as a string (e.g., "String", "Number", "Int")
     */
    val kotlinType: String,

    /**
     * Whether this column can be null
     */
    val isNullable: Boolean = false,

    /**
     * Whether this column uses a marker interface (like MyAlias) rather than a direct table column
     */
    val isMarkerBased: Boolean = false
) {
    /**
     * Property name in PascalCase for marker interfaces
     */
    val capitalizedName: String
        get() = propertyName.replaceFirstChar { it.uppercase() }

    /**
     * Kotlin type with nullability marker if needed
     */
    val kotlinTypeWithNullability: String
        get() = if (isNullable) "$kotlinType?" else kotlinType
}
