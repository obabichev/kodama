package com.obabichev.kodama.tests

import com.obabichev.kodama.query.RelationsContainer
import com.obabichev.kodama.tests.schema.Company
import com.obabichev.kodama.tests.schema.Person
import com.obabichev.kodama.tests.data.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RelationsContainerTests {

    @Test
    fun testRelationCaching() {
        val container = RelationsContainer()

        val relation1 = container.relation(Person)
        val relation2 = container.relation(Person)

        // Should return the same instance (cached)
        assertSame(relation1, relation2)
        assertEquals("person", relation1.name)
    }

    @Test
    fun testColumnByProperty() {
        val container = RelationsContainer()

        val nameColumn = container.column(Person.name)
        val ageColumn = container.column(Person.age)

        assertEquals("name", nameColumn.name)
        assertEquals("age", ageColumn.name)
        assertEquals("person", nameColumn.relation.name)
        assertEquals("person", ageColumn.relation.name)
    }

    @Test
    fun testColumnCaching() {
        val container = RelationsContainer()

        val column1 = container.column(Person.name)
        val column2 = container.column(Person.name)

        // Should return the same instance (cached)
        assertSame(column1, column2)
    }

    @Test
    fun testRelationBuildsColumns() {
        val container = RelationsContainer()

        val relation = container.relation(Person)

        assertEquals("person", relation.name)
        assertEquals(2, relation.columns.size)

        val columnNames = relation.columns.map { it.name }.toSet()
        assertTrue(columnNames.contains("name"))
        assertTrue(columnNames.contains("age"))
    }

    @Test
    fun testMultipleClasses() {
        val container = RelationsContainer()

        val personRelation = container.relation(Person)
        val companyRelation = container.relation(Company)

        assertEquals("person", personRelation.name)
        assertEquals("company", companyRelation.name)

        // Different classes should have different relations
        assertTrue(personRelation !== companyRelation)
    }

    @Test
    fun testColumnsBelongToCorrectRelation() {
        val container = RelationsContainer()

        val nameColumn = container.column(Person.name)
        val ageColumn = container.column(Person.age)
        val personRelation = container.relation(Person)

        // All columns should reference the cached relation
        assertSame(personRelation, nameColumn.relation)
        assertSame(personRelation, ageColumn.relation)
    }

    @Test
    fun testColumnsAvailableFromRelation() {
        val container = RelationsContainer()

        val nameColumn = container.column(Person.name)
        val ageColumn = container.column(Person.age)
        val relation = container.relation(Person)

        // Columns should be registered in the relation
        assertTrue(relation.columns.contains(nameColumn))
        assertTrue(relation.columns.contains(ageColumn))
    }
}
