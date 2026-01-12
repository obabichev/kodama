package com.obabichev.kodama.annotations

/**
 * Marks an interface as a selection marker for type-safe query result access.
 *
 * Selection markers are empty interfaces used with `selectAs<T>()` to provide
 * named, type-safe access to query results.
 *
 * Example:
 * ```kotlin
 * @Marker
 * interface TotalRevenue
 *
 * // Use in query
 * from(Order)
 *     .selectAs(TotalRevenue) { sum(order.cost) }
 *     .execute(tx)
 *     .forEach { row ->
 *         val total = row.totalRevenue  // Type-safe access
 *     }
 * ```
 *
 * This annotation is optional. The code generator can also discover empty interfaces
 * automatically, but using @Marker makes the intent explicit.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)  // Only needed at compile time for KSP
annotation class Marker
