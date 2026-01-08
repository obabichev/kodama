package com.obabichev.kodama.query

import com.obabichev.kodama.schema.Table

/**
 * Type-level constraint that enforces valid joins between tables.
 *
 * This interface is used as a type bound on join operations to ensure
 * that only tables with declared relationships can be joined together.
 *
 * **How it works:**
 *
 * 1. When a relationship is declared between two tables (e.g., Person → Order),
 *    the code generator creates an instance like: `object PersonCanJoinOrder : CanJoin<Person, Order>`
 *
 * 2. The join() method requires a CanJoin<From, To> constraint:
 *    ```kotlin
 *    fun <From, To> join(target: To) where CanJoin<From, To> : Any
 *    ```
 *
 * 3. If you try to join unrelated tables, the compiler will produce an error because
 *    no CanJoin<From, To> instance exists.
 *
 * **Example:**
 *
 * ```kotlin
 * // Declared relationship:
 * object Person : Table("person") {
 *     val orders = oneToMany(Order, Order.userName, this.name)
 * }
 *
 * // Generated:
 * object PersonCanJoinOrder : CanJoin<Person, Order>
 *
 * // Valid query (compiles):
 * from(Person).join(Order) { ... }  // ✅ OK: PersonCanJoinOrder exists
 *
 * // Invalid query (compile error):
 * from(Person).join(UnrelatedTable) { ... }  // ❌ ERROR: No CanJoin<Person, UnrelatedTable>
 * ```
 *
 * @param From The source table type
 * @param To The target table type that can be joined from From
 */
interface CanJoin<From : Table, To : Table>

/**
 * Marker interface for tables that can be joined in the current query context.
 * This is used as a type-level set to track which tables are available for selection.
 *
 * Example:
 * ```kotlin
 * // After: from(Person).join(Order)
 * // Available tables: Contains<Person> & Contains<Order>
 * ```
 */
interface Contains<T : Table>

/**
 * Type-level union of Contains markers.
 * Represents multiple tables being available in the query context.
 */
interface TableSet {
    interface None : TableSet
    interface Single<T : Table> : TableSet, Contains<T>
    interface Union<A : TableSet, B : TableSet> : TableSet
}

/**
 * Helper to combine TableSet types.
 * This allows building up the set of available tables as joins are added.
 */
inline fun <reified A : TableSet, reified B : TableSet> unionTableSets(): TableSet.Union<A, B> {
    throw UnsupportedOperationException("This is a compile-time only construct")
}
