package com.obabichev.kodama.compiler.parser

import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression

/**
 * Represents a discovered query pattern from AST analysis.
 *
 * Contains the base table and all operations in the query chain.
 */
data class QueryPattern(
    val baseTable: String,
    val operations: List<QueryOperation>
) {
    /**
     * Get all tables involved in this query (including base and joined tables).
     */
    fun getTables(): List<String> {
        return listOf(baseTable) + operations.mapNotNull { it.table }
    }

    /**
     * Check if query contains subqueries.
     */
    fun hasSubqueries(): Boolean {
        return operations.any { it.type == OperationType.JOIN_SUBQUERY }
    }

    /**
     * Get all subqueries in this query.
     */
    fun getSubqueries(): List<SubqueryPattern> {
        return operations.mapNotNull { it.subquery }
    }

    /**
     * Get join operations.
     */
    fun getJoins(): List<QueryOperation> {
        return operations.filter { it.type == OperationType.JOIN }
    }

    /**
     * Get selection operations.
     */
    fun getSelections(): List<QueryOperation> {
        return operations.filter {
            it.type in setOf(
                OperationType.SELECT,
                OperationType.SELECT_ALL,
                OperationType.SELECT_ALIASED
            )
        }
    }

    /**
     * Build table combination key (e.g., "Person_Order_Company").
     */
    fun buildCombinationKey(): String {
        return getTables().joinToString("_")
    }

    /**
     * Generate all prefix combinations for this query.
     *
     * For query from(Person).join(Order).join(Company):
     * Returns: ["Person", "Person_Order", "Person_Order_Company"]
     */
    fun generatePrefixCombinations(): List<String> {
        val tables = getTables()
        return (1..tables.size).map { i ->
            tables.take(i).joinToString("_")
        }
    }
}

/**
 * Represents a single operation in a query chain.
 */
data class QueryOperation(
    val type: OperationType,
    val table: String? = null,
    val joinType: JoinType? = null,
    val condition: LambdaExpression? = null,
    val lambda: LambdaExpression? = null,
    val marker: String? = null,
    val intValue: Int? = null,
    val subquery: SubqueryPattern? = null,
    val sourceNode: KtElement
)

/**
 * Represents a lambda expression extracted from AST.
 */
data class LambdaExpression(
    val parameters: List<String>,
    val body: String,
    val sourceNode: KtLambdaExpression
) {
    /**
     * Check if lambda is empty.
     */
    fun isEmpty(): Boolean = body.isBlank()

    /**
     * Get lambda signature (parameters only).
     */
    fun getSignature(): String {
        return if (parameters.isEmpty()) {
            "{ }"
        } else {
            "{ ${parameters.joinToString(", ")} -> ... }"
        }
    }
}

/**
 * Represents an inline subquery pattern.
 *
 * Example: from(Order).select{...}.build().aliasAs<UserTotals>()
 */
data class SubqueryPattern(
    val alias: String,
    val operations: List<QueryOperation>,
    val sourceNode: KtElement
) {
    /**
     * Get base table of subquery.
     */
    fun getBaseTable(): String? {
        return operations.firstOrNull { it.type == OperationType.FROM }?.table
    }

    /**
     * Get all tables in subquery.
     */
    fun getTables(): List<String> {
        return operations.mapNotNull { it.table }
    }

    /**
     * Get selections in subquery.
     */
    fun getSelections(): List<QueryOperation> {
        return operations.filter {
            it.type in setOf(
                OperationType.SELECT,
                OperationType.SELECT_ALL,
                OperationType.SELECT_ALIASED
            )
        }
    }

    /**
     * Check if subquery is an aggregate query.
     */
    fun isAggregate(): Boolean {
        return operations.any { it.type == OperationType.GROUP_BY }
    }

    /**
     * Build combination key for this subquery.
     */
    fun buildCombinationKey(): String {
        val tables = getTables()
        return if (tables.isNotEmpty()) {
            tables.joinToString("_") + "_$alias"
        } else {
            alias
        }
    }
}

/**
 * Types of operations in a query chain.
 */
enum class OperationType {
    FROM,           // from(Person)
    JOIN,           // join(Order) { ... }
    JOIN_SUBQUERY,  // joinAliased(subquery) { ... }
    SELECT,         // select { person.name }
    SELECT_ALL,     // selectAll(Person)
    SELECT_ALIASED, // selectAliased(TotalRevenue) { sum(order.cost) }
    WHERE,          // where { person.age eq 25 }
    GROUP_BY,       // groupBy { order.userName }
    ORDER_BY,       // orderBy { person.name.asc() }
    LIMIT,          // limit(10)
    OFFSET          // offset(5)
}

/**
 * Types of SQL joins.
 */
enum class JoinType {
    INNER,  // join()
    LEFT,   // leftJoin()
    RIGHT,  // rightJoin()
    FULL    // fullJoin() (future)
}

/**
 * Statistics about discovered queries.
 */
data class DiscoveryStatistics(
    val totalQueries: Int,
    val queriesWithJoins: Int,
    val queriesWithSubqueries: Int,
    val uniqueTables: Set<String>,
    val uniqueCombinations: Set<String>,
    val subqueryAliases: Set<String>
) {
    override fun toString(): String {
        return """
            Query Discovery Statistics:
              Total queries: $totalQueries
              Queries with joins: $queriesWithJoins
              Queries with subqueries: $queriesWithSubqueries
              Unique tables: ${uniqueTables.size} (${uniqueTables.joinToString(", ")})
              Unique combinations: ${uniqueCombinations.size}
              Subquery aliases: ${subqueryAliases.size} (${subqueryAliases.joinToString(", ")})
        """.trimIndent()
    }

    companion object {
        fun from(queries: List<QueryPattern>): DiscoveryStatistics {
            return DiscoveryStatistics(
                totalQueries = queries.size,
                queriesWithJoins = queries.count { it.getJoins().isNotEmpty() },
                queriesWithSubqueries = queries.count { it.hasSubqueries() },
                uniqueTables = queries.flatMap { it.getTables() }.toSet(),
                uniqueCombinations = queries.flatMap { it.generatePrefixCombinations() }.toSet(),
                subqueryAliases = queries.flatMap { it.getSubqueries() }.map { it.alias }.toSet()
            )
        }
    }
}
