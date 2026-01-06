package com.obabichev.kodama.compiler.data

/**
 * Represents a combination of markers used together in a query.
 *
 * When a query uses multiple selectAs() calls with different markers,
 * those markers form a combination that needs its own SelectionResult class
 * and execute() method.
 *
 * Example:
 * ```
 * from(Order)
 *     .selectAs(TotalRevenue) { sum(order.cost) }
 *     .selectAs(OrderCount) { count(order.id) }
 *     .execute(transaction)
 * ```
 *
 * This creates a MarkerCombinationInfo with markers: [TotalRevenue, OrderCount]
 * and generates:
 * - SelectionResult_TotalRevenue_OrderCount class
 * - execute() method returning List<SelectionResult_TotalRevenue_OrderCount>
 */
data class MarkerCombinationInfo(
    /**
     * The markers in this combination, in order.
     * Order matters for result class property ordering.
     */
    val markers: List<MarkerInfo>
) {
    /**
     * Result class name for this marker combination.
     * Example: "SelectionResult_TotalRevenue_OrderCount"
     */
    val resultClassName: String
        get() = "SelectionResult_" + markers.joinToString("_") { it.interfaceName }

    /**
     * Aggregate count type for this combination.
     * Example: "Has2Aggregates" for 2 markers
     */
    val aggregateCountType: String
        get() = when (markers.size) {
            1 -> "Has1Aggregate"
            2 -> "Has2Aggregates"
            3 -> "Has3Aggregates"
            4 -> "Has4Aggregates"
            5 -> "Has5Aggregates"
            else -> "Has5Aggregates"  // Cap at 5
        }

    /**
     * Key for deduplication - sorted marker names.
     * Two combinations with same markers in different order are considered equal.
     */
    val combinationKey: String
        get() = markers.map { it.interfaceName }.sorted().joinToString("_")
}
