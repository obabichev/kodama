package com.obabichev.kodama.compiler.parser

import org.jetbrains.kotlin.psi.*

/**
 * Walks Kotlin PSI tree to discover query patterns.
 *
 * Discovers:
 * - from() calls (query entry points)
 * - join() calls (table combinations)
 * - select()/selectAll() calls (column selections)
 * - Inline subquery patterns
 * - WHERE, GROUP BY, ORDER BY, LIMIT, OFFSET clauses
 *
 * Usage:
 * ```kotlin
 * val visitor = QueryDiscoveryVisitor()
 * ktFile.accept(visitor)
 * visitor.discoveredQueries.forEach { query ->
 *     println("Found query with tables: ${query.getTables()}")
 * }
 * ```
 */
class QueryDiscoveryVisitor : KtTreeVisitorVoid() {

    private val _discoveredQueries = mutableListOf<QueryPattern>()
    val discoveredQueries: List<QueryPattern> get() = _discoveredQueries

    override fun visitCallExpression(call: KtCallExpression) {
        // Check if this is a from() call
        val callName = call.calleeExpression?.text

        if (callName == "from") {
            // This is a query entry point
            val query = extractQueryPattern(call)
            if (query != null) {
                _discoveredQueries.add(query)
            }
        }

        // Continue traversing
        super.visitCallExpression(call)
    }

    /**
     * Extract full query pattern from a from() call.
     *
     * Walks the method chain: from().join().select().where()...
     */
    private fun extractQueryPattern(fromCall: KtCallExpression): QueryPattern? {
        val operations = mutableListOf<QueryOperation>()

        // Extract base table from from(Person)
        val baseTable = extractTableArgument(fromCall)
            ?: return null

        operations.add(QueryOperation(
            type = OperationType.FROM,
            table = baseTable,
            sourceNode = fromCall
        ))

        // Walk the chain to find all operations
        var current: KtExpression = fromCall
        while (true) {
            val parent = findMethodChainParent(current) ?: break

            val selector = parent.selectorExpression as? KtCallExpression
            if (selector != null) {
                val operation = extractOperation(selector)
                if (operation != null) {
                    operations.add(operation)
                }
            }

            current = parent
        }

        return QueryPattern(
            baseTable = baseTable,
            operations = operations
        )
    }

    /**
     * Extract operation from a call like join(), select(), where(), etc.
     */
    private fun extractOperation(call: KtCallExpression): QueryOperation? {
        val operationName = call.calleeExpression?.text ?: return null

        return when (operationName) {
            "join", "leftJoin", "rightJoin" -> extractJoinOperation(call, operationName)
            "joinAliased" -> extractJoinAliasedOperation(call)
            "select" -> extractSelectOperation(call)
            "selectAll" -> extractSelectAllOperation(call)
            "selectAliased", "selectAs" -> extractSelectAliasedOperation(call)
            "where" -> extractWhereOperation(call)
            "groupBy" -> extractGroupByOperation(call)
            "orderBy" -> extractOrderByOperation(call)
            "limit" -> extractLimitOperation(call)
            "offset" -> extractOffsetOperation(call)
            else -> null
        }
    }

    private fun extractJoinOperation(call: KtCallExpression, joinMethod: String): QueryOperation? {
        val table = extractTableArgument(call) ?: return null
        val condition = extractLambdaArgument(call)

        return QueryOperation(
            type = OperationType.JOIN,
            table = table,
            joinType = joinMethod.toJoinType(),
            condition = condition,
            sourceNode = call
        )
    }

    private fun extractJoinAliasedOperation(call: KtCallExpression): QueryOperation? {
        // Subquery join: .joinAliased(subquery) { condition }
        val subquery = extractSubqueryArgument(call)
        val condition = extractLambdaArgument(call)

        return QueryOperation(
            type = OperationType.JOIN_SUBQUERY,
            subquery = subquery,
            condition = condition,
            sourceNode = call
        )
    }

    private fun extractSelectOperation(call: KtCallExpression): QueryOperation {
        val lambda = extractLambdaArgument(call)

        return QueryOperation(
            type = OperationType.SELECT,
            lambda = lambda,
            sourceNode = call
        )
    }

    private fun extractSelectAllOperation(call: KtCallExpression): QueryOperation? {
        val table = extractTableArgument(call) ?: return null

        return QueryOperation(
            type = OperationType.SELECT_ALL,
            table = table,
            sourceNode = call
        )
    }

    private fun extractSelectAliasedOperation(call: KtCallExpression): QueryOperation? {
        // Extract marker from selectAliased(TotalRevenue) or selectAs(TotalRevenue)
        val marker = extractFirstArgument(call) ?: return null
        val lambda = extractLambdaArgument(call)

        return QueryOperation(
            type = OperationType.SELECT_ALIASED,
            marker = marker,
            lambda = lambda,
            sourceNode = call
        )
    }

    private fun extractWhereOperation(call: KtCallExpression): QueryOperation {
        val condition = extractLambdaArgument(call)

        return QueryOperation(
            type = OperationType.WHERE,
            condition = condition,
            sourceNode = call
        )
    }

    private fun extractGroupByOperation(call: KtCallExpression): QueryOperation {
        val lambda = extractLambdaArgument(call)

        return QueryOperation(
            type = OperationType.GROUP_BY,
            lambda = lambda,
            sourceNode = call
        )
    }

