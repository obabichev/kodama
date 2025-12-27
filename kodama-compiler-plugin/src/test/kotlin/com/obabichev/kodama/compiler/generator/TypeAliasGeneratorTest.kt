package com.obabichev.kodama.compiler.generator

import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Unit tests for TypeAliasGenerator.
 *
 * Demonstrates the testability of the new architecture:
 * - Pure function: Unit -> String
 * - Easy to test without Gradle context
 * - Fast execution
 */
class TypeAliasGeneratorTest {

    @Test
    fun `generates aggregate count type aliases`() {
        // Given
        val generator = TypeAliasGenerator()

        // When
        val result = generator.generate(Unit)

        // Then
        assertContains(result, "typealias AggCount = com.obabichev.kodama.query.AggCount")
        assertContains(result, "typealias NoAggregates = com.obabichev.kodama.query.NoAggregates")
        assertContains(result, "typealias Has1Aggregate = com.obabichev.kodama.query.Has1Aggregate")
        assertContains(result, "typealias Has2Aggregates = com.obabichev.kodama.query.Has2Aggregates")
    }

    @Test
    fun `generates selection state type aliases`() {
        // Given
        val generator = TypeAliasGenerator()

        // When
        val result = generator.generate(Unit)

        // Then
        assertContains(result, "typealias SelectionState = com.obabichev.kodama.query.SelectionState")
        assertContains(result, "typealias NoSelections = com.obabichev.kodama.query.NoSelections")
        assertContains(result, "typealias Has1Selection = com.obabichev.kodama.query.Has1Selection")
        assertContains(result, "typealias Has2Selections = com.obabichev.kodama.query.Has2Selections")
    }

    @Test
    fun `generates up to 5 aggregate markers`() {
        // Given
        val generator = TypeAliasGenerator()

        // When
        val result = generator.generate(Unit)

        // Then
        assertContains(result, "Has5Aggregates")
    }

    @Test
    fun `generates up to 10 selection markers`() {
        // Given
        val generator = TypeAliasGenerator()

        // When
        val result = generator.generate(Unit)

        // Then
        assertContains(result, "Has10Selections")
    }
}

/**
 * Unit tests for ColumnMarkerGenerator.
 */
class ColumnMarkerGeneratorTest {

    @Test
    fun `generates column marker interfaces`() {
        // Given
        val generator = ColumnMarkerGenerator()
        val columnNames = setOf("name", "age", "email")

        // When
        val result = generator.generate(columnNames)

        // Then
        assertContains(result, "interface Name")
        assertContains(result, "interface Age")
        assertContains(result, "interface Email")
    }

    @Test
    fun `capitalizes first letter of column names`() {
        // Given
        val generator = ColumnMarkerGenerator()
        val columnNames = setOf("userName", "orderCount")

        // When
        val result = generator.generate(columnNames)

        // Then
        assertContains(result, "interface UserName")
        assertContains(result, "interface OrderCount")
    }

    @Test
    fun `generates sorted output`() {
        // Given
        val generator = ColumnMarkerGenerator()
        val columnNames = setOf("zebra", "apple", "banana")

        // When
        val result = generator.generate(columnNames)

        // Then
        val lines = result.lines().filter { it.startsWith("interface") }
        assertTrue(lines[0].contains("Apple"))
        assertTrue(lines[1].contains("Banana"))
        assertTrue(lines[2].contains("Zebra"))
    }
}

/**
 * Unit tests for TableMarkerGenerator.
 */
class TableMarkerGeneratorTest {

    @Test
    fun `generates table marker interfaces`() {
        // Given
        val generator = TableMarkerGenerator()
        val tableNames = listOf("Person", "Order", "Product")

        // When
        val result = generator.generate(tableNames)

        // Then
        assertContains(result, "interface PersonTable")
        assertContains(result, "interface OrderTable")
        assertContains(result, "interface ProductTable")
    }

    @Test
    fun `capitalizes table names`() {
        // Given
        val generator = TableMarkerGenerator()
        val tableNames = listOf("person", "order")

        // When
        val result = generator.generate(tableNames)

        // Then
        assertContains(result, "interface PersonTable")
        assertContains(result, "interface OrderTable")
    }
}

/**
 * Unit tests for AllMarkerGenerator.
 */
class AllMarkerGeneratorTest {

    @Test
    fun `generates AllMarker interface`() {
        // Given
        val generator = AllMarkerGenerator()

        // When
        val result = generator.generate(Unit)

        // Then
        assertContains(result, "interface AllMarker : SelectionMarker")
    }

    @Test
    fun `includes documentation comment`() {
        // Given
        val generator = AllMarkerGenerator()

        // When
        val result = generator.generate(Unit)

        // Then
        assertContains(result, "/**")
        assertContains(result, "Marker interface for .all() selections")
    }
}
