package com.obabichev.kodama.tests.relationships

import com.obabichev.kodama.query.QueryRelationshipRegistry
import com.obabichev.kodama.query.OneToMany
import com.obabichev.kodama.query.ManyToOne
import com.obabichev.kodama.query.CanJoin
import com.obabichev.kodama.tests.schema.Person
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Profile
import com.obabichev.kodama.tests.schema.PersonCanJoinOrder
import com.obabichev.kodama.tests.schema.PersonCanJoinProfile
import com.obabichev.kodama.tests.schema.OrderCanJoinPerson
import com.obabichev.kodama.tests.schema.ProfileCanJoinPerson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proof-of-concept tests for relationship-based join system.
 * Verifies that relationship declarations are registered correctly.
 */
class RelationshipRegistryTest {

    @Test
    fun `Person has declared relationships to Order and Profile`() {
        val relationships = QueryRelationshipRegistry.getRelationships(Person)

        assertEquals(2, relationships.size, "Person should have 2 relationships declared")

        val orderRelationship = relationships.find {
            it is OneToMany && it.targetTable == Order
        } as? OneToMany<*, *, *>

        val profileRelationship = relationships.find {
            it is OneToMany && it.targetTable == Profile
        } as? OneToMany<*, *, *>

        assertTrue(
            orderRelationship != null,
            "Person should have one-to-many relationship with Order"
        )
        assertTrue(
            profileRelationship != null,
            "Person should have one-to-many relationship with Profile"
        )
    }

    @Test
    fun `Order has declared relationship to Person`() {
        val relationships = QueryRelationshipRegistry.getRelationships(Order)

        assertEquals(2, relationships.size, "Order should have 2 relationships declared (Person and Company)")

        val personRelationship = relationships.find { (it as? ManyToOne<*, *, *>)?.targetTable == Person } as? ManyToOne<*, *, *>

        assertTrue(
            personRelationship != null && personRelationship.targetTable == Person,
            "Order should have many-to-one relationship with Person"
        )
    }

    @Test
    fun `Profile has declared relationship to Person`() {
        val relationships = QueryRelationshipRegistry.getRelationships(Profile)

        assertEquals(1, relationships.size, "Profile should have 1 relationship declared")

        val personRelationship = relationships.first() as? ManyToOne<*, *, *>

        assertTrue(
            personRelationship != null && personRelationship.targetTable == Person,
            "Profile should have many-to-one relationship with Person"
        )
    }

    @Test
    fun `Relationship registry contains all tables with relationships`() {
        val tables = QueryRelationshipRegistry.getAllTables()

        assertTrue(
            Person in tables,
            "Registry should contain Person table"
        )
        assertTrue(
            Order in tables,
            "Registry should contain Order table"
        )
        assertTrue(
            Profile in tables,
            "Registry should contain Profile table"
        )
    }

    @Test
    fun `Person to Order relationship has correct columns`() {
        val relationships = QueryRelationshipRegistry.getRelationships(Person)
        val orderRelationship = relationships.find {
            it is OneToMany && it.targetTable == Order
        } as OneToMany<*, *, *>

        assertEquals(
            Order.userName.name,
            orderRelationship.foreignKey.name,
            "Foreign key should be Order.userName"
        )
        assertEquals(
            Person.name.name,
            orderRelationship.primaryKey.name,
            "Primary key should be Person.name"
        )
    }

    @Test
    fun `CanJoin instances exist for declared relationships`() {
        // These are compile-time checks - if code compiles, CanJoin instances exist
        val personToOrder: CanJoin<Person, Order> = PersonCanJoinOrder
        val personToProfile: CanJoin<Person, Profile> = PersonCanJoinProfile
        val orderToPerson: CanJoin<Order, Person> = OrderCanJoinPerson
        val profileToPerson: CanJoin<Profile, Person> = ProfileCanJoinPerson

        // If we reach here, all CanJoin instances are properly defined
        assertTrue(personToOrder is CanJoin<*, *>)
        assertTrue(personToProfile is CanJoin<*, *>)
        assertTrue(orderToPerson is CanJoin<*, *>)
        assertTrue(profileToPerson is CanJoin<*, *>)
    }
}
