package com.obabichev.kodama.compiler.data

import com.obabichev.kodama.compiler.SelectionPattern

/**
 * Represents a discovered query combination (tables joined together).
 *
 * Example:
 * ```
 * QueryCombinationInfo(
 *     baseTable = personTable,
 *     joinedTables = listOf(
 *         JoinedTableInfo(orderTable, JoinType.INNER),
 *         JoinedTableInfo(profileTable, JoinType.LEFT)
 *     ),
 *     selectionPatterns = listOf(...)
 * )
 * ```
 *
 * This generates:
 * - `AfterFromQueryBuilder_Person_Order_Profile` builder class
 * - `SelectContext_Person_Order_Profile` context class
 * - `JoinContext_Person_Order_Profile` context class
 * - `QueryResult_Person_Order_Profile` with correct nullability based on join types
 * - etc.
 */
data class QueryCombinationInfo(
    /**
     * The base table from `from(...)`.
     * This table is never nullable in results.
     */
    val baseTable: TableInfo,

    /**
     * Tables joined to the base table, with their join types.
     * Join type determines nullability of result accessor properties:
     * - INNER: Columns nullable only if defined as nullable in schema
     * - LEFT: Right table columns always nullable
     * - RIGHT: Left table columns always nullable
     * - FULL: All columns always nullable
     */
    val joinedTables: List<JoinedTableInfo> = emptyList(),

    /**
     * Selection patterns discovered for this combination.
     * Each pattern represents a different set of selected columns/aggregates.
     */
    val selectionPatterns: List<SelectionPattern> = emptyList(),

    /**
     * True if this combination was synthesized for joinAliased support, not discovered in code.
     * Synthetic combinations (e.g., Person + UsersWithOrders) only get basic builder/context generation,
     * not marker-based selectAs or hybrid result methods to avoid code explosion.
     */
    val isSynthetic: Boolean = false
) {
    /**
     * All tables in the combination (base + joined tables).
     * Backward compatibility property - returns ordered list of all tables.
     */
    val tables: List<TableInfo>
        get() = listOf(baseTable) + joinedTables.map { it.table }

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

    /**
     * Join pattern signature encoding the specific join types used.
     * Examples:
     * - Single table: "" (empty string)
     * - Person INNER Order: "INNER"
     * - Person LEFT Order: "LEFT"
     * - Person INNER Order LEFT Profile: "INNER_LEFT"
     * - Person RIGHT Order: "RIGHT"
     * - Person FULL Order: "FULL"
     *
     * This signature is used to:
     * 1. Generate separate result classes per join pattern
     * 2. Generate phantom types for type-level join pattern tracking
     * 3. Dispatch execute methods to correct result class
     */
    val joinPattern: String
        get() = joinedTables.joinToString("_") { it.joinType.name }

    /**
     * Phantom type name for this join pattern.
     * Examples:
     * - Single table: "JoinPattern_NONE"
     * - Person INNER Order: "JoinPattern_INNER"
     * - Person LEFT Order: "JoinPattern_LEFT"
     * - Person INNER Order LEFT Profile: "JoinPattern_INNER_LEFT"
     */
    val joinPatternTypeName: String
        get() = if (joinedTables.isEmpty()) "JoinPattern_NONE" else "JoinPattern_$joinPattern"
}
