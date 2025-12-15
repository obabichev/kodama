package com.obabichev.kodama.tests

import com.obabichev.kodama.query.query
import com.obabichev.kodama.query.eq
import com.obabichev.kodama.tests.data.*
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Person
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Demonstrates the type-safe partial column selection feature.
 *
 * Key feature: The QueryResult type parameter shows EXACTLY which columns were selected!
 *
 * Examples:
 * - QueryResult_Person<Person_Name> - only Person.name selected
 * - QueryResult_Person<Person_Name_Age> - Person.name and Person.age selected
 * - QueryResult_Person<TableAllSelected> - all Person columns selected
 * - QueryResult_Person_Order<Person_Name, Order_Product> - Person.name and Order.product selected
 */
class PartialColumnSelectionDemo : PostgresBaseTest() {

    @Test
    fun demonstrateSingleColumnSelection() {
        // Select only Person.name
        val queryBuilder = query()
            .from(Person)
            .select(Person.Name)  // ← Type becomes Person_Name
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ Type signature: QueryResult_Person<Person_Name>
            // This clearly shows only 'name' was selected!

            // ✅ Can access selected column via personName accessor
            assertEquals("kodama", row.person.name)

            // ✅ Compile-time safety: row.person doesn't compile!
            // ✅ Compile-time safety: row.person doesn't compile!
            // Only row.person is available when Person_Name is selected!
        }
    }

    @Test
    fun demonstrateTwoColumnSelection() {
        // Select both Person.name and Person.age
        val queryBuilder = query()
            .from(Person)
            .select(Person.Name)
            .select(Person.Age)  // ← Type becomes Person_Name_Age
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ Type signature: QueryResult_Person<Person_Name_Age>
            // This clearly shows both 'name' and 'age' were selected!

            // ✅ Can access both selected columns via personNameAge accessor
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)
        }
    }

    @Test
    fun demonstratePartialColumnsAcrossJoin() {
        // Select specific columns from multiple tables
        val queryBuilder = query()
            .from(Person)
            .join(Order) { order.userName eq person.name }
            .select(Person.Name)     // ← Person_Name
            .select(Order.Product)   // ← Order_Product
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ Type signature: QueryResult_Person_Order<Person_Name, Order_Product>
            // The type perfectly documents which columns are available!

            // ✅ Can access selected columns via specific accessors
            assertEquals("kodama", row.person.name)
            assertEquals("Laptop", row.order.product)

            // ✅ Compile-time safety:
            // row.person doesn't exist (age not selected)
            // row.orderCost doesn't exist (cost not selected)
            // row.person doesn't exist (not all Person columns selected)
            // row.order doesn't exist (not all Order columns selected)
        }
    }

    @Test
    fun demonstrateMixedAllAndPartialSelection() {
        // Mix .all() selection with partial column selection
        val queryBuilder = query()
            .from(Person)
            .join(Order) { order.userName eq person.name }
            .select(Person.all())      // ← TableAllSelected (all columns)
            .select(Order.Product)     // ← Order_Product (only product)
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ Type signature: QueryResult_Person_Order<TableAllSelected, Order_Product>
            // Person: all columns available
            // Order: only product available

            // ✅ All Person columns accessible via personAll
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)

            // ✅ Order.product accessible via orderProduct
            assertEquals("Laptop", row.order.product)

            // ✅ Compile-time safety: row.order doesn't exist!
        }
    }

    // Note: demonstrateAvailableMethods test removed
    // The lambda-based select API uses person.name, person.all(), etc.
    // within .select { } blocks, providing a consistent and clean syntax.
}
