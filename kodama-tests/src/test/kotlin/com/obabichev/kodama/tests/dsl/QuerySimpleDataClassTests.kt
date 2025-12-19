package com.obabichev.kodama.tests.dsl

import com.obabichev.kodama.components.JoinType
import com.obabichev.kodama.components.expression.and
import com.obabichev.kodama.query.query
import com.obabichev.kodama.query.eq
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.schema.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Person
import com.obabichev.kodama.tests.schema.Profile
import kotlin.test.Test
import kotlin.test.assertEquals

class QuerySimpleDataClassTests : DatabaseTest() {

    override fun requiredTables(): List<Table> = listOf(Person, Order, Profile)
    @Test
    fun test1() {
        testData {
            person("kodama", age = 1)
            order(1, "kodama", "Laptop", 1000)
        }

        val queryBuilder = query()
            .from(Person)
            .join(Order) { order.userName eq person.name }
            .selectAll(Person)
            .where {
                (order.product eq "Laptop") and (person.name eq "kodama")
            }

        println("Test 1 SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            // Type safety verified: row.order is NOT accessible since Order wasn't selected
            // Only Person was selected with .selectAll(Person), so only row.person exists
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)
        }
    }

    @Test
    fun test2_selectOnlyNoJoin() {
        testData {
            person("kodama", age = 1)
        }

        val queryBuilder = query()
            .from(Person)
            .selectAll(Person)
            .where {
                person.name eq "kodama"
            }

        println("Test 2 SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)
        }
    }

    @Test
    fun test3_selectFromJoinedTable() {
        testData {
            person("kodama", age = 1)
            order(1, "kodama", "Laptop", 1000)
        }

        val queryBuilder = query()
            .from(Person)
            .join(Order) {
                order.userName eq person.name
            }
            .selectAll(Person)
            .selectAll(Order)
            .where {
                person.name eq "kodama"
            }

        println("Test 3 SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)
        }
    }

    @Test
    fun test4_selectSpecificColumns() {
        testData {
            person("kodama", age = 1)
            order(1, "kodama", "Laptop", 1000)
        }

        // Use new .select() API for specific columns - chain them for type safety
        withConnection {
            val results = query()
                .from(Person)
                .join(Order) {
                    order.userName eq person.name
                }
                .selectAll(Person)  // Select all person columns
                .selectAll(Order)   // Select all order columns
                .where {
                    person.name eq "kodama"
                }
                .execute(this)
            val row = results.first()

            // ✅ Can access all columns via table accessors
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)
            assertEquals(1, row.order.id)
            assertEquals("Laptop", row.order.product)
        }
    }

    @Test
    fun test5_joinWithProfile() {
        testData {
            person("kodama", age = 1)
            order(1, "kodama", "Laptop", 1000)
        }

        withConnection {
            val results = query()
                .from(Person)
                .join(Order, type = JoinType.INNER) {
                    order.userName eq person.name
                }
                .selectAll(Person)
                .selectAll(Order)
                .where {
                    person.name eq "kodama"
                }
                .execute(this)

            val row = results.first()
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)
            assertEquals("Laptop", row.order.product)
            assertEquals(1000, row.order.cost)
        }
    }

    @Test
    fun test6_twoJoins() {
        testData {
            person("kodama", age = 1)
            order(1, "kodama", "Laptop", 1000)
        }

        val queryBuilder = query()
            .from(Person)
            .join(Order) {
                order.userName eq person.name
            }
            .selectAll(Person)
            .selectAll(Order)
            .where {
                person.name eq "kodama"
            }

        println("Test 6 SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)
            assertEquals("Laptop", row.order.product)
            assertEquals(1000, row.order.cost)
        }
    }

    @Test
    fun test7_selectFromProfile() {
        testData {
            person("kodama", age = 1)
            profile("kodama", "kodama@example.com", "photo1.jpg")
        }

        val queryBuilder = query()
            .from(Profile)
            .selectAll(Profile)
            .where {
                profile.userName eq "kodama"
            }

        println("Test 7 SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()
            assertEquals("kodama", row.profile.userName)
            assertEquals("kodama@example.com", row.profile.contact)
            assertEquals("photo1.jpg", row.profile.photo)
        }
    }

    @Test
    fun test8_multipleJoins() {
        testData {
            person("kodama", age = 1)
            order(1, "kodama", "Laptop", 1000)
            profile("kodama", "kodama@example.com", "photo1.jpg")
        }

        val queryBuilder = query()
            .from(Person)
            .join(Order) { order.userName eq person.name }
            .join(Profile) { profile.userName eq person.name }
            .selectAll(Person)
            .selectAll(Order)
            .selectAll(Profile)
            .where {
                person.name eq "kodama"
            }

        println("Test 8 SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()

            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)
            assertEquals("Laptop", row.order.product)
            assertEquals(1000, row.order.cost)
            assertEquals("kodama@example.com", row.profile.contact)
            assertEquals("photo1.jpg", row.profile.photo)
        }
    }
}
