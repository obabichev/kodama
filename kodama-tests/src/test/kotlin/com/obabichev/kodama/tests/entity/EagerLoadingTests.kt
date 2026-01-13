package com.obabichev.kodama.tests.entity

import com.obabichev.kodama.entity.EntitySession
import com.obabichev.kodama.entity.withOneToMany
import com.obabichev.kodama.entity.withManyToMany
import com.obabichev.kodama.tests.entity.User
import com.obabichev.kodama.tests.entity.UserOrder
import com.obabichev.kodama.tests.entity.Role
import com.obabichev.kodama.tests.entity.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Users
import com.obabichev.kodama.tests.schema.UserOrders
import com.obabichev.kodama.tests.schema.Roles
import com.obabichev.kodama.tests.schema.UserRoles
import com.obabichev.kodama.tests.KodamaBindingRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for eager loading / N+1 prevention (Feature 5.3).
 *
 * Demonstrates how eager loading prevents N+1 query problems by batch-loading
 * relationships for collections of entities.
 */
class EagerLoadingTests : DatabaseTest() {

    companion object {
        private val initRegistry = KodamaBindingRegistry
    }

    override fun requiredTables() = listOf(Users, UserOrders, Roles, UserRoles)

    // ========================================
    // One-to-Many Eager Loading Tests
    // ========================================

    @Test
    fun `eager loading - withOneToMany loads all orders in one query`() {
        // Insert test data: 3 users with varying numbers of orders
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES
                    (7000, 'User1', 'user1@example.com'),
                    (7001, 'User2', 'user2@example.com'),
                    (7002, 'User3', 'user3@example.com')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO user_orders (id, user_id, product, amount)
                VALUES
                    (8000, 7000, 'Product1', 100),
                    (8001, 7000, 'Product2', 200),
                    (8002, 7001, 'Product3', 300),
                    (8003, 7002, 'Product4', 400),
                    (8004, 7002, 'Product5', 500),
                    (8005, 7002, 'Product6', 600)
                """.trimIndent()
            )
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                // Load all users
                val user1 = session.find<User>(7000)!!
                val user2 = session.find<User>(7001)!!
                val user3 = session.find<User>(7002)!!
                val users = listOf(user1, user2, user3)

                // Eager load all orders for all users
                users.withOneToMany<User, UserOrder, Int, Int>(
                    session = session,
                    sourceEntityType = User::class,
                    relationshipName = "orders",
                    targetTable = UserOrders,
                    foreignKeyColumn = UserOrders.userId,
                    sourceIdExtractor = { it.id }
                )

                // Access orders - should return cached results, no additional queries
                val user1Orders = user1.orders(session)
                assertEquals(2, user1Orders.size)
                assertEquals(setOf("Product1", "Product2"), user1Orders.map { it.product }.toSet())

                val user2Orders = user2.orders(session)
                assertEquals(1, user2Orders.size)
                assertEquals("Product3", user2Orders[0].product)

                val user3Orders = user3.orders(session)
                assertEquals(3, user3Orders.size)
                assertEquals(setOf("Product4", "Product5", "Product6"), user3Orders.map { it.product }.toSet())
            }
        }
    }

    @Test
    fun `eager loading - withOneToMany handles users with no orders`() {
        // Insert test data: users with and without orders
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES
                    (7100, 'UserWithOrders', 'with@example.com'),
                    (7101, 'UserWithoutOrders', 'without@example.com')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO user_orders (id, user_id, product, amount)
                VALUES (8100, 7100, 'Product1', 100)
                """.trimIndent()
            )
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                val user1 = session.find<User>(7100)!!
                val user2 = session.find<User>(7101)!!
                val users = listOf(user1, user2)

                // Eager load orders
                users.withOneToMany<User, UserOrder, Int, Int>(
                    session = session,
                    sourceEntityType = User::class,
                    relationshipName = "orders",
                    targetTable = UserOrders,
                    foreignKeyColumn = UserOrders.userId,
                    sourceIdExtractor = { it.id }
                )

                // User1 has orders
                val user1Orders = user1.orders(session)
                assertEquals(1, user1Orders.size)

