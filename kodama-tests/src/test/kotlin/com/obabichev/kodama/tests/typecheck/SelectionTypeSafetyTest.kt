package com.obabichev.kodama.tests.typecheck

import com.obabichev.kodama.query.*
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Person
import com.obabichev.kodama.tests.schema.generated.*
import kotlin.test.Test

/**
 * This test verifies that the per-position selection status implementation works correctly.
 *
 * The type system should enforce that you can ONLY access tables in the result
 * if they have been selected with `.selectAll()`.
 *
 * ## How It Works
 *
 * Each table position (T1, T2, T3, ...) has its own selection status (S1, S2, S3, ...):
 * - `TableNotSelected` - Table has NOT been selected yet
 * - `TableSelected` - Table HAS been selected
 *
 * Result accessors are constrained with `where SN : TableSelected`, which means:
 * - `row.person` is ONLY available when S1 : TableSelected
 * - `row.order` is ONLY available when S2 : TableSelected
 *
 * ## This Test
 *
 * Query: `from(Person).join(Order).selectAll(Order)` produces type:
 * `QueryBuilder_2<PersonMarker, OrderMarker, TableNotSelected, TableSelected, NoSelections>`
 *                                               ^^^^^^^^^^^^^^^^  ^^^^^^^^^^^^^
 *                                               Person NOT selected  Order selected
 *
 * Result type: `QueryResult_2<PersonMarker, OrderMarker, TableNotSelected, TableSelected, NoSelections>`
 *
 * - `row.order.product` ✅ COMPILES (S2 : TableSelected)
 * - `row.person.name` ❌ DOES NOT COMPILE (S1 : TableNotSelected)
 */
class SelectionTypeSafetyTest : DatabaseTest() {

    override fun requiredTables(): List<Table> = listOf(Person, Order)

    @Test
    fun `test only selected tables are accessible in results`() {
        testData {
            person("kodama", 1)
            order(1, "kodama", "Laptop", 1000)
        }

        withConnection {
            // Query: Only Order is selected, Person is joined but NOT selected
            val results = from(Person)
                .join(Order) { order.userName eq person.name }
                .selectAll(Order)  // Only Order selected!
                .execute(this)

            results.forEach { row ->
                // ✅ This SHOULD compile - Order was selected
                val product = row.order.product
                val cost = row.order.cost
                println("Order: $product - $$cost")

                // ❌ These lines SHOULD NOT compile - Person was NOT selected
                // Uncomment to verify compile-time error:
                // val name = row.person.name  // ERROR: No extension function person for QueryResult_2<PersonMarker, OrderMarker, TableNotSelected, TableSelected, NoSelections>
                // val age = row.person.age    // ERROR: where S1 : TableSelected constraint not satisfied
            }
        }
    }

    @Test
    fun `test both tables accessible when both are selected`() {
        testData {
            person("kodama", 1)
            order(1, "kodama", "Laptop", 1000)
        }

        withConnection {
            // Query: BOTH Person and Order are selected
            val results = from(Person)
                .join(Order) { order.userName eq person.name }
                .selectAll(Person)  // Select Person
                .selectAll(Order)   // Select Order
                .execute(this)

            results.forEach { row ->
                // ✅ Both SHOULD compile - both tables were selected
                val name = row.person.name
                val product = row.order.product
                println("$name ordered $product")
            }
        }
    }

    @Test
    fun `test single table query - selected table accessible`() {
        testData {
            person("kodama", 1)
        }

        withConnection {
            // Single-table query: Person is selected
            val results = from(Person)
                .selectAll(Person)
                .execute(this)

            results.forEach { row ->
                // ✅ This SHOULD compile - Person was selected
                val name = row.person.name
                val age = row.person.age
                println("$name is $age years old")
            }
        }
    }
}
