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
            .select {
                +person.all()
            }
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
            .select {
                +person.all()
            }
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
            .select {
                +person.all()
                +order.all()
            }
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
        val queryBuilder = query()
            .from(Person)
            .join(Order) {
                order.userName eq person.name
            }
            .select {
                +person.name
                +person.age
                +order.product
                +order.cost
            }
            .where {
                person.name eq "kodama"
            }

        println("Test 4 SQL: ${queryBuilder.build().sql()}")

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
    fun test5_joinWithProfile() {
        val queryBuilder = query()
            .from(Person)
            .join(Order, type = JoinType.INNER) {
                order.userName eq person.name
            }
            .select {
                +person.all()
                +order.product
                +order.cost
            }
            .where {
                person.name eq "kodama"
            }

        println("Test 5 SQL: ${queryBuilder.build().sql()}")

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
    fun test6_twoJoins() {
        val queryBuilder = query()
            .from(Person)
            .join(Order) {
                order.userName eq person.name
            }
            .select {
                +person.all()
                +order.product
                +order.cost
            }
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
            .select {
                +profile.all()
            }
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
            .select {
                +person.all()
                +order.product
                +order.cost
                +profile.contact
                +profile.photo
            }
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