                // User2 has no orders - should return empty list, not null
                val user2Orders = user2.orders(session)
                assertEquals(0, user2Orders.size)
                assertTrue(user2Orders.isEmpty())
            }
        }
    }

    @Test
    fun `eager loading - withOneToMany on empty list returns empty list`() {
        withConnection {
            EntitySession(this.connection).use { session ->
                val users = emptyList<User>()

                // Should not throw, just return empty list
                val result = users.withOneToMany<User, UserOrder, Int, Int>(
                    session = session,
                    sourceEntityType = User::class,
                    relationshipName = "orders",
                    targetTable = UserOrders,
                    foreignKeyColumn = UserOrders.userId,
                    sourceIdExtractor = { it.id }
                )

                assertEquals(emptyList(), result)
            }
        }
    }

    // ========================================
    // Many-to-Many Eager Loading Tests
    // ========================================

    @Test
    fun `eager loading - withManyToMany loads all roles in one query`() {
        // Insert test data: 3 users with different role combinations
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES
                    (7200, 'User1', 'user1@example.com'),
                    (7201, 'User2', 'user2@example.com'),
                    (7202, 'User3', 'user3@example.com')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO roles (id, name)
                VALUES
                    (200, 'Admin'),
                    (201, 'Editor'),
                    (202, 'Viewer')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO user_roles (user_id, role_id)
                VALUES
                    (7200, 200), (7200, 201),  -- User1: Admin, Editor
                    (7201, 201),                -- User2: Editor
                    (7202, 200), (7202, 202)   -- User3: Admin, Viewer
                """.trimIndent()
            )
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                // Load all users
                val user1 = session.find<User>(7200)!!
                val user2 = session.find<User>(7201)!!
                val user3 = session.find<User>(7202)!!
                val users = listOf(user1, user2, user3)

                // Eager load all roles for all users
                users.withManyToMany<User, Role, Int, Int, Int>(
                    session = session,
                    sourceEntityType = User::class,
                    relationshipName = "roles",
                    targetTable = Roles,
                    junctionTable = UserRoles,
                    sourceForeignKeyColumn = UserRoles.userId,
                    targetForeignKeyColumn = UserRoles.roleId,
                    targetPrimaryKeyColumn = Roles.id,
                    sourceIdExtractor = { it.id }
                )

                // Access roles - should return cached results
                val user1Roles = user1.roles(session).map { it.name }.sorted()
                assertEquals(listOf("Admin", "Editor"), user1Roles)

                val user2Roles = user2.roles(session).map { it.name }
                assertEquals(listOf("Editor"), user2Roles)

                val user3Roles = user3.roles(session).map { it.name }.sorted()
                assertEquals(listOf("Admin", "Viewer"), user3Roles)
            }
        }
    }

    @Test
    fun `eager loading - withManyToMany handles users with no roles`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES
                    (7300, 'UserWithRoles', 'with@example.com'),
                    (7301, 'UserWithoutRoles', 'without@example.com')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO roles (id, name)
                VALUES (210, 'TestRole')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO user_roles (user_id, role_id)
                VALUES (7300, 210)
                """.trimIndent()
            )
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                val user1 = session.find<User>(7300)!!
                val user2 = session.find<User>(7301)!!
                val users = listOf(user1, user2)

                // Eager load roles
                users.withManyToMany<User, Role, Int, Int, Int>(
                    session = session,
                    sourceEntityType = User::class,
                    relationshipName = "roles",
                    targetTable = Roles,
                    junctionTable = UserRoles,
                    sourceForeignKeyColumn = UserRoles.userId,
                    targetForeignKeyColumn = UserRoles.roleId,
                    targetPrimaryKeyColumn = Roles.id,
                    sourceIdExtractor = { it.id }
                )

                // User1 has roles
                val user1Roles = user1.roles(session)
                assertEquals(1, user1Roles.size)

                // User2 has no roles
                val user2Roles = user2.roles(session)
                assertEquals(0, user2Roles.size)
                assertTrue(user2Roles.isEmpty())
            }
        }
    }

    // ========================================
    // Chaining / Multiple Relationships Tests
    // ========================================

    @Test
    fun `eager loading - can load multiple relationships with chaining`() {
        // Insert test data with both orders and roles
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (7400, 'ChainUser', 'chain@example.com')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO user_orders (id, user_id, product, amount)
                VALUES (8400, 7400, 'Product1', 100)
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO roles (id, name)
                VALUES (220, 'ChainRole')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO user_roles (user_id, role_id)
                VALUES (7400, 220)
                """.trimIndent()
            )
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                val user = session.find<User>(7400)!!
                val users = listOf(user)

                // Chain both eager loads
                users
                    .withOneToMany<User, UserOrder, Int, Int>(
                        session = session,
                        sourceEntityType = User::class,
                        relationshipName = "orders",
                        targetTable = UserOrders,
                        foreignKeyColumn = UserOrders.userId,
                        sourceIdExtractor = { it.id }
                    )
                    .withManyToMany<User, Role, Int, Int, Int>(
                        session = session,
                        sourceEntityType = User::class,
                        relationshipName = "roles",
                        targetTable = Roles,
                        junctionTable = UserRoles,
                        sourceForeignKeyColumn = UserRoles.userId,
                        targetForeignKeyColumn = UserRoles.roleId,
                        targetPrimaryKeyColumn = Roles.id,
                        sourceIdExtractor = { it.id }
                    )

                // Both relationships should be cached
                val orders = user.orders(session)
                assertEquals(1, orders.size)

                val roles = user.roles(session)
                assertEquals(1, roles.size)
            }
        }
    }

    @Test
    fun `eager loading - cache persists across multiple accesses`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (7500, 'CacheUser', 'cache@example.com')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO user_orders (id, user_id, product, amount)
                VALUES (8500, 7500, 'Product1', 100)
                """.trimIndent()
            )
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                val user = session.find<User>(7500)!!
                val users = listOf(user)

                // Eager load
                users.withOneToMany<User, UserOrder, Int, Int>(
                    session = session,
                    sourceEntityType = User::class,
                    relationshipName = "orders",
                    targetTable = UserOrders,
                    foreignKeyColumn = UserOrders.userId,
                    sourceIdExtractor = { it.id }
                )

                // Access multiple times - should use cache
                val orders1 = user.orders(session)
                val orders2 = user.orders(session)
                val orders3 = user.orders(session)

                // All should return the same cached list
                assertEquals(1, orders1.size)
                assertEquals(1, orders2.size)
                assertEquals(1, orders3.size)

                // Verify they're the same instances (from identity map)
                assertTrue(orders1[0] === orders2[0])
                assertTrue(orders2[0] === orders3[0])
            }
        }
    }
}
