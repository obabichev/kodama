package com.obabichev.kodama.tests

import com.obabichev.kodama.entity.EntitySession
import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.tests.entity.User
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Users
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Test that demonstrates ZERO boilerplate auto-discovery.
 *
 * Notice:
 * - NO companion object
 * - NO KodamaBindingRegistry reference
 * - NO EntitySession.autoBindingProvider setup
 *
 * Entities just work! ✅
 */
class AutoDiscoveryTest : DatabaseTest() {

    // ✅ NO companion object needed!
    // EntitySession automatically discovers and initializes the registry

    override fun requiredTables(): List<Table> = listOf(Users)

    @Test
    fun `entities work with ZERO boilerplate`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (42, 'Bob', 'bob@example.com')
                """.trimIndent()
            )
        }

        // Test entity loading
        withConnection {
            EntitySession(this.connection).use { session ->
                // ✅ This just works - NO setup required at all!
                val user = session.find<User>(42)

                assertNotNull(user, "User should be found")
                assertEquals(42, user.id)
                assertEquals("Bob", user.name)
                assertEquals("bob@example.com", user.email)

                println("✅ SUCCESS: Entity loaded with ZERO boilerplate!")
                println("   User: ${user.name}, Email: ${user.email}")
                println("   No companion object, no imports, no setup - it just works!")
            }
        }
    }
}
