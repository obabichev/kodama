package com.obabichev.kodama.compiler.generator

import com.obabichev.kodama.compiler.model.ColumnModel
import com.obabichev.kodama.compiler.model.TableModel
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Unit tests for TableAccessorGenerator.
 *
 * Demonstrates testing with mock models - no need for actual database or Gradle context.
 */
class TableAccessorGeneratorTest {

    @Test
    fun `generates accessor class for table`() {
        // Given
        val generator = TableAccessorGenerator("com.example.schema")
        val table = TableModel(
            name = "Person",
            columns = listOf(
                ColumnModel("name", "String", false),
                ColumnModel("age", "Int", false)
            ),
            schemaPackage = "com.example.schema"
        )

        // When
        val result = generator.generate(table)

        // Then
        assertContains(result, "class PersonAccessor")
    }

    @Test
    fun `generates column properties with correct types`() {
        // Given
        val generator = TableAccessorGenerator("com.example.schema")
        val table = TableModel(
            name = "Person",
            columns = listOf(
                ColumnModel("name", "String", false),
                ColumnModel("age", "Int", false)
            ),
            schemaPackage = "com.example.schema"
        )

        // When
        val result = generator.generate(table)

        // Then
        assertContains(result, "val name: com.obabichev.kodama.components.TypedColumn<String, PersonTable, Name>")
        assertContains(result, "val age: com.obabichev.kodama.components.TypedColumn<Int, PersonTable, Age>")
    }

    @Test
    fun `generates nullable column types`() {
        // Given
        val generator = TableAccessorGenerator("com.example.schema")
        val table = TableModel(
            name = "Product",
            columns = listOf(
                ColumnModel("name", "String", false),
                ColumnModel("description", "String", true)
            ),
            schemaPackage = "com.example.schema"
        )

        // When
        val result = generator.generate(table)

        // Then
        assertContains(result, "val name: com.obabichev.kodama.components.TypedColumn<String, ProductTable, Name>")
        assertContains(result, "val description: com.obabichev.kodama.components.TypedColumn<String?, ProductTable, Description>")
    }

    @Test
    fun `uses schema package in generated code`() {
        // Given
        val generator = TableAccessorGenerator("com.custom.package")
        val table = TableModel(
            name = "Order",
            columns = listOf(
                ColumnModel("id", "Int", false)
            ),
            schemaPackage = "com.custom.package"
        )

        // When
        val result = generator.generate(table)

        // Then
        assertContains(result, "com.custom.package.Order")
    }
}

/**
 * Unit tests for TableOrderByAccessorGenerator.
 */
class TableOrderByAccessorGeneratorTest {

    @Test
    fun `generates order by accessor class`() {
        // Given
        val generator = TableOrderByAccessorGenerator("com.example.schema")
        val table = TableModel(
            name = "Person",
            columns = listOf(
                ColumnModel("name", "String", false),
                ColumnModel("age", "Int", false)
            ),
            schemaPackage = "com.example.schema"
        )

        // When
        val result = generator.generate(table)

        // Then
        assertContains(result, "class PersonOrderByAccessor")
    }

    @Test
    fun `generates column properties for order by`() {
        // Given
        val generator = TableOrderByAccessorGenerator("com.example.schema")
        val table = TableModel(
            name = "Person",
            columns = listOf(
                ColumnModel("name", "String", false),
                ColumnModel("age", "Int", false)
            ),
            schemaPackage = "com.example.schema"
        )

        // When
        val result = generator.generate(table)

        // Then
        assertContains(result, "val name: com.obabichev.kodama.components.TypedColumn<String, PersonTable, Name>")
        assertContains(result, "val age: com.obabichev.kodama.components.TypedColumn<Int, PersonTable, Age>")
    }

    @Test
    fun `handles nullable columns in order by`() {
        // Given
        val generator = TableOrderByAccessorGenerator("com.example.schema")
        val table = TableModel(
            name = "Product",
            columns = listOf(
                ColumnModel("price", "Int", true)
            ),
            schemaPackage = "com.example.schema"
        )

        // When
        val result = generator.generate(table)

        // Then
        assertContains(result, "val price: com.obabichev.kodama.components.TypedColumn<Int?, ProductTable, Price>")
    }
}
