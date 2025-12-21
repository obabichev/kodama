package com.obabichev.kodama.compiler.generator

import com.obabichev.kodama.compiler.model.InsertMethodModel
import com.obabichev.kodama.compiler.model.ParameterModel
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Unit tests for InsertMethodGenerator.
 *
 * Demonstrates testing complex code generation logic with mock models.
 */
class InsertMethodGeneratorTest {

    @Test
    fun `generates insert method signature`() {
        // Given
        val generator = InsertMethodGenerator("com.example.schema")
        val model = InsertMethodModel(
            tableName = "Person",
            parameters = listOf(
                ParameterModel("transaction", "com.obabichev.kodama.execute.JdbcTransaction", false),
                ParameterModel("name", "String", false),
                ParameterModel("age", "Int", false)
            ),
            schemaPackage = "com.example.schema"
        )

        // When
        val result = generator.generate(model)

        // Then
        assertContains(result, "fun com.example.schema.Person.insert(")
        assertContains(result, "transaction: com.obabichev.kodama.execute.JdbcTransaction,")
        assertContains(result, "name: String,")
        assertContains(result, "age: Int")
        assertContains(result, "): com.obabichev.kodama.insert.InsertResult")
    }

    @Test
    fun `generates method body with InsertStatement`() {
        // Given
        val generator = InsertMethodGenerator("com.example.schema")
        val model = InsertMethodModel(
            tableName = "Person",
            parameters = listOf(
                ParameterModel("transaction", "com.obabichev.kodama.execute.JdbcTransaction", false),
                ParameterModel("name", "String", false)
            ),
            schemaPackage = "com.example.schema"
        )

        // When
        val result = generator.generate(model)

        // Then
        assertContains(result, "val table = this")
        assertContains(result, "val insert = com.obabichev.kodama.insert.InsertStatement(")
        assertContains(result, "table = table,")
        assertContains(result, "columns = listOf(table.name),")
        assertContains(result, "values = listOf(name)")
        assertContains(result, "return insert.execute(transaction)")
    }

    @Test
    fun `handles nullable parameters`() {
        // Given
        val generator = InsertMethodGenerator("com.example.schema")
        val model = InsertMethodModel(
            tableName = "Product",
            parameters = listOf(
                ParameterModel("transaction", "com.obabichev.kodama.execute.JdbcTransaction", false),
                ParameterModel("name", "String", false),
                ParameterModel("description", "String", true)
            ),
            schemaPackage = "com.example.schema"
        )

        // When
        val result = generator.generate(model)

        // Then
        assertContains(result, "name: String,")
        assertContains(result, "description: String?")
    }

    @Test
    fun `generates documentation comment`() {
        // Given
        val generator = InsertMethodGenerator("com.example.schema")
        val model = InsertMethodModel(
            tableName = "Order",
            parameters = listOf(
                ParameterModel("transaction", "com.obabichev.kodama.execute.JdbcTransaction", false),
                ParameterModel("product", "String", false)
            ),
            schemaPackage = "com.example.schema"
        )

        // When
        val result = generator.generate(model)

        // Then
        assertContains(result, "/**")
        assertContains(result, "Insert a row into the Order table")
        assertContains(result, "@param product Value for column 'product'")
        assertContains(result, "@return InsertResult")
    }

    @Test
    fun `handles multiple columns`() {
        // Given
        val generator = InsertMethodGenerator("com.example.schema")
        val model = InsertMethodModel(
            tableName = "Order",
            parameters = listOf(
                ParameterModel("transaction", "com.obabichev.kodama.execute.JdbcTransaction", false),
                ParameterModel("id", "Int", false),
                ParameterModel("product", "String", false),
                ParameterModel("cost", "Int", false)
            ),
            schemaPackage = "com.example.schema"
        )

        // When
        val result = generator.generate(model)

        // Then
        assertContains(result, "columns = listOf(table.id, table.product, table.cost),")
        assertContains(result, "values = listOf(id, product, cost)")
    }

    @Test
    fun `excludes transaction from columns and values`() {
        // Given
        val generator = InsertMethodGenerator("com.example.schema")
        val model = InsertMethodModel(
            tableName = "Person",
            parameters = listOf(
                ParameterModel("transaction", "com.obabichev.kodama.execute.JdbcTransaction", false),
                ParameterModel("name", "String", false)
            ),
            schemaPackage = "com.example.schema"
        )

        // When
        val result = generator.generate(model)

        // Then
        assertContains(result, "columns = listOf(table.name),")
        assertContains(result, "values = listOf(name)")

        // Verify transaction is NOT in columns or values
        val lines = result.lines()
        val columnsLine = lines.find { it.contains("columns = listOf") }
        val valuesLine = lines.find { it.contains("values = listOf") }

        assertTrue(columnsLine?.contains("transaction") == false)
        assertTrue(valuesLine?.contains("transaction") == false)
    }
}
