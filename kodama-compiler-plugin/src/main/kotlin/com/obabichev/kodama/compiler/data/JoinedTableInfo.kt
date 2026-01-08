package com.obabichev.kodama.compiler.data

/**
 * Represents the type of SQL join operation.
 *
 * Different join types affect nullability of result columns:
 * - **INNER**: Both tables required to match → columns nullable only if defined as nullable in schema
 * - **LEFT**: Left table required, right table optional → right table columns always nullable
 * - **RIGHT**: Right table required, left table optional → left table columns always nullable
 * - **FULL**: Both tables optional → all table columns always nullable
 */
enum class JoinType {
    /**
     * INNER JOIN - both sides of the join must match.
     * Only rows with matching join conditions are returned.
     *
     * Nullability: Columns are nullable only if defined as nullable in schema.
     */
    INNER,

    /**
     * LEFT OUTER JOIN - left side is required, right side is optional.
     * All rows from left table are returned, right table columns are NULL when no match.
     *
     * Nullability: Right table columns are always nullable.
     */
    LEFT,

    /**
     * RIGHT OUTER JOIN - right side is required, left side is optional.
     * All rows from right table are returned, left table columns are NULL when no match.
     *
     * Nullability: Left table columns are always nullable.
     */
    RIGHT,

    /**
     * FULL OUTER JOIN - both sides are optional.
     * All rows from both tables are returned, with NULLs where there's no match.
     *
     * Nullability: All table columns are always nullable.
     */
    FULL
}

/**
 * Represents a table that has been joined to a query, along with the type of join.
 *
 * This information is used to determine correct nullability of result accessor properties.
 *
 * Example:
 * ```kotlin
 * from(Person)                           // Base table (never nullable)
 *     .innerJoin(Order) { ... }          // JoinedTableInfo(Order, INNER)
 *     .leftJoin(Profile) { ... }         // JoinedTableInfo(Profile, LEFT)
 * ```
 *
 * Generated result class:
 * ```kotlin
 * data class QueryResult_Person_Order_Profile(...) {
 *     val person: PersonResultAccessor_All_NonNull      // Base table
 *     val order: OrderResultAccessor_All_NonNull        // INNER JOIN
 *     val profile: ProfileResultAccessor_All_Nullable   // LEFT JOIN
 * }
 * ```
 *
 * @property table The table being joined
 * @property joinType The type of join (INNER, LEFT, RIGHT, FULL)
 */
data class JoinedTableInfo(
    val table: TableInfo,
    val joinType: JoinType
)
