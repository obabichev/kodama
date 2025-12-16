package com.obabichev.kodama.tests

import com.obabichev.kodama.components.JoinType
import com.obabichev.kodama.query.query
import com.obabichev.kodama.query.eq
import com.obabichev.kodama.tests.data.*
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Person
import com.obabichev.kodama.tests.schema.Profile
import kotlin.test.Test
import kotlin.test.assertEquals

class QuerySimpleDataClassTests : PostgresBaseTest() {
    @Test
    fun test1() {
        val queryBuilder = query()
            .from(Person)
            .join(Order) { order.userName eq person.name }
            .selectAll(Person)
            .where {
                person.name eq "kodama"
            }

        println("Test 1 SQL: ${queryBuilder.build().sql()}")

        withConnection {
            val results = queryBuilder.execute(this)
            val row = results.first()
            assertEquals("kodama", row.person.name)
            assertEquals(1, row.person.age)
        }
    }

    @Test
    fun test2_selectOnlyNoJoin() {
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
