package com.obabichev.kodama.tests.entity

import com.obabichev.kodama.entity.EntitySession
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.entity.User
import com.obabichev.kodama.tests.entity.UserOrder
import com.obabichev.kodama.tests.entity.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Users
import com.obabichev.kodama.tests.schema.UserOrders
import com.obabichev.kodama.tests.KodamaBindingRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for EntitySession lifecycle hooks (Phase 5).
 *
 * Tests entity lifecycle listener functionality:
 * - onPrePersist / onPostPersist (before/after INSERT)
 * - onPreUpdate / onPostUpdate (before/after UPDATE)
 * - onPreDelete / onPostDelete (before/after DELETE)
 * - onPostLoad (after entity loaded from database)
 */
class EntityLifecycleHooksTests : DatabaseTest() {

    companion object {
        // Ensure the binding registry is loaded to enable auto-registration
        private val initRegistry = KodamaBindingRegistry
    }

    override fun requiredTables(): List<Table> = listOf(Users, UserOrders)

    // ========================================
    // Basic Lifecycle Hook Tests
    // ========================================

    @Test
    fun `lifecycle hooks - onPrePersist and onPostPersist called on INSERT`() {
        val events = mutableListOf<String>()

        withConnection {
            EntitySession(this.connection).use { session ->
                session.registerListener(User::class, object : com.obabichev.kodama.entity.EntityListener<User> {
                    override fun onPrePersist(entity: User, session: com.obabichev.kodama.entity.EntitySession) {
                        events.add("onPrePersist: ${entity.name}")
                    }

                    override fun onPostPersist(entity: User, session: com.obabichev.kodama.entity.EntitySession) {
                        events.add("onPostPersist: ${entity.name}")
                    }
                })

                val user = User(4000, "TestUser", "test@example.com")
                session.save<User, Int>(user)

                // Before flush - no hooks called
                assertEquals(0, events.size)

                session.flush()

                // After flush - both hooks called in order
                assertEquals(2, events.size)
                assertEquals("onPrePersist: TestUser", events[0])
                assertEquals("onPostPersist: TestUser", events[1])
            }
        }
    }

