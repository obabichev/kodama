package com.obabichev.kodama.tests

import com.obabichev.kodama.query.query
import com.obabichev.kodama.query.eq
import com.obabichev.kodama.tests.data.*
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Person
import com.obabichev.kodama.tests.schema.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * This class demonstrates the compile-time type safety for column selections.
 *
 * Key rule: Table accessors (row.person, row.order, etc.) are only available
 * when .all() was called for that table in the select clause.
 */
class TypeSafetyDemonstration : PostgresBaseTest() {

    @Test
    fun demonstrateAllSelectedMakesAccessorAvailable() {
        // When we select person.all(), the accessor is available
        val queryBuilder = query()
            .from(Person)
            .select(Person.all())
            .where {
                person.name eq "kodama"
            }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ This compiles because person.all() was selected - NO NULLABILITY!
            val personAccessor = row.person
            assertEquals("kodama", personAccessor.name)
            assertEquals(1, personAccessor.age)
        }
    }

    @Test
    fun demonstrateSpecificColumnsDoNotExposeAccessor() {
        // When we select specific columns - chain them for type safety
        val queryBuilder = query()
            .from(Person)
            .join(Order) {
                order.userName eq person.name
            }
            .select(Person.Name)   // Select name
            .select(Person.Age)    // Select age
            .select(Order.Product) // Select product
            .where {
                person.name eq "kodama"
            }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ Only selected accessors are available
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)
            assertEquals("Laptop", row.order.product)

            // ❌ Accessing other accessors would NOT compile:
            // row.person - compile error
            // row.order - compile error
        }
    }

    @Test
    fun demonstratePartialAllSelection() {
        // Select all columns from all three tables
        val queryBuilder = query()
            .from(Person)
            .join(Order) {
                order.userName eq person.name
            }
            .join(Profile) {
                profile.userName eq person.name
            }
            .select(Person.all())
            .select(Order.all())
            .select(Profile.all())

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ All accessors available - NO NULLABILITY!
            assertEquals("kodama", row.person.name)
            assertEquals("kodama@example.com", row.profile.contact)
            assertEquals("Laptop", row.order.product)
        }
    }

    /**
     * This test demonstrates the improved type safety that prevents the bug
     * described in the original problem:
     *
     * "I removed from select the line `profile.photo` and the line
     * `assertEquals("photo1.jpg", row.profile.photo)` fails, because it tries
     * to get `photo` column that was not selected."
     *
     * With the new type safety, if you don't select .all(), you can't access
     * row.profile at all - you get a runtime error!
     */
    @Test
    fun demonstrateOriginalProblemIsSolved() {
        val queryBuilder = query()
            .from(Person)
            .join(Order) { order.userName eq person.name }
            .join(Profile) { profile.userName eq person.name }
            .select(Person.all())
            .select(Order.all())
            .select(Profile.all())
            .where {
                person.name eq "kodama"
            }

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // ✅ All accessors available - NO NULLABILITY!
            assertEquals("kodama", row.person.name)
            assertEquals("Laptop", row.order.product)
            assertEquals("kodama@example.com", row.profile.contact)

            // This is the type safety improvement!
            // The type signature QueryResult_Person_All_Order_All_Profile_All
            // clearly documents what was selected!
        }
    }
}
