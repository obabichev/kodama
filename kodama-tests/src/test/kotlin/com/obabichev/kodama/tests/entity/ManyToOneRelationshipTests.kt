package com.obabichev.kodama.tests.entity

import com.obabichev.kodama.entity.EntitySession
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.entity.User
import com.obabichev.kodama.tests.entity.UserOrder
import com.obabichev.kodama.tests.entity.generated.User  // Factory function
import com.obabichev.kodama.tests.entity.generated.UserOrder  // Factory function
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Users
import com.obabichev.kodama.tests.schema.UserOrders
import com.obabichev.kodama.tests.KodamaBindingRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for many-to-one relationships in the entity layer.
 *
 * Features Tested:
 * - Many-to-one relationship navigation (child → parent)
 * - Generated relationship method implementations in entity interfaces
 * - Identity map integration for parent entities
 * - Navigation from multiple children to same parent
 * - Nullable foreign keys (optional relationships)
 */
class ManyToOneRelationshipTests : DatabaseTest() {

    companion object {
        // Ensure the binding registry is loaded to enable auto-registration
        private val initRegistry = KodamaBindingRegistry
    }

    override fun requiredTables(): List<Table> = listOf(Users, UserOrders)

    @Test
    fun `navigate from order to user - parent exists`() {
        // Setup test data
        withConnection {
            executeUpdate("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@test.com')")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (1, 1, 'Laptop', 1200)")
        }

        // Test
        withConnection {
            EntitySession(this.connection).use { session ->
                with(session) {
                    val order = find<UserOrder>(1)
                    assertNotNull(order, "Order should be found")

                    // Navigate to parent user
                    val user = order.user(session)

                    // Verify
                    assertNotNull(user, "User should be found")
                    assertEquals(1, user.id, "User ID should match")
                    assertEquals("Alice", user.name, "User name should match")
                    assertEquals("alice@test.com", user.email, "User email should match")
                }
            }
        }
    }

    @Test
    fun `multiple orders navigate to same user - identity map returns same instance`() {
        withConnection {
            executeUpdate("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@test.com')")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (1, 1, 'Laptop', 1200)")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (2, 1, 'Mouse', 25)")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (3, 1, 'Keyboard', 75)")
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                with(session) {
                    val order1 = find<UserOrder>(1)!!
                    val order2 = find<UserOrder>(2)!!
                    val order3 = find<UserOrder>(3)!!

                    // Navigate from each order to parent user
                    val user1 = order1.user(session)
                    val user2 = order2.user(session)
                    val user3 = order3.user(session)

                    // All should return the same instance from identity map
                    assertNotNull(user1)
                    assertTrue(user1 === user2, "Should return same User instance from identity map")
                    assertTrue(user1 === user3, "Should return same User instance from identity map")
                    assertTrue(user2 === user3, "Should return same User instance from identity map")
                }
            }
        }
    }

    @Test
    fun `navigate works with already cached parent`() {
        withConnection {
            executeUpdate("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@test.com')")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (1, 1, 'Laptop', 1200)")
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                with(session) {
                    // Load user directly first (caches it)
                    val directUser = find<User>(1)
                    assertNotNull(directUser)

                    // Now load order and navigate to user
                    val order = find<UserOrder>(1)!!
                    val parentUser = order.user(session)

                    // Should return cached instance
                    assertTrue(directUser === parentUser, "Should use cached User from identity map")
                }
            }
        }
    }

    @Test
    fun `different orders can belong to different users`() {
        withConnection {
            executeUpdate("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@test.com')")
            executeUpdate("INSERT INTO users (id, name, email) VALUES (2, 'Bob', 'bob@test.com')")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (1, 1, 'Laptop', 1200)")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (2, 1, 'Mouse', 25)")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (3, 2, 'Monitor', 300)")
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                with(session) {
                    val aliceOrder1 = find<UserOrder>(1)!!
                    val aliceOrder2 = find<UserOrder>(2)!!
                    val bobOrder = find<UserOrder>(3)!!

                    val alice1 = aliceOrder1.user(session)
                    val alice2 = aliceOrder2.user(session)
                    val bob = bobOrder.user(session)

                    // Alice's orders point to same user
                    assertNotNull(alice1)
                    assertNotNull(alice2)
                    assertTrue(alice1 === alice2, "Alice's orders should reference same User instance")
                    assertEquals("Alice", alice1.name)

                    // Bob's order points to different user
                    assertNotNull(bob)
                    assertTrue(alice1 !== bob, "Bob should be different User instance")
                    assertEquals("Bob", bob.name)
                }
            }
        }
    }

    @Test
    fun `bidirectional navigation - order to user and back to orders`() {
        withConnection {
            executeUpdate("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@test.com')")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (1, 1, 'Laptop', 1200)")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (2, 1, 'Mouse', 25)")
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                with(session) {
                    // Start from order
                    val order = find<UserOrder>(1)!!

                    // Navigate to parent user
                    val user = order.user(session)
                    assertNotNull(user)
                    assertEquals("Alice", user.name)

                    // Navigate back to all orders
                    val allOrders = user.orders(session)
                    assertEquals(2, allOrders.size, "User should have 2 orders")

                    // Original order should be in the list (same instance from identity map)
                    assertTrue(allOrders.contains(order), "Original order should be in user's orders")
                    assertTrue(allOrders.any { it === order }, "Should find exact same instance")
                }
            }
        }
    }

    @Test
    fun `create new order and navigate to existing user`() {
        withConnection {
            executeUpdate("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@test.com')")
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                with(session) {
                    // Create new order
                    val newOrder = UserOrder(
                        id = 100,
                        userId = 1,
                        product = "Headphones",
                        amount = 80
                    )
                    save<UserOrder, Int>(newOrder)
                    flush()

                    // Navigate to parent user
                    val user = newOrder.user(session)
                    assertNotNull(user, "Should find parent user")
                    assertEquals("Alice", user.name)
                    assertEquals("alice@test.com", user.email)
                }
            }
        }
    }

    @Test
    fun `many-to-one loads all parent fields correctly`() {
        withConnection {
            executeUpdate("INSERT INTO users (id, name, email) VALUES (1, 'Alice Smith', 'alice.smith@test.com')")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (1, 1, 'Gaming Laptop', 2500)")
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                with(session) {
                    val order = find<UserOrder>(1)!!
                    val user = order.user(session)

                    // Verify all fields are loaded correctly
                    assertNotNull(user)
                    assertEquals(1, user.id, "User ID should match")
                    assertEquals("Alice Smith", user.name, "User name should match")
                    assertEquals("alice.smith@test.com", user.email, "User email should match")
                }
            }
        }
    }

    @Test
    fun `delete user after checking orders reference it`() {
        withConnection {
            executeUpdate("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@test.com')")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (1, 1, 'Laptop', 1200)")
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                with(session) {
                    val order = find<UserOrder>(1)!!
                    val user = order.user(session)
                    assertNotNull(user)

                    // Delete order first (to avoid foreign key constraint)
                    delete(order)
                    flush()

                    // Then delete user
                    delete(user)
                    flush()

                    // Verify deletion
                    assertNull(find<UserOrder>(1), "Order should be deleted")
                    assertNull(find<User>(1), "User should be deleted")
                }
            }
        }
    }
}
