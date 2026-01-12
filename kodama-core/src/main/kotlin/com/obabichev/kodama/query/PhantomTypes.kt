package com.obabichev.kodama.query

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.schema.Table

/**
 * Phantom type that encodes the join chain at compile time.
 *
 * This type parameter tracks which tables have been joined and in what order.
 * It enables compile-time validation of table references in query clauses.
 *
 * **Type Evolution Example:**
 * ```kotlin
 * from(Person)                         // JoinPattern = NoJoin
 *   .join(Order) { ... }               // JoinPattern = InnerJoin<Order, NoJoin>
 *   .leftJoin(Profile) { ... }         // JoinPattern = LeftJoin<Profile, InnerJoin<Order, NoJoin>>
 * ```
 *
 * The join chain is encoded as a nested type structure, allowing the compiler
 * to track which tables are available in each context.
 */
sealed interface JoinPattern {
    /**
     * No joins have been performed yet.
     * Only the base table from from() is available.
     */
    interface NoJoin : JoinPattern

    /**
     * INNER JOIN: Both tables are non-nullable.
     *
     * @param Joined The table being joined
     * @param Rest The previous join pattern (nested)
     */
    interface InnerJoin<Joined : Table, Rest : JoinPattern> : JoinPattern

    /**
     * LEFT OUTER JOIN: Right table is nullable.
     *
     * @param Joined The table being joined (nullable in results)
     * @param Rest The previous join pattern (nested)
     */
    interface LeftJoin<Joined : Table, Rest : JoinPattern> : JoinPattern

    /**
     * RIGHT OUTER JOIN: Left table is nullable.
     *
     * @param Joined The table being joined (left side becomes nullable)
     * @param Rest The previous join pattern (nested)
     */
    interface RightJoin<Joined : Table, Rest : JoinPattern> : JoinPattern

    /**
     * FULL OUTER JOIN: Both tables are nullable.
     *
     * @param Joined The table being joined
     * @param Rest The previous join pattern (nested)
     */
    interface FullJoin<Joined : Table, Rest : JoinPattern> : JoinPattern
}

/**
 * Phantom type that encodes the selection state at compile time.
 *
 * This type parameter tracks which columns or tables have been selected,
 * enabling compile-time validation of result accessors.
 *
 * **Type Evolution Example:**
 * ```kotlin
 * from(Person)                         // SelectionSet = NoSelections
 *   .select { person.name }            // SelectionSet = ColumnSelected<Person, Name, NoSelections>
 *   .select { person.age }             // SelectionSet = ColumnSelected<Person, Age, ColumnSelected<...>>
 * ```
 *
 * **Result Type Mapping:**
 * The SelectionSet type directly determines the result accessor structure:
 * ```kotlin
 * // SelectionSet = AllSelected<Person, NoSelections>
 * // Result type has: row.person.name, row.person.age
 *
 * // SelectionSet = ColumnSelected<Person, Name, NoSelections>
 * // Result type has: row.name only
 * ```
 */
sealed interface SelectionSet {
    /**
     * No columns selected yet.
     * This is the initial state after from() and join().
     */
    interface NoSelections : SelectionSet

    /**
     * All columns from a table are selected via selectAll(Table).
     *
     * @param FromTable The table whose columns are selected
     * @param Rest The previous selection state (nested)
     */
    interface AllSelected<FromTable : Table, Rest : SelectionSet> : SelectionSet

    /**
     * A single column is selected via select { table.column }.
     *
     * @param FromTable The table the column belongs to
     * @param Col The column type
     * @param Rest The previous selection state (nested)
     */
    interface ColumnSelected<FromTable : Table, Col : Column<*>, Rest : SelectionSet> : SelectionSet

    /**
     * An aliased expression is selected via select { expression alias "name" }.
     *
     * @param T The result type of the expression
     * @param AliasName The compile-time alias name (for type-safe access)
     * @param Rest The previous selection state (nested)
     */
    interface AliasedSelected<T, AliasName : String, Rest : SelectionSet> : SelectionSet
}

/**
 * NOTE: AggCount and related types are defined in AggregateCount.kt
 * NOTE: OrderByClause is defined in OrderBy.kt
 *
 * This file contains only the new phantom types for Kodama 2.0:
 * - JoinPattern: Tracks join chain
 * - SelectionSet: Tracks column selections
 */
