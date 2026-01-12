package com.obabichev.kodama.tests.entity

import com.obabichev.kodama.entity.EntitySession
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.entity.User
import com.obabichev.kodama.tests.entity.UserOrder
import com.obabichev.kodama.tests.entity.impl.UserOrder  // Factory function
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
 * Tests for one-to-many relationships in the entity layer.
 *
 * Features Tested:
 * - Relationship methods with context receivers (user.orders(session) with EntitySession in context)
 * - Generated relationship method implementations in entity interfaces
 * - Lazy loading of related entities
 * - Identity map integration for relationship queries
 * - Manual cascade operations
 */
class RelationshipTests : DatabaseTest() {

    companion object {
        // Ensure the binding registry is loaded to enable auto-registration
        private val initRegistry = KodamaBindingRegistry
    }

    override fun requiredTables(): List<Table> = listOf(Users, UserOrders)

    @Test
    fun `load one-to-many relationship returns all related entities`() {
        // Setup test data
        withConnection {
            executeUpdate("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@test.com')")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (1, 1, 'Laptop', 1200)")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (2, 1, 'Mouse', 25)")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (3, 1, 'Keyboard', 75)")
        }

        // Test
        withConnection {
            EntitySession(this.connection).use { session ->
                with(session) {
                    val user = find<User>(1)
                    assertNotNull(user, "User should be found")

                    // Load orders via relationship - uses context parameter
                    val orders = user.orders(session)

                    // Verify
                    assertEquals(3, orders.size, "User should have 3 orders")
                    assertTrue(orders.any { it.product == "Laptop" }, "Should have Laptop order")
                    assertTrue(orders.any { it.product == "Mouse" }, "Should have Mouse order")
                    assertTrue(orders.any { it.product == "Keyboard" }, "Should have Keyboard order")
                    assertEquals(1300, orders.sumOf { it.amount }, "Total amount should be 1300")
                }
            }
        }
    }

    @Test
    fun `load one-to-many relationship returns empty list when no related entities`() {
        // User with no orders
        withConnection {
            executeUpdate("INSERT INTO users (id, name, email) VALUES (99, 'NoOrders', 'none@test.com')")
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                with(session) {
                    val user = find<User>(99)!!
                    val orders = user.orders(session)

                    assertTrue(orders.isEmpty(), "User with no orders should return empty list")
                }
            }
        }
    }

    @Test
    fun `relationship respects identity map - no duplicate entity instances`() {
        withConnection {
            executeUpdate("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@test.com')")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (1, 1, 'Laptop', 1200)")
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                with(session) {
                    val user = find<User>(1)!!

                    // Load orders first time
                    val orders1 = user.orders(session)

                    // Load orders second time
                    val orders2 = user.orders(session)

                    // Instances should be same (from identity map)
                    assertEquals(1, orders1.size)
                    assertEquals(1, orders2.size)
                    assertTrue(orders1[0] === orders2[0], "Should return same instance from identity map")
                }
            }
        }
    }

    @Test
    fun `relationship works with already cached entities`() {
        withConnection {
            executeUpdate("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@test.com')")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (1, 1, 'Laptop', 1200)")
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                with(session) {
                    // Load order directly first (caches it)
                    val directOrder = find<UserOrder>(1)
                    assertNotNull(directOrder)

                    // Now load via relationship
                    val user = find<User>(1)!!
                    val orders = user.orders(session)

                    // Should return cached instance
                    assertEquals(1, orders.size)
                    assertTrue(directOrder === orders[0], "Should use cached entity from identity map")
                }
            }
        }
    }

    @Test
    fun `multiple users can have separate orders`() {
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
                    val alice = find<User>(1)!!
                    val bob = find<User>(2)!!

                    val aliceOrders = alice.orders(session)
                    val bobOrders = bob.orders(session)

                    assertEquals(2, aliceOrders.size, "Alice should have 2 orders")
                    assertEquals(1, bobOrders.size, "Bob should have 1 order")

                    assertTrue(aliceOrders.all { it.userId == 1 }, "All Alice's orders should reference Alice")
                    assertTrue(bobOrders.all { it.userId == 2 }, "All Bob's orders should reference Bob")
                }
            }
        }
    }

    @Test
    fun `manual cascade delete - delete related entities explicitly`() {
        withConnection {
            executeUpdate("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@test.com')")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (1, 1, 'Laptop', 1200)")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (2, 1, 'Mouse', 25)")
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                with(session) {
                    val user = find<User>(1)!!

                    // Manual cascade: delete orders first
                    val orders = user.orders(session)
                    orders.forEach { delete(it) }

                    // Then delete user
                    delete(user)

                    // Flush both deletes
                    flush()

                    // Verify deletion
                    assertNull(find<User>(1), "User should be deleted")
                    assertNull(find<UserOrder>(1), "Order 1 should be deleted")
                    assertNull(find<UserOrder>(2), "Order 2 should be deleted")
                }
            }
        }
    }

    @Test
    fun `save new related entity and verify via relationship`() {
        withConnection {
            executeUpdate("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@test.com')")
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                with(session) {
                    val user = find<User>(1)!!

                    // Initially no orders
                    assertEquals(0, user.orders(session).size, "User should have no orders initially")

                    // Create and save new order
                    val newOrder = UserOrder(
                        id = 100,
                        userId = user.id,
                        product = "Headphones",
                        amount = 80
                    )
                    save<UserOrder, Int>(newOrder)
                    flush()

                    // Load orders again - should include new order
                    val orders = user.orders(session)
                    assertEquals(1, orders.size, "User should have 1 order after saving")
                    assertEquals("Headphones", orders[0].product)
                }
            }
        }
    }

    @Test
    fun `relationship query loads all fields correctly`() {
        withConnection {
            executeUpdate("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@test.com')")
            executeUpdate("INSERT INTO user_orders (id, user_id, product, amount) VALUES (1, 1, 'Gaming Laptop', 2500)")
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                with(session) {
                    val user = find<User>(1)!!
                    val orders = user.orders(session)

                    assertEquals(1, orders.size)
                    val order = orders[0]

                    // Verify all fields are loaded correctly
                    assertEquals(1, order.id, "Order ID should match")
                    assertEquals(1, order.userId, "User ID should match")
                    assertEquals("Gaming Laptop", order.product, "Product should match")
                    assertEquals(2500, order.amount, "Amount should match")
                }
            }
        }
    }
}
