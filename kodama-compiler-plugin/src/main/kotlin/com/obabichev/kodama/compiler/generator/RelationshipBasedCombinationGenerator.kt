package com.obabichev.kodama.compiler.generator

/**
 * Pure relationship-based table combination generator.
 *
 * Generates all valid table join combinations from declared relationships,
 * eliminating the need for regex-based pattern discovery in test files.
 *
 * **Input:** Declared relationships between tables (from relationships.json)
 * **Output:** All valid table combinations up to maxDepth
 *
 * **Example:**
 * ```
 * Relationships:
 *   Person → Order
 *   Person → Profile
 *   Order → Company
 *
 * Generated Combinations:
 *   Depth 1: [Person], [Order], [Profile], [Company]
 *   Depth 2: [Person, Order], [Person, Profile], [Order, Company]
 *   Depth 3: [Person, Order, Company]
 * ```
 *
 * **Benefits:**
 * - Zero regex patterns (was 41)
 * - Deterministic generation (same relationships → same output)
 * - Faster builds (no file scanning)
 * - Predictable code size
 *
 * @param tables List of all table names in the schema
 * @param relationships List of (from, to) relationship pairs
 */
class RelationshipBasedCombinationGenerator(
    private val tables: List<String>,
    private val relationships: List<Pair<String, String>>
) {

    /**
     * Relationship graph for fast reachability queries.
     * Maps each table to the set of tables it can join to.
     */
    private val graph: Map<String, Set<String>> by lazy {
        buildRelationshipGraph()
    }

    /**
     * Generate all valid table combinations up to maxDepth.
     *
     * **Strategy:**
     * 1. Depth 1: Each table as standalone ([Person], [Order], ...)
     * 2. Depth 2+: For each N-table combination, try adding reachable tables
     * 3. Stop at maxDepth (typically 3-4 tables)
     *
     * **Example Progression:**
     * ```
     * [Person]
     *   → [Person, Order]      (via Person → Order)
     *     → [Person, Order, Company]  (via Order → Company)
     *   → [Person, Profile]    (via Person → Profile)
     * ```
     *
     * @param maxDepth Maximum number of tables in a combination (default: 3)
     * @return List of all valid table combinations, sorted by size
     */
    fun generateAllCombinations(maxDepth: Int = 3): List<TableCombination> {
        require(maxDepth >= 1) { "maxDepth must be >= 1" }

        val result = mutableListOf<TableCombination>()
        val seen = mutableSetOf<Set<String>>()  // Dedup using set

        // Depth 1: Each table standalone
        tables.forEach { table ->
            val combo = TableCombination(
                tables = listOf(table),
                depth = 1,
                rootTable = table
            )
            result.add(combo)
            seen.add(setOf(table))
        }

        // Depth 2+: Expand combinations breadth-first
        var currentLevel = result.toList()

        for (depth in 2..maxDepth) {
            val nextLevel = mutableListOf<TableCombination>()

            currentLevel.forEach { combination ->
                // Try adding each reachable table
                val reachable = getReachableTables(combination.tables)

                reachable.forEach { nextTable ->
                    // Skip if already in combination
                    if (!combination.tables.contains(nextTable)) {
                        val expanded = combination.tables + nextTable
                        val expandedSet = expanded.toSet()

                        // Skip if we've already generated this combination
                        if (!seen.contains(expandedSet)) {
                            val newCombo = TableCombination(
                                tables = expanded,
                                depth = depth,
                                rootTable = combination.rootTable
                            )
                            nextLevel.add(newCombo)
                            seen.add(expandedSet)
                        }
                    }
                }
            }

            result.addAll(nextLevel)
            currentLevel = nextLevel
        }

        return result.sortedWith(
            compareBy<TableCombination> { it.depth }
                .thenBy { it.tables.joinToString("_") }
        )
    }

    /**
     * Generate combinations for specific root tables only.
     * Useful for incremental generation or testing.
     *
     * @param rootTables Tables to start from
     * @param maxDepth Maximum depth per root
     * @return Combinations starting from the specified roots
     */
    fun generateCombinationsFrom(
        rootTables: List<String>,
        maxDepth: Int = 3
    ): List<TableCombination> {
        val tempGenerator = RelationshipBasedCombinationGenerator(
            tables = rootTables,
            relationships = relationships
        )
        return tempGenerator.generateAllCombinations(maxDepth)
    }

    /**
     * Get statistics about the relationship graph.
     * Useful for debugging and optimization.
     */
    fun getStatistics(): GenerationStatistics {
        return GenerationStatistics(
            totalTables = tables.size,
            totalRelationships = relationships.size,
            tablesWithRelationships = graph.keys.size,
            averageDegree = if (graph.isNotEmpty()) {
                graph.values.map { it.size }.average()
            } else 0.0,
            maxDegree = graph.values.maxOfOrNull { it.size } ?: 0
        )
    }

    /**
     * Build adjacency list representation of relationship graph.
     * Maps each table to the set of tables it can directly join to.
     */
    private fun buildRelationshipGraph(): Map<String, Set<String>> {
        return relationships
            .groupBy { it.first }
            .mapValues { (_, rels) -> rels.map { it.second }.toSet() }
    }

    /**
     * Get all tables reachable from any table in the given combination.
     * Uses direct relationships only (no transitive closure in this method).
     *
     * @param combination Current table combination
     * @return Set of tables that can be joined next
     */
    private fun getReachableTables(combination: List<String>): Set<String> {
        return combination.flatMap { table ->
            graph[table] ?: emptySet()
        }.toSet()
    }
}

/**
 * Represents a valid table combination generated from relationships.
 *
 * @property tables List of table names in join order
 * @property depth Number of tables (1 = single table, 2 = one join, etc.)
 * @property rootTable The first table in the combination (the FROM table)
 */
data class TableCombination(
    val tables: List<String>,
    val depth: Int,
    val rootTable: String
) {
    /**
     * Unique key for this combination.
     * Example: "Person_Order_Company"
     */
    val key: String
        get() = tables.joinToString("_")

    /**
     * Check if this combination contains a specific table.
     */
    fun contains(table: String): Boolean = tables.contains(table)

    /**
     * Get the join chain as pairs: (table, joinType)
     * First table has no join type (it's the FROM table).
     *
     * Example: [("Person", null), ("Order", "INNER"), ("Company", "INNER")]
     */
    fun toJoinChain(defaultJoinType: String = "INNER"): List<Pair<String, String?>> {
        return tables.mapIndexed { index, table ->
            if (index == 0) {
                table to null  // FROM table has no join type
            } else {
                table to defaultJoinType
            }
        }
    }

    override fun toString(): String = key
}

/**
 * Statistics about relationship graph and generation.
 */
data class GenerationStatistics(
    val totalTables: Int,
    val totalRelationships: Int,
    val tablesWithRelationships: Int,
    val averageDegree: Double,
    val maxDegree: Int
) {
    override fun toString(): String = """
        |Generation Statistics:
        |  Total tables: $totalTables
        |  Total relationships: $totalRelationships
        |  Tables with relationships: $tablesWithRelationships
        |  Average degree: ${"%.2f".format(averageDegree)}
        |  Max degree: $maxDegree
    """.trimMargin()
}
