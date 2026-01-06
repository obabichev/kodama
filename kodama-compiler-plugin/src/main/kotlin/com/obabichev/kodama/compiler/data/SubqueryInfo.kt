package com.obabichev.kodama.compiler.data

/**
 * Metadata for a subquery definition.
 *
 * Example:
 * ```
 * SubqueryInfo(
 *     name = "ExpensiveOrders",
 *     sqlAlias = "expensive_orders",
 *     columns = listOf(
 *         SubqueryColumnInfo("userName", "user_name", "String", false, false),
 *         SubqueryColumnInfo("product", "product", "String", false, false)
 *     ),
 *     sourceTables = listOf("Order")
 * )
 * ```
 */
data class SubqueryInfo(
    /**
     * Subquery name in PascalCase (e.g., "ExpensiveOrders", "UserTotals")
     * This becomes the marker interface name.
     */
    val name: String,

    /**
     * SQL alias for the subquery in snake_case (e.g., "expensive_orders", "user_totals")
     */
    val sqlAlias: String,

    /**
     * Columns in the subquery result
     */
    val columns: List<SubqueryColumnInfo>,

    /**
     * Source tables involved in this subquery (e.g., ["Order"], ["Person", "Order"])
     */
    val sourceTables: List<String> = emptyList()
) {
    /**
     * SubqueryTable class name.
     * Example: "SubqueryTable_ExpensiveOrders"
     */
    val subqueryTableClassName: String
        get() = "SubqueryTable_$name"

    /**
     * Accessor class name.
     * Example: "ExpensiveOrdersAccessor"
     */
    val accessorClassName: String
        get() = "${name}Accessor"

    /**
     * Result accessor class name.
     * Example: "ExpensiveOrdersResultAccessor"
     */
    val resultAccessorClassName: String
        get() = "${name}ResultAccessor"

    /**
     * Query result class name.
     * Example: "QueryResult_ExpensiveOrders"
     */
    val queryResultClassName: String
        get() = "QueryResult_$name"

    /**
     * Builder class name for this subquery.
     * Example: "AfterFromQueryBuilder_ExpensiveOrders"
     */
    val builderClassName: String
        get() = "AfterFromQueryBuilder_$name"

    /**
     * SelectAllContext class name.
     * Example: "SelectAllContext_ExpensiveOrders"
     */
    val selectAllContextClassName: String
        get() = "SelectAllContext_$name"
}
