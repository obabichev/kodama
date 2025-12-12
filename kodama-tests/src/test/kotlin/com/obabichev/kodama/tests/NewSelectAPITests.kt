package com.obabichev.kodama.tests

import com.obabichev.kodama.query.query
import com.obabichev.kodama.query.eq
import com.obabichev.kodama.tests.data.*
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for the new select() API that requires returning SelectionMarker
 */
class NewSelectAPITests : PostgresBaseTest() {

    @Test
    fun testSelectTableAll() {
        // Select all columns from Person using lambda API
        val queryBuilder = query()
            .from(Person)
            .select(Person.all())  // Lambda-based select method
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ person accessor is available (non-nullable!)
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)
        }
    }

    @Test
    fun testSelectSpecificColumn() {
        // Select specific column using lambda API
        val queryBuilder = query()
            .from(Person)
            .select(Person.Name)  // Select only name column
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ personName accessor available
            assertEquals("kodama", row.person.name)

            // ❌ Accessing other accessors would NOT compile:
            // row.person would cause compile error
            // row.person would cause compile error
        }
    }

    @Test
    fun testMultipleSelectCalls() {
        // Select from multiple tables
        val queryBuilder = query()
            .from(Person)
            .join(Order) { order.userName eq person.name }
            .select(Person.all())      // Select all Person columns
            .select(Order.Product)     // Select Order product column
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ person is available (used .all()) - NO NULL CHECKS NEEDED!
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)

            // ✅ orderProduct is available
            assertEquals("Laptop", row.order.product)
        }
    }

    @Test
    fun testBothTablesWithAll() {
        val queryBuilder = query()
            .from(Person)
            .join(Order) { order.userName eq person.name }
            .select(Person.all())
            .select(Order.all())
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ Both accessors available (both used .all()) - NO NULLABILITY!
            assertEquals("kodama", row.person.name)
            assertEquals("Laptop", row.order.product)
            assertEquals(1000, row.order.cost)
        }
    }

    @Test
    fun testMultipleSpecificColumns() {
        val queryBuilder = query()
            .from(Person)
            .select(Person.Name)
            .select(Person.Age)  // Select both name and age
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ personNameAge accessor available, both columns accessible
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)
        }
    }
}
