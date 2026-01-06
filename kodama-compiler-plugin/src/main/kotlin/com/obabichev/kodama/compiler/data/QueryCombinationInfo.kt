package com.obabichev.kodama.compiler.data

import com.obabichev.kodama.compiler.SelectionPattern

/**
 * Represents a discovered query combination (tables joined together).
 *
 * Example:
 * ```
 * QueryCombinationInfo(
 *     tables = listOf(personTable, orderTable),
 *     selectionPatterns = listOf(...)
 * )
 * ```
 *
 * This generates:
 * - `AfterFromQueryBuilder_Person_Order` builder class
 * - `SelectContext_Person_Order` context class
 * - `JoinContext_Person_Order` context class
 * - etc.
 */
data class QueryCombinationInfo(
    /**
     * Ordered list of tables in this combination.
     * Order matters: [Person, Order] is different from [Order, Person]
     */
    val tables: List<TableInfo>,

    /**
     * Selection patterns discovered for this combination.
     * Each pattern represents a different set of selected columns/aggregates.
     */
    val selectionPatterns: List<SelectionPattern> = emptyList()
) {
    /**
     * Builder class name for this combination.
     * Example: "AfterFromQueryBuilder_Person_Order"
     */
    val builderClassName: String
        get() = "AfterFromQueryBuilder_" + tables.joinToString("_") { it.capitalizedName }

    /**
     * Select context class name.
     * Example: "SelectContext_Person_Order"
     */
    val contextClassName: String
        get() = "SelectContext_" + tables.joinToString("_") { it.capitalizedName }

    /**
     * Join context class name (includes all tables).
     * Example: "JoinContext_Person_Order"
     */
    val joinContextClassName: String
        get() = "JoinContext_" + tables.joinToString("_") { it.capitalizedName }

    /**
     * Where context class name.
     * Example: "WhereContext_Person_Order"
     */
    val whereContextClassName: String
        get() = "WhereContext_" + tables.joinToString("_") { it.capitalizedName }

    /**
     * OrderBy context class name.
     * Example: "OrderByContext_Person_Order"
     */
    val orderByContextClassName: String
        get() = "OrderByContext_" + tables.joinToString("_") { it.capitalizedName }

    /**
     * GroupBy context class name.
     * Example: "GroupByContext_Person_Order"
     */
    val groupByContextClassName: String
        get() = "GroupByContext_" + tables.joinToString("_") { it.capitalizedName }

    /**
     * Get table names as underscore-separated string.
     * Example: "Person_Order"
     */
    val tableNamesSeparated: String
        get() = tables.joinToString("_") { it.capitalizedName }
}
