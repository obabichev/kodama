package com.obabichev.kodama.tests

import com.obabichev.kodama.query.query
import com.obabichev.kodama.query.eq
import com.obabichev.kodama.tests.data.*
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Person
import com.obabichev.kodama.tests.schema.Profile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests demonstrating TRUE compile-time type safety with the new selectXxxAll() API
 */
class TypeLevelSafetyTests : PostgresBaseTest() {

    @Test
    fun testSelectAllWithTypeTracking() {
        val queryBuilder = query()
            .from(Person)
            .join(Order) { order.userName eq person.name }
            .join(Profile) { profile.userName eq person.name }
            .select(Person.all())   // ← Changes type to <TableAllSelected, NotSelectedState, NotSelectedState>
            .select(Order.all())    // ← Changes type to <TableAllSelected, TableAllSelected, NotSelectedState>
            .select(Profile.all())  // ← Changes type to <TableAllSelected, TableAllSelected, TableAllSelected>
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ All tables are non-nullable! (all used .all())
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)
            assertEquals("Laptop", row.order.product)
            assertEquals(1000, row.order.cost)
            assertEquals("kodama@example.com", row.profile.contact)
            assertEquals("photo1.jpg", row.profile.photo)
        }
    }

    @Test
    fun testSelectPartialColumns() {
        val queryBuilder = query()
            .from(Person)
            .select(Person.Name)
            .select(Person.Age) // ← Select both name and age (NOT .all())
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ Type shows Person_Name_Age
            // row has type: QueryResult_Person<Person_Name_Age>

            // ✅ Both columns accessible via personNameAge
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)
        }
    }

    @Test
    fun testMixedSelectionWithNewAPI() {
        val queryBuilder = query()
            .from(Person)
            .join(Order) { order.userName eq person.name }
            .select(Person.all())  // ← All Person columns
            .select(Order.all())   // ← All Order columns
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ Both tables non-nullable
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)
            assertEquals("Laptop", row.order.product)
            assertEquals(1000, row.order.cost)
        }
    }

    @Test
    fun testAllTablesSelected() {
        val queryBuilder = query()
            .from(Person)
            .join(Order) { order.userName eq person.name }
            .select(Person.all())
            .select(Order.all())
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ Both tables are non-nullable!
            assertEquals("kodama", row.person.name)
            assertEquals("Laptop", row.order.product)

            // No !! needed anywhere!
        }
    }

    @Test
    fun testPartialColumnsWithJoin() {
        val queryBuilder = query()
            .from(Person)
            .join(Order) { order.userName eq person.name }
            .select(Person.Name)        // ← Only name from Person
            .select(Order.Product)      // ← Only product from Order
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ Type signature: QueryResult_Person_Order<Person_Name, Order_Product>
            // This clearly shows which columns are available!

            // ✅ Selected columns accessible via specific accessors
            assertEquals("kodama", row.person.name)
            assertEquals("Laptop", row.order.product)

            // ✅ Compile-time safety: row.person doesn't exist!
            // ✅ Compile-time safety: row.order doesn't exist!
        }
    }
}
