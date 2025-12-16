package com.obabichev.kodama.query

/**
 * Type-level marker for tracking the number of aggregate functions in a query.
 *
 * This sealed interface enables compile-time type safety for aggregate queries.
 * Each selectAggregate() call advances the type from NoAggregates -> Has1Aggregate -> Has2Aggregates, etc.
 *
 * Example:
 * ```kotlin
 * query()
 *     .from(Order)
 *     .selectAggregate { sum(order.cost) alias "total" }    // Returns Builder<Has1Aggregate>
 *     .selectAggregate { count(order.id) alias "count" }    // Returns Builder<Has2Aggregates>
 *     .execute(connection)  // Returns QueryResultIterable<AggregateResult2>
 * ```
 *
 * This ensures that:
 * 1. The result type matches exactly the number of aggregates selected
 * 2. Accessing non-existent aggregates (e.g., row.agg3 when only 2 selected) is a compile error
 * 3. IDE autocomplete works immediately without regeneration
 */
sealed interface AggCount

/**
 * Marker indicating no aggregate functions have been selected.
 * This is the initial state when a query builder is created.
 */
object NoAggregates : AggCount

/**
 * Marker indicating exactly 1 aggregate function has been selected.
 */
object Has1Aggregate : AggCount

/**
 * Marker indicating exactly 2 aggregate functions have been selected.
 */
object Has2Aggregates : AggCount

/**
 * Marker indicating exactly 3 aggregate functions have been selected.
 */
object Has3Aggregates : AggCount

/**
 * Marker indicating exactly 4 aggregate functions have been selected.
 */
object Has4Aggregates : AggCount

/**
 * Marker indicating exactly 5 aggregate functions have been selected.
 */
object Has5Aggregates : AggCount

/**
 * Marker indicating exactly 6 aggregate functions have been selected.
 */
object Has6Aggregates : AggCount

/**
 * Marker indicating exactly 7 aggregate functions have been selected.
 */
object Has7Aggregates : AggCount

/**
 * Marker indicating exactly 8 aggregate functions have been selected.
 */
object Has8Aggregates : AggCount

/**
 * Marker indicating exactly 9 aggregate functions have been selected.
 */
object Has9Aggregates : AggCount

/**
 * Marker indicating exactly 10 aggregate functions have been selected.
 */
object Has10Aggregates : AggCount

/**
 * Type-level marker for tracking the number of selections (aggregates, constants, subqueries, etc.) in a query.
 *
 * This interface (not sealed!) works for any type of selectable (not just aggregates).
 * It enables the new unified selection API with named accessors.
 *
 * IMPORTANT: This must be a regular interface (not sealed) to allow code generation
 * to create specific phantom types like SelectionSet_totalRevenue, SelectionSet_orderCount, etc.
 * These generated types encode the exact selections made at compile time, eliminating conflicts.
 *
 * Example:
 * ```kotlin
 * query()
 *     .from(Order)
 *     .select_totalRevenue { sum(order.cost) }  // Returns Builder<SelectionSet_totalRevenue>
 *     .select_orderCount { count(order.id) }    // Returns Builder<SelectionSet_totalRevenue_orderCount>
 *     .execute(connection)  // Returns QueryResultIterable<SelectionResult_totalRevenue_orderCount>
 * ```
 *
 * Result classes have compile-time typed named accessors:
 * ```kotlin
 * val totalRevenue: Number = row.totalRevenue  // Compile-time safe!
 * val orderCount: Number = row.orderCount      // Compile-time safe!
 * // row.otherField  // ❌ Compile error - field doesn't exist!
 * ```
 */
interface SelectionState : AggCount

/**
 * Marker indicating no selections have been made yet.
 */
object NoSelections : SelectionState

/**
 * Marker indicating exactly 1 selection has been made.
 */
object Has1Selection : SelectionState

/**
 * Marker indicating exactly 2 selections have been made.
 */
object Has2Selections : SelectionState

/**
 * Marker indicating exactly 3 selections have been made.
 */
object Has3Selections : SelectionState

/**
 * Marker indicating exactly 4 selections have been made.
 */
object Has4Selections : SelectionState

/**
 * Marker indicating exactly 5 selections have been made.
 */
object Has5Selections : SelectionState

/**
 * Marker indicating exactly 6 selections have been made.
 */
object Has6Selections : SelectionState

/**
 * Marker indicating exactly 7 selections have been made.
 */
object Has7Selections : SelectionState

/**
 * Marker indicating exactly 8 selections have been made.
 */
object Has8Selections : SelectionState

/**
 * Marker indicating exactly 9 selections have been made.
 */
object Has9Selections : SelectionState

/**
 * Marker indicating exactly 10 selections have been made.
 */
object Has10Selections : SelectionState
