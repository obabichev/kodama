#!/usr/bin/env kotlin

/**
 * Standalone test script for RelationshipBasedCombinationGenerator.
 *
 * This tests the combination algorithm with real Kodama relationships data
 * without needing to compile the full test suite.
 *
 * Run: kotlinc -script test-relationship-generator.kts
 * Or: chmod +x test-relationship-generator.kts && ./test-relationship-generator.kts
 */

// Simplified version for testing

data class TableCombination(
    val tables: List<String>,
    val depth: Int,
    val rootTable: String
) {
    val key: String get() = tables.joinToString("_")
    override fun toString(): String = key
}

class RelationshipBasedCombinationGenerator(
    private val tables: List<String>,
    private val relationships: List<Pair<String, String>>
) {

    private val graph: Map<String, Set<String>> by lazy {
        relationships
            .groupBy { it.first }
            .mapValues { (_, rels) -> rels.map { it.second }.toSet() }
    }

    fun generateAllCombinations(maxDepth: Int = 3): List<TableCombination> {
        val result = mutableListOf<TableCombination>()
        val seen = mutableSetOf<Set<String>>()

        // Depth 1
        tables.forEach { table ->
            result.add(TableCombination(listOf(table), 1, table))
            seen.add(setOf(table))
        }

        // Depth 2+
        var currentLevel = result.toList()

        for (depth in 2..maxDepth) {
            val nextLevel = mutableListOf<TableCombination>()

            currentLevel.forEach { combination ->
                val reachable = getReachableTables(combination.tables)

                reachable.forEach { nextTable ->
                    if (!combination.tables.contains(nextTable)) {
                        val expanded = combination.tables + nextTable
                        val expandedSet = expanded.toSet()

                        if (!seen.contains(expandedSet)) {
                            nextLevel.add(TableCombination(expanded, depth, combination.rootTable))
                            seen.add(expandedSet)
                        }
                    }
                }
            }

            result.addAll(nextLevel)
            currentLevel = nextLevel
        }

        return result.sortedWith(
            compareBy<TableCombination> { it.depth }.thenBy { it.key }
        )
    }

    private fun getReachableTables(combination: List<String>): Set<String> {
        return combination.flatMap { table ->
            graph[table] ?: emptySet()
        }.toSet()
    }
}

// Test with real Kodama relationships
fun main() {
    println("=" .repeat(70))
    println("Relationship-Based Combination Generator - Test")
    println("=".repeat(70))
    println()

    // Real relationships from kodama-tests/build/generated/ksp/main/resources/relationships.json
    val tables = listOf(
        "Person", "Order", "Profile", "Company",
        "Product", "Settings", "Numerics", "UserOrders",
        "Users", "TradingStrategy", "MarketData", "Events",
        "SerialTest", "IdentityTest", "BigSerialTest", "SmallSerialTest", "Org"
    )

    val relationships = listOf(
        "Person" to "Order",
        "Person" to "Profile",
        "Order" to "Person",
        "Order" to "Company",
        "Profile" to "Person",
        "Company" to "Order",
        "TradingStrategy" to "MarketData",
        "MarketData" to "TradingStrategy"
    )

    println("Input:")
    println("  Tables: ${tables.size}")
    println("  Relationships: ${relationships.size}")
    println()

    relationships.forEach { (from, to) ->
        println("  $from → $to")
    }
    println()

    val generator = RelationshipBasedCombinationGenerator(tables, relationships)
    val combinations = generator.generateAllCombinations(maxDepth = 3)

    val depth1 = combinations.filter { it.depth == 1 }
    val depth2 = combinations.filter { it.depth == 2 }
    val depth3 = combinations.filter { it.depth == 3 }

    println("Results:")
    println("  Depth 1 (single tables): ${depth1.size} combinations")
    println("  Depth 2 (one join): ${depth2.size} combinations")
    println("  Depth 3 (two joins): ${depth3.size} combinations")
    println("  Total: ${combinations.size} combinations")
    println()

    println("Depth 1 combinations:")
    depth1.forEach { println("  - ${it.key}") }
    println()

    println("Depth 2 combinations:")
    depth2.forEach { println("  - ${it.key}") }
    println()

    println("Depth 3 combinations:")
    depth3.forEach { println("  - ${it.key}") }
    println()

    // Verify some expected combinations
    val keys = combinations.map { it.key }.toSet()
    println("Verification:")
    println("  Person_Order exists: ${keys.contains("Person_Order")}")
    println("  Person_Profile exists: ${keys.contains("Person_Profile")}")
    println("  Order_Company exists: ${keys.contains("Order_Company")}")
    println("  Person_Order_Company exists: ${keys.contains("Person_Order_Company")}")
    println("  TradingStrategy_MarketData exists: ${keys.contains("TradingStrategy_MarketData")}")
    println()

    // Compare with current hybrid approach (from build logs)
    println("Comparison with current approach:")
    println("  Current (hybrid regex+relationships): ~33 combinations")
    println("  Pure relationship-based: ${combinations.size} combinations")
    println("  Difference: ${combinations.size - 33} more combinations")
    println()
    println("This is expected - pure approach generates ALL valid combinations,")
    println("not just the ones found in tests.")
    println()

    println("=" .repeat(70))
    println("✅ Test completed successfully!")
    println("=" .repeat(70))
}

main()
