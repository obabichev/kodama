package com.obabichev.kodama.tests.dsl

import com.obabichev.kodama.query.*
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Person
import com.obabichev.kodama.tests.schema.generated.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.Ignore

// Marker objects are auto-generated in the generated package
// Import them using: PersonName, PersonAge, OrderProduct, OrderCost

/**
 * Tests for marker-based column selection with .selectAs()
 * This provides named accessors for individual columns.
 */
class QueryMarkerBasedSelectionTests : DatabaseTest() {

    @Test
    fun `test single column selection with marker`() {
        testData {
            person("kodama", age = 1)
        }

        withConnection {
            val result = from(Person)
                .selectAs(PersonName) { person.name }
                .execute(this)
                .first()

            val personName: String = result.personName

            // Type is now String, not Any?
            assertEquals("kodama", personName)
        }
    }

    @Test
    fun `test multiple column selections with markers`() {
        testData {
            person("kodama", age = 1)
        }

        withConnection {
            val result = from(Person)
                .selectAs(PersonName) { person.name }
                .selectAs(PersonAge) { person.age }
                .execute(this)
                .first()

            // Explicit type annotations ensure compile-time type safety
            val personName: String = result.personName
            val personAge: Int = result.personAge

            assertEquals("kodama", personName)
            assertEquals(1, personAge)
        }
    }

    @Test
    fun `test column selection from joined tables`() {
        testData {
            person("kodama", age = 1)
            order(1, "kodama", "Laptop", 1000)
        }

        withConnection {
            val result = from(Person)
                .join(Order) { order.userName eq person.name }
                .selectAs(PersonName) { person.name }
                .selectAs(OrderProduct) { order.product }
                .selectAs(OrderCost) { order.cost }
                .execute(this)
                .first()

            // Explicit type annotations ensure compile-time type safety
            val personName: String = result.personName
            val orderProduct: String = result.orderProduct
            val orderCost: Int = result.orderCost

            assertEquals("kodama", personName)
            assertEquals("Laptop", orderProduct)
            assertEquals(1000, orderCost)
        }
    }

    @Test
    fun `test mixing selectAs with selectAll`() {
        testData {
            person("kodama", age = 1)
            order(1, "kodama", "Laptop", 1000)
        }

        withConnection {
            val result = from(Person)
                .join(Order) { order.userName eq person.name }
                .selectAs(PersonName) { person.name }
                .selectAll(Order)
                .execute(this)
                .first()

            // Explicit type annotations ensure compile-time type safety
            val personName: String = result.personName
            val product: String = result.order.product as String
            val cost: Int = result.order.cost as Int

            assertEquals("kodama", personName)
            assertEquals("Laptop", product)
            assertEquals(1000, cost)
        }
    }

    override fun requiredTables(): List<Table> {
        return listOf(Person, Order)
    }
}
