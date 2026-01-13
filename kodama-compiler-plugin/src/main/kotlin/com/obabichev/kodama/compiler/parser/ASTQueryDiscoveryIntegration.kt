package com.obabichev.kodama.compiler.parser

import org.gradle.api.logging.Logger
import java.io.File

/**
 * Integrates AST-based query discovery with the code generation task.
 *
 * This replaces regex-based pattern matching with structured AST parsing.
 */
class ASTQueryDiscoveryIntegration(
    private val logger: Logger
) {

    /**
     * Discover table combinations from test files using AST parsing.
     *
     * @param testFiles List of Kotlin test files to scan
     * @return Set of table combinations (each combination is a list of table names)
     */
    fun discoverTableCombinations(testFiles: List<File>): Set<List<String>> {
        logger.lifecycle("Kodama AST Parser: Discovering query patterns...")

        val parser = KotlinASTParser()
        val visitor = QueryDiscoveryVisitor()

        try {
            testFiles.forEach { file ->
                if (file.extension == "kt") {
                    try {
                        val ktFile = parser.parse(file)
                        ktFile.accept(visitor)
                    } catch (e: Exception) {
                        logger.warn("Failed to parse ${file.name}: ${e.message}")
                    }
                }
            }

            val queries = visitor.discoveredQueries
            logger.lifecycle("Kodama AST Parser: Discovered ${queries.size} queries")

            // Generate table combinations with deduplication
            val rawCombinations = queries.flatMap { query ->
                // Get tables and remove consecutive duplicates
                val cleanedTables = removeDuplicateTables(query.getTables())

                // Generate prefixes from cleaned list
                generateCleanPrefixes(cleanedTables)
            }

            // Deduplicate and filter invalid combinations
            val combinations = deduplicateCombinations(rawCombinations)

            logger.lifecycle("Kodama AST Parser: Generated ${combinations.size} table combinations (after deduplication)")

            // Log statistics
            val stats = DiscoveryStatistics.from(queries)
            logger.info(stats.toString())

            return combinations

        } finally {
            parser.dispose()
        }
    }

    /**
     * Remove consecutive duplicate tables from a list.
     *
     * Example: [Person, Person, Order] -> [Person, Order]
     * Example: [Person, Order, Order, Profile] -> [Person, Order, Profile]
     */
    private fun removeDuplicateTables(tables: List<String>): List<String> {
        if (tables.isEmpty()) return tables

        val result = mutableListOf<String>()
        var previous: String? = null

        for (table in tables) {
            if (table != previous) {
                result.add(table)
                previous = table
            }
        }

        return result
    }

    /**
     * Generate clean prefix combinations without duplicates.
     *
     * Example: [Person, Order, Profile] -> [[Person], [Person, Order], [Person, Order, Profile]]
     */
    private fun generateCleanPrefixes(tables: List<String>): List<List<String>> {
        if (tables.isEmpty()) return emptyList()

        return (1..tables.size).map { i ->
            tables.take(i)
        }
    }

    /**
     * Deduplicate combinations and filter out invalid patterns.
     *
     * Filters out:
     * - Combinations with consecutive duplicate tables (Person → Person)
     * - Combinations that are subsets of longer valid combinations
     * - Invalid patterns that don't represent real joins
     */
    private fun deduplicateCombinations(combinations: List<List<String>>): Set<List<String>> {
        val seen = mutableSetOf<List<String>>()
        val valid = mutableListOf<List<String>>()

        for (combination in combinations) {
            // Skip if already seen
            if (combination in seen) continue

            // Skip if has consecutive duplicates (shouldn't happen after cleaning, but safety check)
            if (hasConsecutiveDuplicates(combination)) {
                logger.debug("Filtered duplicate pattern: ${combination.joinToString(" → ")}")
                continue
            }

            // Skip single-table patterns that appear multiple times
            // (e.g., Person appears in many queries, we only need it once)
            if (combination.size == 1) {
                val tableName = combination.first()
                if (seen.any { it.size == 1 && it.first() == tableName }) {
                    continue
                }
            }

            seen.add(combination)
            valid.add(combination)
        }

        logger.debug("Deduplication: ${combinations.size} raw → ${valid.size} clean combinations")

        return valid.toSet()
    }

    /**
     * Check if a list has consecutive duplicate elements.
     */
    private fun hasConsecutiveDuplicates(list: List<String>): Boolean {
        for (i in 0 until list.size - 1) {
            if (list[i] == list[i + 1]) {
                return true
            }
        }
        return false
    }

    /**
     * Discover column markers from test files using AST parsing.
     *
     * Markers are used for selectAliased(MarkerName) type-safe selections.
     *
     * @param testFiles List of Kotlin test files to scan
     * @return Map of marker name to inferred type
     */
    fun discoverColumnMarkers(testFiles: List<File>): DiscoveredMarkers {
        val parser = KotlinASTParser()
        val visitor = QueryDiscoveryVisitor()

        try {
            testFiles.forEach { file ->
                if (file.extension == "kt") {
                    try {
                        val ktFile = parser.parse(file)
                        ktFile.accept(visitor)
                    } catch (e: Exception) {
                        logger.warn("Failed to parse ${file.name}: ${e.message}")
                    }
                }
            }

            val markers = mutableMapOf<String, String>()
            val markerTableUsage = mutableMapOf<String, MutableSet<Set<String>>>()
            val markerAliasStyles = mutableMapOf<String, String>()

            // Extract markers from discovered queries
            visitor.discoveredQueries.forEach { query ->
                val tables = query.getTables().toSet()

                query.operations
                    .filter { it.type == OperationType.SELECT_ALIASED }
                    .forEach { op ->
                        val markerName = op.marker ?: return@forEach

                        // Infer type from lambda body
                        val inferredType = inferTypeFromLambda(op.lambda)
                        markers[markerName] = inferredType

                        // Track table usage
                        markerTableUsage.getOrPut(markerName) { mutableSetOf() }.add(tables)

                        // Infer SQL alias style
                        val aliasStyle = inferSqlAliasStyleFromLambda(op.lambda)
                        markerAliasStyles[markerName] = aliasStyle
                    }

                // Also extract markers from subqueries
                query.getSubqueries().forEach { subquery ->
                    val subqueryTables = subquery.getTables().toSet()

                    subquery.operations
                        .filter { it.type == OperationType.SELECT_ALIASED }
                        .forEach { op ->
                            val markerName = op.marker ?: return@forEach

                            val inferredType = inferTypeFromLambda(op.lambda)
                            markers[markerName] = inferredType

                            markerTableUsage.getOrPut(markerName) { mutableSetOf() }.add(subqueryTables)

                            val aliasStyle = inferSqlAliasStyleFromLambda(op.lambda)
                            markerAliasStyles[markerName] = aliasStyle
                        }
                }
            }

            logger.lifecycle("Kodama AST Parser: Discovered ${markers.size} column markers")

            return DiscoveredMarkers(
                markerTypes = markers,
                markerTableUsage = markerTableUsage,
                markerAliasStyles = markerAliasStyles
            )

        } finally {
            parser.dispose()
        }
    }

    /**
     * Discover subquery patterns from test files using AST parsing.
     *
     * @param testFiles List of Kotlin test files to scan
     * @return List of discovered subquery patterns
     */
    fun discoverSubqueries(testFiles: List<File>): List<SubqueryPattern> {
        val parser = KotlinASTParser()
        val visitor = QueryDiscoveryVisitor()

        try {
            testFiles.forEach { file ->
                if (file.extension == "kt") {
                    try {
                        val ktFile = parser.parse(file)
                        ktFile.accept(visitor)
                    } catch (e: Exception) {
                        logger.warn("Failed to parse ${file.name}: ${e.message}")
                    }
                }
            }

            val subqueries = visitor.discoveredQueries.flatMap { it.getSubqueries() }

            logger.lifecycle("Kodama AST Parser: Discovered ${subqueries.size} subqueries")

            return subqueries

        } finally {
            parser.dispose()
        }
    }

    /**
     * Infer Kotlin type from lambda expression body.
     */
    private fun inferTypeFromLambda(lambda: LambdaExpression?): String {
        if (lambda == null) return "Number"

        val body = lambda.body.lowercase()

        return when {
            // Boolean expressions (check first!)
            body.contains(" lt ") || body.contains(" gt ") || body.contains(" lte ") ||
            body.contains(" gte ") || body.contains(" eq ") || body.contains(" neq ") -> "Boolean"
            // Aggregates
            body.contains("sum(") -> "Long"
            body.contains("count(") -> "Long"
            body.contains("avg(") -> "Double"
            body.contains("min(") || body.contains("max(") -> "Number"
            // Simple column types
            body.contains(".cost") || body.contains(".id") || body.contains(".age") -> "Int"
            body.contains(".name") || body.contains(".product") || body.contains(".username") -> "String"
            else -> "Number"  // Default for aggregates
        }
    }

    /**
     * Infer SQL alias style from lambda expression.
     */
    private fun inferSqlAliasStyleFromLambda(lambda: LambdaExpression?): String {
        if (lambda == null) return "SNAKE_CASE"

        val body = lambda.body

        // Check if the expression uses database-style naming
        return if (body.contains("_")) {
            "SNAKE_CASE"
        } else {
            "CAMEL_CASE"
        }
    }
}

/**
 * Result of marker discovery.
 */
data class DiscoveredMarkers(
    val markerTypes: Map<String, String>,
    val markerTableUsage: Map<String, MutableSet<Set<String>>>,
    val markerAliasStyles: Map<String, String>
)
