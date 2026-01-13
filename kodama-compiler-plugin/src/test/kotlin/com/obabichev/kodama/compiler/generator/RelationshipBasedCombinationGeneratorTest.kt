package com.obabichev.kodama.compiler.generator

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for RelationshipBasedCombinationGenerator.
 *
 * Validates that the pure relationship-based approach generates
 * correct table combinations from declared relationships.
 */
class RelationshipBasedCombinationGeneratorTest {

    @Test
    fun `test simple linear chain - Person to Order to Company`() {
        // Given: Linear relationship chain
        val tables = listOf("Person", "Order", "Company")
        val relationships = listOf(
            "Person" to "Order",
            "Order" to "Company"
        )

        val generator = RelationshipBasedCombinationGenerator(tables, relationships)

        // When: Generate combinations up to depth 3
        val combinations = generator.generateAllCombinations(maxDepth = 3)

        // Then: Should generate all valid combinations
        val keys = combinations.map { it.key }.toSet()

        // Depth 1: Each table standalone
        assertTrue(keys.contains("Person"))
        assertTrue(keys.contains("Order"))
        assertTrue(keys.contains("Company"))

        // Depth 2: Direct relationships
        assertTrue(keys.contains("Person_Order"))
        assertTrue(keys.contains("Order_Company"))

        // Depth 3: Transitive relationship
        assertTrue(keys.contains("Person_Order_Company"))

        // Should NOT contain invalid combinations
        assertFalse(keys.contains("Person_Company"))  // No direct relationship

        println("Generated ${combinations.size} combinations:")
        combinations.forEach { println("  - ${it.key} (depth=${it.depth}, root=${it.rootTable})") }
    }

    @Test
    fun `test star pattern - Person has many relationships`() {
        // Given: Person has relationships to Order and Profile
        val tables = listOf("Person", "Order", "Profile")
        val relationships = listOf(
            "Person" to "Order",
            "Person" to "Profile"
        )

        val generator = RelationshipBasedCombinationGenerator(tables, relationships)

        // When
        val combinations = generator.generateAllCombinations(maxDepth = 3)
        val keys = combinations.map { it.key }.toSet()

        // Then
        assertTrue(keys.contains("Person"))
        assertTrue(keys.contains("Order"))
        assertTrue(keys.contains("Profile"))
        assertTrue(keys.contains("Person_Order"))
        assertTrue(keys.contains("Person_Profile"))

        // Should also have Person -> Order + Person -> Profile (both branches)
        assertTrue(keys.contains("Person_Order_Profile") || keys.contains("Person_Profile_Order"))

        println("\nStar pattern generated ${combinations.size} combinations:")
        combinations.forEach { println("  - ${it.key}") }
    }

    @Test
    fun `test bidirectional relationships - Person and Order`() {
        // Given: Person → Order AND Order → Person (many-to-one / one-to-many)
        val tables = listOf("Person", "Order")
        val relationships = listOf(
            "Person" to "Order",  // Person.orders (one-to-many)
            "Order" to "Person"   // Order.person (many-to-one)
        )

        val generator = RelationshipBasedCombinationGenerator(tables, relationships)

        // When
        val combinations = generator.generateAllCombinations(maxDepth = 2)
        val keys = combinations.map { it.key }.toSet()

        // Then: Should generate both directions
        assertTrue(keys.contains("Person_Order"))  // from(Person).join(Order)
        assertTrue(keys.contains("Order_Person"))  // from(Order).join(Person)

        println("\nBidirectional relationships generated ${combinations.size} combinations:")
        combinations.forEach { println("  - ${it.key}") }
    }

