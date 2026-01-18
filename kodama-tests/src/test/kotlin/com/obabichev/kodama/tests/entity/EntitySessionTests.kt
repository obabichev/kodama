package com.obabichev.kodama.tests.entity

import com.obabichev.kodama.entity.EntitySession
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.entity.User
import com.obabichev.kodama.tests.entity.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Users
import com.obabichev.kodama.tests.entity.generated.KodamaBindingRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for EntitySession and entity layer Phase 1 functionality.
 *
 * Phase 1 Features Tested:
 * - EntityTable<E> definition
 * - Manual EntityBinding registration
 * - find() method with identity map caching
 * - Entity loading from database
 * - Transaction-scoped sessions
 */
class EntitySessionTests : DatabaseTest() {

    companion object {
        // Ensure the binding registry is loaded to enable auto-registration
        private val initRegistry = KodamaBindingRegistry
    }

    override fun requiredTables(): List<Table> = listOf(Users)

    @Test
    fun `find entity by id - entity exists`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (1, 'Alice', 'alice@example.com')
                """.trimIndent()
            )
        }

        // Test entity loading with new API
        withConnection {
            EntitySession(this.connection).use { session ->
                // Find user using User.find() instead of session.find()
                val user = session.find<User>(1)

                // Verify
                assertNotNull(user, "User should be found")
                assertEquals(1, user.id)
                assertEquals("Alice", user.name)
                assertEquals("alice@example.com", user.email)
            }
        }
    }

    @Test
    fun `find entity by id - entity does not exist`() {
        withConnection {
            EntitySession(this.connection).use { session ->
                // Try to find non-existent user
                val user = session.find<User>(999)

                // Verify
                assertNull(user, "Non-existent user should return null")
            }
        }
    }

    @Test
    fun `find same entity twice - returns same instance from identity map`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (2, 'Bob', 'bob@example.com')
                """.trimIndent()
            )
        }

        // Test identity map caching
        withConnection {
            EntitySession(this.connection).use { session ->
                // Find user first time
                val user1 = session.find<User>(2)
                assertNotNull(user1)

                // Find same user second time
                val user2 = session.find<User>(2)
                assertNotNull(user2)

                // Verify same instance (identity map hit)
                assertTrue(
                    user1 === user2,
                    "Identity map should return the same instance for the same ID"
                )
            }
        }
    }

    @Test
    fun `find multiple different entities - each cached separately`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES
                    (10, 'Charlie', 'charlie@example.com'),
                    (20, 'Diana', 'diana@example.com'),
                    (30, 'Eve', 'eve@example.com')
                """.trimIndent()
            )
        }

        // Test multiple entities
        withConnection {
            EntitySession(this.connection).use { session ->
                // Find three different users
                val charlie = session.find<User>(10)
                val diana = session.find<User>(20)
                val eve = session.find<User>(30)

                // Verify all found
                assertNotNull(charlie)
                assertNotNull(diana)
                assertNotNull(eve)

                // Verify correct data
                assertEquals("Charlie", charlie.name)
                assertEquals("Diana", diana.name)
                assertEquals("Eve", eve.name)

                // Find again - should get same instances
                val charlie2 = session.find<User>(10)
                val diana2 = session.find<User>(20)
                val eve2 = session.find<User>(30)

                // Verify identity map for each
                assertTrue(charlie === charlie2)
                assertTrue(diana === diana2)
                assertTrue(eve === eve2)

                // Verify session statistics
                val stats = session.stats()
                assertEquals(3, stats.cachedEntities, "Should have 3 entities cached")
            }
        }
    }

    @Test
    fun `session clear removes entities from cache`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (50, 'Frank', 'frank@example.com')
                """.trimIndent()
            )
        }

        // Test cache clearing
        withConnection {
            EntitySession(this.connection).use { session ->
                // Find user - loads into cache
                val user1 = session.find<User>(50)
                assertNotNull(user1)
                assertEquals(1, session.stats().cachedEntities)

                // Clear cache
                session.clear()
                assertEquals(0, session.stats().cachedEntities)

                // Find again - should load from database (not cache)
                val user2 = session.find<User>(50)
                assertNotNull(user2)

                // Should be different instances (cache was cleared)
                assertTrue(
                    user1 !== user2,
                    "After clear(), find should create new instance"
                )

                // But data should be the same
                assertEquals(user1.id, user2.id)
                assertEquals(user1.name, user2.name)
                assertEquals(user1.email, user2.email)
            }
        }
    }

    @Test
    fun `reified find method works correctly`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (100, 'Grace', 'grace@example.com')
                """.trimIndent()
            )
        }

        // Test reified find
        withConnection {
            EntitySession(this.connection).use { session ->
                // Use reified find (no need to pass entity class/table)
                val user = session.find<User>(100)

                // Verify
                assertNotNull(user)
                assertEquals(100, user.id)
                assertEquals("Grace", user.name)
                assertEquals("grace@example.com", user.email)
            }
        }
    }

    @Test
    fun `session close clears identity map`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (200, 'Henry', 'henry@example.com')
                """.trimIndent()
            )
        }

        withConnection {
            val session = EntitySession(this.connection)

            // Find user
            val user = session.find<User>(200)
            assertNotNull(user)
            assertEquals(1, session.stats().cachedEntities)

            // Close session
            session.close()

            // Cache should be cleared
            assertEquals(0, session.stats().cachedEntities)
        }
    }

    // ========================================
    // Phase 2: INSERT Support Tests
    // ========================================

    @Test
    fun `save new entity stages it for INSERT`() {
        withConnection {
            EntitySession(this.connection).use { session ->
                // Create new entity
                val newUser = User(
                    id = 500,
                    name = "NewUser",
                    email = "newuser@example.com"
                )

                // Save - should stage for INSERT
                session.save<com.obabichev.kodama.tests.entity.User, Int>(newUser)

                // Check statistics - should have 1 pending insert
                val stats = session.stats()
                assertEquals(1, stats.cachedEntities)
                assertEquals(1, stats.pendingInserts)
                assertEquals(0, stats.pendingUpdates)
                assertEquals(0, stats.pendingDeletes)

                // Entity should be in identity map
                val cached = session.find<User>(500)
                assertNotNull(cached)
                assertEquals("NewUser", cached.name)
            }
        }
    }

    @Test
    fun `flush executes pending INSERT and marks entity as MANAGED`() {
        withConnection {
            EntitySession(this.connection).use { session ->
                // Create new entity
                val newUser = User(
                    id = 600,
                    name = "FlushTest",
                    email = "flush@example.com"
                )

                // Save and flush
                session.save<com.obabichev.kodama.tests.entity.User, Int>(newUser)
                session.flush()

                // After flush: should have 0 pending inserts
                val stats = session.stats()
                assertEquals(1, stats.cachedEntities)
                assertEquals(0, stats.pendingInserts)

                // Entity should exist in database
                val stmt = connection.createStatement()
                val result = stmt.executeQuery("SELECT * FROM users WHERE id = 600")
                assertTrue(result.next(), "Entity should be in database after flush")
                assertEquals("FlushTest", result.getString("name"))
                assertEquals("flush@example.com", result.getString("email"))
                result.close()
                stmt.close()
            }
        }
    }

    @Test
    fun `save and flush multiple entities`() {
        withConnection {
            EntitySession(this.connection).use { session ->
                // Create multiple entities
                val user1 = User(700, "User1", "user1@example.com")
                val user2 = User(701, "User2", "user2@example.com")
                val user3 = User(702, "User3", "user3@example.com")

                // Save all
                session.save<com.obabichev.kodama.tests.entity.User, Int>(user1)
                session.save<com.obabichev.kodama.tests.entity.User, Int>(user2)
                session.save<com.obabichev.kodama.tests.entity.User, Int>(user3)

                // Check pending
                val statsBeforeFlush = session.stats()
                assertEquals(3, statsBeforeFlush.pendingInserts)

                // Flush all
                session.flush()

                // Check after flush
                val statsAfterFlush = session.stats()
                assertEquals(3, statsAfterFlush.cachedEntities)
                assertEquals(0, statsAfterFlush.pendingInserts)

                // Verify all exist in database
                val stmt = connection.createStatement()
                val count = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE id BETWEEN 700 AND 702")
                assertTrue(count.next())
                assertEquals(3, count.getInt(1))
                count.close()
                stmt.close()
            }
        }
    }

    @Test
    fun `flush with no pending changes does nothing`() {
        withConnection {
            EntitySession(this.connection).use { session ->
                // Flush with nothing pending - should not error
                session.flush()

                // Stats should be zero
                val stats = session.stats()
                assertEquals(0, stats.cachedEntities)
                assertEquals(0, stats.pendingInserts)
            }
        }
    }

    @Test
    fun `save-flush-save-flush works correctly`() {
        withConnection {
            EntitySession(this.connection).use { session ->
                // First batch
                val user1 = User(800, "Batch1", "batch1@example.com")
                session.save<com.obabichev.kodama.tests.entity.User, Int>(user1)
                session.flush()

                // Second batch
                val user2 = User(801, "Batch2", "batch2@example.com")
                session.save<com.obabichev.kodama.tests.entity.User, Int>(user2)

                // Before second flush
                val statsBeforeSecondFlush = session.stats()
                assertEquals(2, statsBeforeSecondFlush.cachedEntities)
                assertEquals(1, statsBeforeSecondFlush.pendingInserts)

                session.flush()

                // After second flush
                val statsAfterSecondFlush = session.stats()
                assertEquals(2, statsAfterSecondFlush.cachedEntities)
                assertEquals(0, statsAfterSecondFlush.pendingInserts)

                // Verify both exist
                val stmt = connection.createStatement()
                val count = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE id IN (800, 801)")
                assertTrue(count.next())
                assertEquals(2, count.getInt(1))
                count.close()
                stmt.close()
            }
        }
    }

    @Test
    fun `entity remains in identity map after flush`() {
        withConnection {
            EntitySession(this.connection).use { session ->
                // Create and save entity
                val user = User(900, "Persistent", "persistent@example.com")
                session.save<com.obabichev.kodama.tests.entity.User, Int>(user)
                session.flush()

                // Find again - should return same instance from identity map (no DB query)
                val found = session.find<User>(900)
                assertNotNull(found)
                assertTrue(user === found, "Should return same instance from identity map")
            }
        }
    }

    // ========================================
    // Phase 3: UPDATE Support Tests
    // ========================================

    @Test
    fun `modify loaded entity and flush - executes UPDATE`() {
        // Insert test entity
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (1000, 'Original', 'original@example.com')
                """.trimIndent()
            )
        }

        // Load, modify, and update
        withConnection {
            EntitySession(this.connection).use { session ->
                // Load entity
                val user = session.find<User>(1000)
                assertNotNull(user)
                assertEquals("Original", user.name)

                // Modify using copy
                val modified = user.copy(name = "Modified")

                // Save modified entity
                session.save<com.obabichev.kodama.tests.entity.User, Int>(modified)

                // Check stats - should have 1 pending update
                val statsBeforeFlush = session.stats()
                assertEquals(1, statsBeforeFlush.pendingUpdates)

                // Flush
                session.flush()

                // Check stats after flush
                val statsAfterFlush = session.stats()
                assertEquals(0, statsAfterFlush.pendingUpdates)

                // Verify in database
                val stmt = connection.createStatement()
                val result = stmt.executeQuery("SELECT * FROM users WHERE id = 1000")
                assertTrue(result.next())
                assertEquals("Modified", result.getString("name"))
                assertEquals("original@example.com", result.getString("email"))  // Unchanged
                result.close()
                stmt.close()
            }
        }
    }

    @Test
    fun `modify multiple fields - partial UPDATE with only changed fields`() {
        // Insert test entity
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (1100, 'Alice', 'alice@old.com')
                """.trimIndent()
            )
        }

        // Modify both fields
        withConnection {
            EntitySession(this.connection).use { session ->
                val user = session.find<User>(1100)!!
                val modified = user.copy(name = "Alicia", email = "alice@new.com")

                session.save<com.obabichev.kodama.tests.entity.User, Int>(modified)
                session.flush()

                // Verify both fields changed
                val stmt = connection.createStatement()
                val result = stmt.executeQuery("SELECT * FROM users WHERE id = 1100")
                assertTrue(result.next())
                assertEquals("Alicia", result.getString("name"))
                assertEquals("alice@new.com", result.getString("email"))
                result.close()
                stmt.close()
            }
        }
    }

    @Test
    fun `save entity without changes - no UPDATE executed`() {
        // Insert test entity
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (1200, 'Unchanged', 'unchanged@example.com')
                """.trimIndent()
            )
        }

        // Load and save without modifying
        withConnection {
            EntitySession(this.connection).use { session ->
                val user = session.find<User>(1200)!!

                // Save same entity (no changes)
                session.save<com.obabichev.kodama.tests.entity.User, Int>(user)

                // Should NOT be marked as pending update
                val stats = session.stats()
                assertEquals(0, stats.pendingUpdates)
            }
        }
    }

    @Test
    fun `multiple save-flush cycles for same entity`() {
        // Insert test entity
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (1300, 'Version1', 'v1@example.com')
                """.trimIndent()
            )
        }

        // Multiple modifications
        withConnection {
            EntitySession(this.connection).use { session ->
                // Load
                val user1 = session.find<User>(1300)!!

                // First modification
                val user2 = user1.copy(name = "Version2")
                session.save<com.obabichev.kodama.tests.entity.User, Int>(user2)
                session.flush()

                // Second modification
                val user3 = user2.copy(email = "v3@example.com")
                session.save<com.obabichev.kodama.tests.entity.User, Int>(user3)
                session.flush()

                // Verify final state
                val stmt = connection.createStatement()
                val result = stmt.executeQuery("SELECT * FROM users WHERE id = 1300")
                assertTrue(result.next())
                assertEquals("Version2", result.getString("name"))
                assertEquals("v3@example.com", result.getString("email"))
                result.close()
                stmt.close()
            }
        }
    }

    @Test
    fun `UPDATE and INSERT in same flush`() {
        // Insert existing entity
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (1400, 'Existing', 'existing@example.com')
                """.trimIndent()
            )
        }

        // Mix INSERT and UPDATE
        withConnection {
            EntitySession(this.connection).use { session ->
                // Load and modify existing entity
                val existing = session.find<User>(1400)!!
                val modified = existing.copy(name = "Updated")
                session.save<com.obabichev.kodama.tests.entity.User, Int>(modified)

                // Create new entity
                val newUser = User(1401, "NewUser", "new@example.com")
                session.save<com.obabichev.kodama.tests.entity.User, Int>(newUser)

                // Check stats before flush
                val statsBeforeFlush = session.stats()
                assertEquals(1, statsBeforeFlush.pendingInserts)
                assertEquals(1, statsBeforeFlush.pendingUpdates)

                // Flush both
                session.flush()

                // Check stats after flush
                val statsAfterFlush = session.stats()
                assertEquals(0, statsAfterFlush.pendingInserts)
                assertEquals(0, statsAfterFlush.pendingUpdates)

                // Verify both in database
                val stmt = connection.createStatement()

                // Check UPDATE
                val result1 = stmt.executeQuery("SELECT * FROM users WHERE id = 1400")
                assertTrue(result1.next())
                assertEquals("Updated", result1.getString("name"))
                result1.close()

                // Check INSERT
                val result2 = stmt.executeQuery("SELECT * FROM users WHERE id = 1401")
                assertTrue(result2.next())
                assertEquals("NewUser", result2.getString("name"))
                result2.close()

                stmt.close()
            }
        }
    }

    @Test
    fun `snapshot is updated after flush for future change detection`() {
        // Insert test entity
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (1500, 'First', 'first@example.com')
                """.trimIndent()
            )
        }

        // Load, modify, flush, modify again
        withConnection {
            EntitySession(this.connection).use { session ->
                // Load original
                val user1 = session.find<User>(1500)!!

                // First modification
                val user2 = user1.copy(name = "Second")
                session.save<com.obabichev.kodama.tests.entity.User, Int>(user2)
                session.flush()  // Snapshot should be updated to "Second"

                // Second modification (should detect change from "Second", not "First")
                val user3 = user2.copy(name = "Third")
                session.save<com.obabichev.kodama.tests.entity.User, Int>(user3)

                // Should detect change (Second -> Third)
                val stats = session.stats()
                assertEquals(1, stats.pendingUpdates)

                session.flush()

                // Verify final state
                val stmt = connection.createStatement()
                val result = stmt.executeQuery("SELECT * FROM users WHERE id = 1500")
                assertTrue(result.next())
                assertEquals("Third", result.getString("name"))
                result.close()
                stmt.close()
            }
        }
    }

    // ========================================
    // Phase 4: DELETE Support Tests
    // ========================================

    @Test
    fun `delete loaded entity and flush - executes DELETE`() {
        // Insert test entity
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (2000, 'ToDelete', 'delete@example.com')
                """.trimIndent()
            )
        }

        // Load, delete, and flush
        withConnection {
            EntitySession(this.connection).use { session ->
                // Load entity
                val user = session.find<User>(2000)
                assertNotNull(user)

                // Delete
                session.delete(user)

                // Check stats - should have 1 pending delete
                val statsBeforeFlush = session.stats()
                assertEquals(1, statsBeforeFlush.pendingDeletes)
                assertEquals(1, statsBeforeFlush.cachedEntities)  // Still in cache before flush

                // Flush
                session.flush()

                // Check stats after flush
                val statsAfterFlush = session.stats()
                assertEquals(0, statsAfterFlush.pendingDeletes)
                assertEquals(0, statsAfterFlush.cachedEntities)  // Removed after flush

                // Verify deleted from database
                val stmt = connection.createStatement()
                val result = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE id = 2000")
                assertTrue(result.next())
                assertEquals(0, result.getInt(1), "Entity should be deleted from database")
                result.close()
                stmt.close()
            }
        }
    }

    @Test
    fun `delete multiple entities`() {
        // Insert test entities
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES
                    (2100, 'Delete1', 'delete1@example.com'),
                    (2101, 'Delete2', 'delete2@example.com'),
                    (2102, 'Delete3', 'delete3@example.com')
                """.trimIndent()
            )
        }

        // Delete multiple
        withConnection {
            EntitySession(this.connection).use { session ->
                // Load all
                val user1 = session.find<User>(2100)!!
                val user2 = session.find<User>(2101)!!
                val user3 = session.find<User>(2102)!!

                // Delete all
                session.delete(user1)
                session.delete(user2)
                session.delete(user3)

                // Check stats
                val statsBeforeFlush = session.stats()
                assertEquals(3, statsBeforeFlush.pendingDeletes)

                // Flush
                session.flush()

                // Check stats after flush
                val statsAfterFlush = session.stats()
                assertEquals(0, statsAfterFlush.pendingDeletes)
                assertEquals(0, statsAfterFlush.cachedEntities)

                // Verify all deleted
                val stmt = connection.createStatement()
                val result = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE id BETWEEN 2100 AND 2102")
                assertTrue(result.next())
                assertEquals(0, result.getInt(1))
                result.close()
                stmt.close()
            }
        }
    }

    @Test
    fun `delete PENDING_INSERT entity - removes from session without database operation`() {
        withConnection {
            EntitySession(this.connection).use { session ->
                // Create new entity (not yet persisted)
                val newUser = User(2200, "TempUser", "temp@example.com")
                session.save<com.obabichev.kodama.tests.entity.User, Int>(newUser)

                // Verify pending insert
                assertEquals(1, session.stats().pendingInserts)

                // Delete before flush
                session.delete(newUser)

                // Should be removed from session immediately (never persisted)
                val stats = session.stats()
                assertEquals(0, stats.pendingInserts)
                assertEquals(0, stats.pendingDeletes)
                assertEquals(0, stats.cachedEntities)

                // Flush should be no-op
                session.flush()

                // Verify never inserted in database
                val stmt = connection.createStatement()
                val result = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE id = 2200")
                assertTrue(result.next())
                assertEquals(0, result.getInt(1))
                result.close()
                stmt.close()
            }
        }
    }

    @Test
    fun `delete PENDING_UPDATE entity - marks for DELETE instead`() {
        // Insert test entity
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (2300, 'Original', 'original@example.com')
                """.trimIndent()
            )
        }

        // Load, modify, then delete
        withConnection {
            EntitySession(this.connection).use { session ->
                // Load and modify
                val user = session.find<User>(2300)!!
                val modified = user.copy(name = "Modified")
                session.save<com.obabichev.kodama.tests.entity.User, Int>(modified)

                // Verify pending update
                assertEquals(1, session.stats().pendingUpdates)

                // Delete (should override pending update)
                session.delete(modified)

                // Should be marked for delete, not update
                val stats = session.stats()
                assertEquals(0, stats.pendingUpdates)
                assertEquals(1, stats.pendingDeletes)

                // Flush
                session.flush()

                // Verify deleted (modification never applied)
                val stmt = connection.createStatement()
                val result = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE id = 2300")
                assertTrue(result.next())
                assertEquals(0, result.getInt(1))
                result.close()
                stmt.close()
            }
        }
    }

    @Test
    fun `INSERT UPDATE and DELETE in same flush`() {
        // Insert existing entity
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (2400, 'Existing', 'existing@example.com')
                """.trimIndent()
            )
        }

        // Mix all operations
        withConnection {
            EntitySession(this.connection).use { session ->
                // INSERT: Create new entity
                val newUser = User(2401, "NewUser", "new@example.com")
                session.save<com.obabichev.kodama.tests.entity.User, Int>(newUser)

                // UPDATE: Load and modify
                val existing = session.find<User>(2400)!!
                val modified = existing.copy(name = "Updated")
                session.save<com.obabichev.kodama.tests.entity.User, Int>(modified)

                // DELETE: Load and delete another
                executeUpdate("INSERT INTO users (id, name, email) VALUES (2402, 'ToDelete', 'delete@example.com')")
                val toDelete = session.find<User>(2402)!!
                session.delete(toDelete)

                // Check stats before flush
                val statsBeforeFlush = session.stats()
                assertEquals(1, statsBeforeFlush.pendingInserts)
                assertEquals(1, statsBeforeFlush.pendingUpdates)
                assertEquals(1, statsBeforeFlush.pendingDeletes)

                // Flush all
                session.flush()

                // Check stats after flush
                val statsAfterFlush = session.stats()
                assertEquals(0, statsAfterFlush.pendingInserts)
                assertEquals(0, statsAfterFlush.pendingUpdates)
                assertEquals(0, statsAfterFlush.pendingDeletes)

                // Verify results
                val stmt = connection.createStatement()

                // Check INSERT
                val r1 = stmt.executeQuery("SELECT name FROM users WHERE id = 2401")
                assertTrue(r1.next())
                assertEquals("NewUser", r1.getString("name"))
                r1.close()

                // Check UPDATE
                val r2 = stmt.executeQuery("SELECT name FROM users WHERE id = 2400")
                assertTrue(r2.next())
                assertEquals("Updated", r2.getString("name"))
                r2.close()

                // Check DELETE
                val r3 = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE id = 2402")
                assertTrue(r3.next())
                assertEquals(0, r3.getInt(1))
                r3.close()

                stmt.close()
            }
        }
    }

    @Test
    fun `delete entity twice - second delete is no-op`() {
        // Insert test entity
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (2500, 'DoubleDelete', 'double@example.com')
                """.trimIndent()
            )
        }

        // Delete twice
        withConnection {
            EntitySession(this.connection).use { session ->
                val user = session.find<User>(2500)!!

                // First delete
                session.delete(user)
                assertEquals(1, session.stats().pendingDeletes)

                // Second delete (no-op)
                session.delete(user)
                assertEquals(1, session.stats().pendingDeletes)  // Still 1

                // Flush
                session.flush()

                // Verify deleted once
                val stmt = connection.createStatement()
                val result = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE id = 2500")
                assertTrue(result.next())
                assertEquals(0, result.getInt(1))
                result.close()
                stmt.close()
            }
        }
    }

    @Test
    fun `entity removed from identity map after delete flush`() {
        // Insert test entity
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (2600, 'RemoveFromCache', 'remove@example.com')
                """.trimIndent()
            )
        }

        // Delete and verify removal from cache
        withConnection {
            EntitySession(this.connection).use { session ->
                // Load (adds to identity map)
                val user = session.find<User>(2600)!!
                assertEquals(1, session.stats().cachedEntities)

                // Delete and flush
                session.delete(user)
                session.flush()

                // Should be removed from cache
                assertEquals(0, session.stats().cachedEntities)

                // Try to find again - should return null (not in cache, not in DB)
                val notFound = session.find<User>(2600)
                assertNull(notFound, "Deleted entity should not be found")
            }
        }
    }

    @Test
    fun `get entity by id - entity exists - returns non-null`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (3000, 'GetTest', 'gettest@example.com')
                """.trimIndent()
            )
        }

        // Test get() method
        withConnection {
            EntitySession(this.connection).use { session ->
                // Get user - should not require !!
                val user = session.get<User>(3000)

                // Verify
                assertEquals(3000, user.id)
                assertEquals("GetTest", user.name)
                assertEquals("gettest@example.com", user.email)
            }
        }
    }

    @Test
    fun `get entity by id - entity does not exist - throws exception`() {
        withConnection {
            EntitySession(this.connection).use { session ->
                // Try to get non-existent user - should throw
                val exception = kotlin.runCatching {
                    session.get<User>(9999)
                }.exceptionOrNull()

                // Verify exception is thrown
                assertNotNull(exception, "Should throw exception for non-existent entity")
                assertTrue(
                    exception is com.obabichev.kodama.entity.EntityNotFoundException,
                    "Should throw EntityNotFoundException"
                )

                val entityNotFound = exception as com.obabichev.kodama.entity.EntityNotFoundException
                assertEquals("User", entityNotFound.entityType)
                assertEquals(9999, entityNotFound.id)
                assertTrue(
                    entityNotFound.message?.contains("User") == true,
                    "Exception message should mention entity type"
                )
                assertTrue(
                    entityNotFound.message?.contains("9999") == true,
                    "Exception message should mention ID"
                )
            }
        }
    }

    @Test
    fun `get same entity twice - returns same instance from identity map`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (3001, 'GetCache', 'getcache@example.com')
                """.trimIndent()
            )
        }

        // Test identity map with get()
        withConnection {
            EntitySession(this.connection).use { session ->
                // Get user first time
                val user1 = session.get<User>(3001)

                // Get same user second time
                val user2 = session.get<User>(3001)

                // Verify same instance (identity map hit)
                assertTrue(
                    user1 === user2,
                    "Identity map should return the same instance for the same ID"
                )
            }
        }
    }

    @Test
    fun `get vs find - both work correctly for same entity`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (3002, 'FindGet', 'findget@example.com')
                """.trimIndent()
            )
        }

        // Test mixing find() and get()
        withConnection {
            EntitySession(this.connection).use { session ->
                // Find first (returns nullable)
                val foundUser = session.find<User>(3002)
                assertNotNull(foundUser)

                // Get same user (returns non-null)
                val gotUser = session.get<User>(3002)

                // Should be same instance from identity map
                assertTrue(
                    foundUser === gotUser,
                    "find() and get() should return same instance from identity map"
                )

                assertEquals("FindGet", gotUser.name)
            }
        }
    }
}