    @Test
    fun `lifecycle hooks - onPreUpdate and onPostUpdate called on UPDATE`() {
        // Insert test entity
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (4100, 'Original', 'original@example.com')
                """.trimIndent()
            )
        }

        val events = mutableListOf<String>()

        withConnection {
            EntitySession(this.connection).use { session ->
                session.registerListener(User::class, object : com.obabichev.kodama.entity.EntityListener<User> {
                    override fun onPreUpdate(entity: User, old: User, session: com.obabichev.kodama.entity.EntitySession) {
                        events.add("onPreUpdate: ${old.name} -> ${entity.name}")
                    }

                    override fun onPostUpdate(entity: User, old: User, session: com.obabichev.kodama.entity.EntitySession) {
                        events.add("onPostUpdate: ${old.name} -> ${entity.name}")
                    }
                })

                val user = session.find<User>(4100)!!
                val modified = user.copy(name = "Modified")
                session.save<User, Int>(modified)

                // Before flush - no hooks called
                assertEquals(0, events.size)

                session.flush()

                // After flush - both hooks called in order
                assertEquals(2, events.size)
                assertEquals("onPreUpdate: Original -> Modified", events[0])
                assertEquals("onPostUpdate: Original -> Modified", events[1])
            }
        }
    }

    @Test
    fun `lifecycle hooks - onPreDelete and onPostDelete called on DELETE`() {
        // Insert test entity
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (4200, 'ToDelete', 'delete@example.com')
                """.trimIndent()
            )
        }

        val events = mutableListOf<String>()

        withConnection {
            EntitySession(this.connection).use { session ->
                session.registerListener(User::class, object : com.obabichev.kodama.entity.EntityListener<User> {
                    override fun onPreDelete(entity: User, session: com.obabichev.kodama.entity.EntitySession) {
                        events.add("onPreDelete: ${entity.name}")
                    }

                    override fun onPostDelete(entity: User, session: com.obabichev.kodama.entity.EntitySession) {
                        events.add("onPostDelete: ${entity.name}")
                    }
                })

                val user = session.find<User>(4200)!!
                session.delete(user)

                // Before flush - no hooks called
                assertEquals(0, events.size)

                session.flush()

                // After flush - both hooks called in order
                assertEquals(2, events.size)
                assertEquals("onPreDelete: ToDelete", events[0])
                assertEquals("onPostDelete: ToDelete", events[1])
            }
        }
    }

    @Test
    fun `lifecycle hooks - onPostLoad called on find`() {
        // Insert test entity
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (4300, 'LoadTest', 'load@example.com')
                """.trimIndent()
            )
        }

        val events = mutableListOf<String>()

        withConnection {
            EntitySession(this.connection).use { session ->
                session.registerListener(User::class, object : com.obabichev.kodama.entity.EntityListener<User> {
                    override fun onPostLoad(entity: User, session: com.obabichev.kodama.entity.EntitySession) {
                        events.add("onPostLoad: ${entity.name}")
                    }
                })

                // Find entity - should trigger onPostLoad
                val user = session.find<User>(4300)
                assertNotNull(user)

                // Hook called immediately after load
                assertEquals(1, events.size)
                assertEquals("onPostLoad: LoadTest", events[0])

                // Find again - should NOT trigger (from identity map)
                session.find<User>(4300)

                // Still just 1 event (cached entity not reloaded)
                assertEquals(1, events.size)
            }
        }
    }

    // ========================================
    // Validation Tests
    // ========================================

    @Test
    fun `lifecycle hooks - validation in onPrePersist can throw exception`() {
        withConnection {
            EntitySession(this.connection).use { session ->
                session.registerListener(User::class, object : com.obabichev.kodama.entity.EntityListener<User> {
                    override fun onPrePersist(entity: User, session: com.obabichev.kodama.entity.EntitySession) {
                        require(entity.email.contains("@")) { "Invalid email format" }
                    }
                })

                // Valid email - should succeed
                val validUser = User(4400, "Valid", "valid@example.com")
                session.save<User, Int>(validUser)
                session.flush()  // Should succeed

                // Invalid email - should throw
                val invalidUser = User(4401, "Invalid", "invalid-email")
                session.save<User, Int>(invalidUser)

                // Should throw on flush
                val exception = kotlin.runCatching {
                    session.flush()
                }.exceptionOrNull()

                assertNotNull(exception, "Should throw exception for invalid email")
                // Exception is wrapped in SessionException by error recovery
                assertTrue(
                    exception is com.obabichev.kodama.entity.SessionException,
                    "Should throw SessionException (actual: ${exception!!::class.simpleName})"
                )
                // Original validation exception should be the cause
                val cause = exception.cause
                assertNotNull(cause, "Should have a cause")
                assertTrue(
                    cause is IllegalArgumentException,
                    "Cause should be IllegalArgumentException (actual: ${cause::class.simpleName})"
                )
                assertTrue(
                    cause.message?.contains("Invalid email format") == true,
                    "Should contain error message"
                )
            }
        }
    }

    @Test
    fun `lifecycle hooks - validation in onPreUpdate can throw exception`() {
        // Insert test entity
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (4500, 'Test', 'test@example.com')
                """.trimIndent()
            )
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                session.registerListener(User::class, object : com.obabichev.kodama.entity.EntityListener<User> {
                    override fun onPreUpdate(entity: User, old: User, session: com.obabichev.kodama.entity.EntitySession) {
                        require(entity.email.contains("@")) { "Invalid email format" }
                    }
                })

                val user = session.find<User>(4500)!!

                // Invalid email modification
                val modified = user.copy(email = "invalid-email")
                session.save<User, Int>(modified)

                // Should throw on flush
                val exception = kotlin.runCatching {
                    session.flush()
                }.exceptionOrNull()

                assertNotNull(exception, "Should throw exception for invalid email")
                // Exception is wrapped in SessionException by error recovery
                assertTrue(
                    exception is com.obabichev.kodama.entity.SessionException,
                    "Should throw SessionException (actual: ${exception!!::class.simpleName})"
                )
                // Original validation exception should be the cause
                val cause = exception.cause
                assertNotNull(cause, "Should have a cause")
                assertTrue(
                    cause is IllegalArgumentException,
                    "Cause should be IllegalArgumentException (actual: ${cause::class.simpleName})"
                )
            }
        }
    }

    // ========================================
    // Multiple Listeners Tests
    // ========================================

    @Test
    fun `lifecycle hooks - multiple listeners can be registered`() {
        val events = mutableListOf<String>()

        withConnection {
            EntitySession(this.connection).use { session ->
                // Register first listener
                session.registerListener(User::class, object : com.obabichev.kodama.entity.EntityListener<User> {
                    override fun onPrePersist(entity: User, session: com.obabichev.kodama.entity.EntitySession) {
                        events.add("Listener1: onPrePersist")
                    }
                })

                // Register second listener
                session.registerListener(User::class, object : com.obabichev.kodama.entity.EntityListener<User> {
                    override fun onPrePersist(entity: User, session: com.obabichev.kodama.entity.EntitySession) {
                        events.add("Listener2: onPrePersist")
                    }
                })

                // Register third listener
                session.registerListener(User::class, object : com.obabichev.kodama.entity.EntityListener<User> {
                    override fun onPrePersist(entity: User, session: com.obabichev.kodama.entity.EntitySession) {
                        events.add("Listener3: onPrePersist")
                    }
                })

                val user = User(4600, "MultiListener", "multi@example.com")
                session.save<User, Int>(user)
                session.flush()

                // All three listeners called in registration order
                assertEquals(3, events.size)
                assertEquals("Listener1: onPrePersist", events[0])
                assertEquals("Listener2: onPrePersist", events[1])
                assertEquals("Listener3: onPrePersist", events[2])
            }
        }
    }

    @Test
    fun `lifecycle hooks - listeners are called in registration order`() {
        val events = mutableListOf<Int>()

        withConnection {
            EntitySession(this.connection).use { session ->
                for (i in 1..5) {
                    session.registerListener(User::class, object : com.obabichev.kodama.entity.EntityListener<User> {
                        override fun onPostPersist(entity: User, session: com.obabichev.kodama.entity.EntitySession) {
                            events.add(i)
                        }
                    })
                }

                val user = User(4700, "OrderTest", "order@example.com")
                session.save<User, Int>(user)
                session.flush()

                // Listeners called in registration order
                assertEquals(listOf(1, 2, 3, 4, 5), events)
            }
        }
    }

    // ========================================
    // Real-World Use Case Tests
    // ========================================

    @Test
    fun `lifecycle hooks - audit logging use case`() {
        data class AuditEntry(
            val action: String,
            val entityType: String,
            val entityId: Int,
            val details: String
        )

        val auditLog = mutableListOf<AuditEntry>()

        withConnection {
            EntitySession(this.connection).use { session ->
                session.registerListener(User::class, object : com.obabichev.kodama.entity.EntityListener<User> {
                    override fun onPostPersist(entity: User, session: com.obabichev.kodama.entity.EntitySession) {
                        auditLog.add(
                            AuditEntry(
                                action = "CREATE",
                                entityType = "User",
                                entityId = entity.id,
                                details = "Created user: ${entity.name}"
                            )
                        )
                    }

                    override fun onPostUpdate(entity: User, old: User, session: com.obabichev.kodama.entity.EntitySession) {
                        val changes = mutableListOf<String>()
                        if (entity.name != old.name) changes.add("name: ${old.name} -> ${entity.name}")
                        if (entity.email != old.email) changes.add("email: ${old.email} -> ${entity.email}")

                        auditLog.add(
                            AuditEntry(
                                action = "UPDATE",
                                entityType = "User",
                                entityId = entity.id,
                                details = "Updated fields: ${changes.joinToString(", ")}"
                            )
                        )
                    }

                    override fun onPostDelete(entity: User, session: com.obabichev.kodama.entity.EntitySession) {
                        auditLog.add(
                            AuditEntry(
                                action = "DELETE",
                                entityType = "User",
                                entityId = entity.id,
                                details = "Deleted user: ${entity.name}"
                            )
                        )
                    }
                })

                // Create user
                val user = User(4800, "AuditTest", "audit@example.com")
                session.save<User, Int>(user)
                session.flush()

                // Modify user
                val modified = user.copy(email = "newemail@example.com")
                session.save<User, Int>(modified)
                session.flush()

                // Delete user
                session.delete(modified)
                session.flush()

                // Verify audit log
                assertEquals(3, auditLog.size)

                assertEquals("CREATE", auditLog[0].action)
                assertEquals("Created user: AuditTest", auditLog[0].details)

                assertEquals("UPDATE", auditLog[1].action)
                assertTrue(auditLog[1].details.contains("email:"))

                assertEquals("DELETE", auditLog[2].action)
                assertEquals("Deleted user: AuditTest", auditLog[2].details)
            }
        }
    }

    @Test
    fun `lifecycle hooks - computed fields use case`() {
        var prePersistCalls = 0
        var preUpdateCalls = 0

        withConnection {
            EntitySession(this.connection).use { session ->
                session.registerListener(User::class, object : com.obabichev.kodama.entity.EntityListener<User> {
                    override fun onPrePersist(entity: User, session: com.obabichev.kodama.entity.EntitySession) {
                        // In real code, would update entity.createdAt = Instant.now()
                        // Here we just count calls
                        prePersistCalls++
                    }

                    override fun onPreUpdate(entity: User, old: User, session: com.obabichev.kodama.entity.EntitySession) {
                        // In real code, would update entity.updatedAt = Instant.now()
                        preUpdateCalls++
                    }
                })

                // Create entity - onPrePersist called
                val user = User(4900, "ComputedFields", "computed@example.com")
                session.save<User, Int>(user)
                session.flush()

                assertEquals(1, prePersistCalls)
                assertEquals(0, preUpdateCalls)

                // Update entity - onPreUpdate called
                val modified = user.copy(name = "Updated")
                session.save<User, Int>(modified)
                session.flush()

                assertEquals(1, prePersistCalls)
                assertEquals(1, preUpdateCalls)
            }
        }
    }

    @Test
    fun `lifecycle hooks - onPostLoad called for relationship queries`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (5000, 'RelTest', 'rel@example.com')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO user_orders (id, user_id, product, amount)
                VALUES
                    (5001, 5000, 'Product1', 100),
                    (5002, 5000, 'Product2', 200)
                """.trimIndent()
            )
        }

        val loadEvents = mutableListOf<String>()

        withConnection {
            EntitySession(this.connection).use { session ->
                session.registerListener(UserOrder::class, object : com.obabichev.kodama.entity.EntityListener<UserOrder> {
                    override fun onPostLoad(entity: UserOrder, session: com.obabichev.kodama.entity.EntitySession) {
                        loadEvents.add("Loaded order: ${entity.product}")
                    }
                })

                val user = session.find<User>(5000)!!

                // Load relationships - should trigger onPostLoad for each order
                val orders = user.orders(session)

                // Both orders loaded, both hooks called
                assertEquals(2, loadEvents.size)
                assertEquals("Loaded order: Product1", loadEvents[0])
                assertEquals("Loaded order: Product2", loadEvents[1])
            }
        }
    }
}