    @Test
    fun `test real Kodama relationships from relationships json`() {
        // Given: Real relationships from kodama-tests
        val tables = listOf(
            "Person", "Order", "Profile", "Company",
            "TradingStrategy", "MarketData"
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

        val generator = RelationshipBasedCombinationGenerator(tables, relationships)

        // When: Generate combinations up to depth 3
        val combinations = generator.generateAllCombinations(maxDepth = 3)

        // Then: Should generate reasonable number of combinations
        val depth1 = combinations.filter { it.depth == 1 }
        val depth2 = combinations.filter { it.depth == 2 }
        val depth3 = combinations.filter { it.depth == 3 }

        println("\nReal Kodama relationships:")
        println("  Depth 1 (single tables): ${depth1.size} combinations")
        println("  Depth 2 (one join): ${depth2.size} combinations")
        println("  Depth 3 (two joins): ${depth3.size} combinations")
        println("  Total: ${combinations.size} combinations")

        // Verify some expected combinations exist
        val keys = combinations.map { it.key }.toSet()
        assertTrue(keys.contains("Person_Order"))
        assertTrue(keys.contains("Person_Profile"))
        assertTrue(keys.contains("Order_Company"))
        assertTrue(keys.contains("Person_Order_Company"))  // Transitive

        println("\nAll combinations:")
        combinations.forEach { println("  - ${it.key} (depth=${it.depth})") }

        // Print statistics
        val stats = generator.getStatistics()
        println("\n$stats")
    }

    @Test
    fun `test maxDepth limiting`() {
        // Given: Chain that could go deep
        val tables = listOf("A", "B", "C", "D")
        val relationships = listOf(
            "A" to "B",
            "B" to "C",
            "C" to "D"
        )

        val generator = RelationshipBasedCombinationGenerator(tables, relationships)

        // When: Limit to depth 2
        val combinations = generator.generateAllCombinations(maxDepth = 2)

        // Then: Should not have 3+ table combinations
        val maxDepth = combinations.maxOfOrNull { it.depth } ?: 0
        assertEquals(2, maxDepth)

        assertFalse(combinations.any { it.key == "A_B_C" })
        assertFalse(combinations.any { it.key == "A_B_C_D" })

        println("\nMax depth 2 generated ${combinations.size} combinations:")
        combinations.forEach { println("  - ${it.key}") }
    }

    @Test
    fun `test no relationships - only single tables`() {
        // Given: Tables with no relationships
        val tables = listOf("Person", "Order", "Profile")
        val relationships = emptyList<Pair<String, String>>()

        val generator = RelationshipBasedCombinationGenerator(tables, relationships)

        // When
        val combinations = generator.generateAllCombinations(maxDepth = 3)

        // Then: Should only have single-table combinations
        assertEquals(3, combinations.size)
        assertTrue(combinations.all { it.depth == 1 })

        println("\nNo relationships generated ${combinations.size} combinations:")
        combinations.forEach { println("  - ${it.key}") }
    }

    @Test
    fun `test join chain generation`() {
        // Given
        val tables = listOf("Person", "Order", "Company")
        val relationships = listOf(
            "Person" to "Order",
            "Order" to "Company"
        )

        val generator = RelationshipBasedCombinationGenerator(tables, relationships)
        val combinations = generator.generateAllCombinations(maxDepth = 3)

        // When: Get the Person -> Order -> Company combination
        val combo = combinations.find { it.key == "Person_Order_Company" }
        assertNotNull(combo)

        val joinChain = combo!!.toJoinChain()

        // Then: Should have proper join chain
        assertEquals(3, joinChain.size)
        assertEquals("Person" to null, joinChain[0])      // FROM Person
        assertEquals("Order" to "INNER", joinChain[1])    // INNER JOIN Order
        assertEquals("Company" to "INNER", joinChain[2])  // INNER JOIN Company

        println("\nJoin chain for ${combo.key}:")
        joinChain.forEach { (table, joinType) ->
            if (joinType == null) {
                println("  FROM $table")
            } else {
                println("  $joinType JOIN $table")
            }
        }
    }

    @Test
    fun `test statistics calculation`() {
        // Given: Real Kodama relationships
        val tables = listOf("Person", "Order", "Profile", "Company")
        val relationships = listOf(
            "Person" to "Order",
            "Person" to "Profile",
            "Order" to "Person",
            "Order" to "Company",
            "Profile" to "Person",
            "Company" to "Order"
        )

        val generator = RelationshipBasedCombinationGenerator(tables, relationships)

        // When
        val stats = generator.getStatistics()

        // Then
        assertEquals(4, stats.totalTables)
        assertEquals(6, stats.totalRelationships)
        assertEquals(4, stats.tablesWithRelationships)
        assertTrue(stats.averageDegree > 0)
        assertTrue(stats.maxDegree >= 1)

        println("\n$stats")
    }
}