    private fun extractOrderByOperation(call: KtCallExpression): QueryOperation {
        val lambda = extractLambdaArgument(call)

        return QueryOperation(
            type = OperationType.ORDER_BY,
            lambda = lambda,
            sourceNode = call
        )
    }

    private fun extractLimitOperation(call: KtCallExpression): QueryOperation? {
        val value = extractIntArgument(call) ?: return null

        return QueryOperation(
            type = OperationType.LIMIT,
            intValue = value,
            sourceNode = call
        )
    }

    private fun extractOffsetOperation(call: KtCallExpression): QueryOperation? {
        val value = extractIntArgument(call) ?: return null

        return QueryOperation(
            type = OperationType.OFFSET,
            intValue = value,
            sourceNode = call
        )
    }

    /**
     * Extract table name from from(Person) or join(Order).
     */
    private fun extractTableArgument(call: KtCallExpression): String? {
        val arg = call.valueArguments.firstOrNull() ?: return null
        return (arg.getArgumentExpression() as? KtNameReferenceExpression)?.text
    }

    /**
     * Extract first argument as string (for markers like TotalRevenue).
     */
    private fun extractFirstArgument(call: KtCallExpression): String? {
        val arg = call.valueArguments.firstOrNull() ?: return null
        return (arg.getArgumentExpression() as? KtNameReferenceExpression)?.text
    }

    /**
     * Extract lambda from { order.userName eq person.name }.
     */
    private fun extractLambdaArgument(call: KtCallExpression): LambdaExpression? {
        val lambda = call.lambdaArguments.firstOrNull()?.getLambdaExpression()
            ?: call.valueArguments.lastOrNull()?.getArgumentExpression() as? KtLambdaExpression
            ?: return null

        return LambdaExpression(
            parameters = lambda.valueParameters.map { it.text },
            body = lambda.bodyExpression?.text ?: "",
            sourceNode = lambda
        )
    }

    /**
     * Extract integer argument from limit(10) or offset(5).
     */
    private fun extractIntArgument(call: KtCallExpression): Int? {
        val arg = call.valueArguments.firstOrNull()?.getArgumentExpression()
        return when (arg) {
            is KtConstantExpression -> arg.text.toIntOrNull()
            else -> null
        }
    }

    /**
     * Extract subquery from inline definition.
     *
     * Pattern: from(Order).select{...}.build().aliasAs<UserTotals>()
     */
    private fun extractSubqueryArgument(call: KtCallExpression): SubqueryPattern? {
        val arg = call.valueArguments.firstOrNull()?.getArgumentExpression()
            ?: return null

        // Check if it's a subquery pattern (ends with .aliasAs<T>() or .build())
        return extractSubqueryFromExpression(arg)
    }

    /**
     * Recursively extract subquery pattern from expression.
     */
    private fun extractSubqueryFromExpression(expr: KtExpression): SubqueryPattern? {
        if (expr !is KtQualifiedExpression) return null

        val selector = expr.selectorExpression as? KtCallExpression ?: return null
        val selectorName = selector.calleeExpression?.text

        when (selectorName) {
            "aliasAs" -> {
                // Extract type argument: aliasAs<UserTotals>()
                val typeArg = selector.typeArguments.firstOrNull()?.typeReference?.text
                    ?: return null

                // Extract the query chain before .aliasAs()
                val queryChain = expr.receiverExpression
                val subqueryOps = extractSubqueryOperations(queryChain)

                return SubqueryPattern(
                    alias = typeArg,
                    operations = subqueryOps,
                    sourceNode = expr
                )
            }

            "build" -> {
                // .build().aliasAs<T>() pattern
                // Check parent for aliasAs
                val parent = findMethodChainParent(expr)
                if (parent != null) {
                    return extractSubqueryFromExpression(parent)
                }
            }
        }

        return null
    }

    /**
     * Extract operations from subquery chain.
     */
    private fun extractSubqueryOperations(expr: KtExpression): List<QueryOperation> {
        val operations = mutableListOf<QueryOperation>()

        var current = expr
        while (current is KtQualifiedExpression) {
            val selector = current.selectorExpression as? KtCallExpression
            if (selector != null) {
                val op = extractOperation(selector)
                if (op != null) {
                    operations.add(0, op) // Prepend to maintain order
                }
            }

            current = current.receiverExpression
        }

        // Add base FROM operation if we find a from() call
        if (current is KtCallExpression && current.calleeExpression?.text == "from") {
            val baseTable = extractTableArgument(current)
            if (baseTable != null) {
                operations.add(0, QueryOperation(
                    type = OperationType.FROM,
                    table = baseTable,
                    sourceNode = current
                ))
            }
        }

        return operations
    }

    /**
     * Find parent qualified expression for method chaining.
     *
     * This walks up the tree to find: receiver.method() where receiver is the current expression.
     */
    private fun findMethodChainParent(element: KtExpression): KtQualifiedExpression? {
        var parent = element.parent

        while (parent != null) {
            if (parent is KtQualifiedExpression) {
                // Check if current element is the receiver
                if (parent.receiverExpression == element) {
                    return parent
                }
            }
            parent = parent.parent
        }

        return null
    }

    private fun String.toJoinType(): JoinType {
        return when (this) {
            "join" -> JoinType.INNER
            "leftJoin" -> JoinType.LEFT
            "rightJoin" -> JoinType.RIGHT
            else -> JoinType.INNER
        }
    }
}
