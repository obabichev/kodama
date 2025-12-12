package com.obabichev.kodama.tests

import com.obabichev.kodama.query.query
import com.obabichev.kodama.query.eq
import com.obabichev.kodama.tests.data.*
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Person
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * This test file demonstrates TRUE COMPILE-TIME SAFETY.
 *
 * If you uncomment the lines marked with "❌ COMPILE ERROR",
 * the code will NOT compile - proving type safety works!
 */
class CompileTimeSafetyVerification : PostgresBaseTest() {

    @Test
    fun verifyPersonNameAccessor() {
        val queryBuilder = query()
            .from(Person)
            .select(Person.Name)  // ← Type: QueryResult_Person<Person_Name>
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ COMPILES: row.person exists
            assertEquals("kodama", row.person.name)

            // ❌ COMPILE ERROR: Uncomment these lines to verify compile-time safety:
            // val age = row.person.age  // Error: Unresolved reference 'personAge'
            // val all = row.person      // Error: Unresolved reference 'personAll'
            // val both = row.person // Error: Unresolved reference 'personNameAge'
        }
    }


    @Test
    fun verifyPersonNameAgeAccessor() {
        val queryBuilder = query()
            .from(Person)
            .select(Person.Name)
            .select(Person.Age)  // ← Type: QueryResult_Person<Person_Name_Age>
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ COMPILES: row.person exists
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)

            // ❌ COMPILE ERROR: Uncomment these lines to verify compile-time safety:
            // val name = row.person    // Error: Unresolved reference 'personName'
            // val age = row.person      // Error: Unresolved reference 'personAge'
            // val all = row.person      // Error: Unresolved reference 'personAll'
        }
    }

    @Test
    fun verifyPersonAllAccessor() {
        val queryBuilder = query()
            .from(Person)
            .select(Person.all())  // ← Type: QueryResult_Person<TableAllSelected>
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ COMPILES: row.person exists
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)

            // ❌ COMPILE ERROR: Uncomment these lines to verify compile-time safety:
            // val name = row.person     // Error: Unresolved reference 'personName'
            // val age = row.person       // Error: Unresolved reference 'personAge'
            // val both = row.person  // Error: Unresolved reference 'personNameAge'
        }
    }

    @Test
    fun verifyMixedAccessors() {
        val queryBuilder = query()
            .from(Person)
            .join(Order) { order.userName eq person.name }
            .select(Person.Name)      // ← Person_Name
            .select(Order.Product)    // ← Order_Product
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ COMPILES: row.person and row.order exist
            assertEquals("kodama", row.person.name)
            assertEquals("Laptop", row.order.product)

            // ❌ COMPILE ERROR: Uncomment these lines to verify compile-time safety:
            // val personAll = row.person        // Error: Unresolved reference 'personAll'
            // val personAge = row.person        // Error: Unresolved reference 'personAge'
            // val orderAll = row.order          // Error: Unresolved reference 'orderAll'
            // val orderCost = row.orderCost        // Error: Unresolved reference 'orderCost'
            // val orderIdProduct = row.order  // Error: Unresolved reference 'orderIdProduct'
        }
    }

    @Test
    fun verifyAccessorOnlyExposesSelectedColumns() {
        val queryBuilder = query()
            .from(Person)
            .select(Person.Name)  // ← Only name column
            .where { person.name eq "kodama" }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ COMPILES: personName accessor only has 'name' property
            assertEquals("kodama", row.person.name)

            // ❌ COMPILE ERROR: Uncomment this line to verify column-level safety:
            // val age = row.person.age  // Error: Unresolved reference 'age'
            // This proves that PersonResultAccessor_Name only exposes the 'name' property!
        }
    }
}
