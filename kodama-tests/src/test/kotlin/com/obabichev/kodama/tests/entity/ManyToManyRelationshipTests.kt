package com.obabichev.kodama.tests.entity

import com.obabichev.kodama.entity.EntitySession
import com.obabichev.kodama.tests.entity.User
import com.obabichev.kodama.tests.entity.Role
import com.obabichev.kodama.tests.entity.UserRole
import com.obabichev.kodama.tests.entity.generated.*
import com.obabichev.kodama.tests.infrastructure.DatabaseTest
import com.obabichev.kodama.tests.schema.Users
import com.obabichev.kodama.tests.schema.Roles
import com.obabichev.kodama.tests.schema.UserRoles
import com.obabichev.kodama.tests.KodamaBindingRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for many-to-many relationships (Feature 5.2).
 *
 * Tests entity many-to-many relationship functionality:
 * - Loading entities through junction tables
 * - Bidirectional many-to-many relationships (User ↔ Role)
 * - Multiple related entities
 * - Empty relationships
 */
class ManyToManyRelationshipTests : DatabaseTest() {

    companion object {
        // Ensure the binding registry is loaded to enable auto-registration
        private val initRegistry = KodamaBindingRegistry
    }

    override fun requiredTables() = listOf(Users, Roles, UserRoles)

    // ========================================
    // Basic Many-to-Many Tests
    // ========================================

    @Test
    fun `many-to-many - user with single role`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (6000, 'SingleRoleUser', 'single@example.com')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO roles (id, name)
                VALUES (100, 'Admin')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO user_roles (user_id, role_id)
                VALUES (6000, 100)
                """.trimIndent()
            )
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                val user = session.find<User>(6000)!!
                val roles = user.roles(session)

                assertEquals(1, roles.size)
                assertEquals(100, roles[0].id)
                assertEquals("Admin", roles[0].name)
            }
        }
    }

    @Test
    fun `many-to-many - user with multiple roles`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (6100, 'MultiRoleUser', 'multi@example.com')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO roles (id, name)
                VALUES
                    (101, 'Admin'),
                    (102, 'Editor'),
                    (103, 'Viewer')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO user_roles (user_id, role_id)
                VALUES
                    (6100, 101),
                    (6100, 102),
                    (6100, 103)
                """.trimIndent()
            )
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                val user = session.find<User>(6100)!!
                val roles = user.roles(session)

                assertEquals(3, roles.size)

                val roleNames = roles.map { it.name }.sorted()
                assertEquals(listOf("Admin", "Editor", "Viewer"), roleNames)

                val roleIds = roles.map { it.id }.sorted()
                assertEquals(listOf(101, 102, 103), roleIds)
            }
        }
    }

    @Test
    fun `many-to-many - user with no roles`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (6200, 'NoRoleUser', 'norole@example.com')
                """.trimIndent()
            )
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                val user = session.find<User>(6200)!!
                val roles = user.roles(session)

                assertEquals(0, roles.size)
                assertTrue(roles.isEmpty())
            }
        }
    }

    // ========================================
    // Bidirectional Relationship Tests
    // ========================================

    @Test
    fun `many-to-many - role with single user`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (6300, 'AdminUser', 'admin@example.com')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO roles (id, name)
                VALUES (110, 'SuperAdmin')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO user_roles (user_id, role_id)
                VALUES (6300, 110)
                """.trimIndent()
            )
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                val role = session.find<Role>(110)!!
                val users = role.users(session)

                assertEquals(1, users.size)
                assertEquals(6300, users[0].id)
                assertEquals("AdminUser", users[0].name)
            }
        }
    }

    @Test
    fun `many-to-many - role with multiple users`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES
                    (6400, 'User1', 'user1@example.com'),
                    (6401, 'User2', 'user2@example.com'),
                    (6402, 'User3', 'user3@example.com')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO roles (id, name)
                VALUES (111, 'TeamMember')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO user_roles (user_id, role_id)
                VALUES
                    (6400, 111),
                    (6401, 111),
                    (6402, 111)
                """.trimIndent()
            )
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                val role = session.find<Role>(111)!!
                val users = role.users(session)

                assertEquals(3, users.size)

                val userNames = users.map { it.name }.sorted()
                assertEquals(listOf("User1", "User2", "User3"), userNames)

                val userIds = users.map { it.id }.sorted()
                assertEquals(listOf(6400, 6401, 6402), userIds)
            }
        }
    }

    @Test
    fun `many-to-many - role with no users`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO roles (id, name)
                VALUES (112, 'UnusedRole')
                """.trimIndent()
            )
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                val role = session.find<Role>(112)!!
                val users = role.users(session)

                assertEquals(0, users.size)
                assertTrue(users.isEmpty())
            }
        }
    }

    // ========================================
    // Complex Scenario Tests
    // ========================================

    @Test
    fun `many-to-many - multiple users with overlapping roles`() {
        // Insert test data: User1 has Admin+Editor, User2 has Editor+Viewer, User3 has Admin+Viewer
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES
                    (6500, 'User1', 'user1@example.com'),
                    (6501, 'User2', 'user2@example.com'),
                    (6502, 'User3', 'user3@example.com')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO roles (id, name)
                VALUES
                    (120, 'Admin'),
                    (121, 'Editor'),
                    (122, 'Viewer')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO user_roles (user_id, role_id)
                VALUES
                    (6500, 120), (6500, 121),  -- User1: Admin, Editor
                    (6501, 121), (6501, 122),  -- User2: Editor, Viewer
                    (6502, 120), (6502, 122)   -- User3: Admin, Viewer
                """.trimIndent()
            )
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                // Check User1's roles
                val user1 = session.find<User>(6500)!!
                val user1Roles = user1.roles(session).map { it.name }.sorted()
                assertEquals(listOf("Admin", "Editor"), user1Roles)

                // Check User2's roles
                val user2 = session.find<User>(6501)!!
                val user2Roles = user2.roles(session).map { it.name }.sorted()
                assertEquals(listOf("Editor", "Viewer"), user2Roles)

                // Check User3's roles
                val user3 = session.find<User>(6502)!!
                val user3Roles = user3.roles(session).map { it.name }.sorted()
                assertEquals(listOf("Admin", "Viewer"), user3Roles)

                // Check Admin role's users
                val adminRole = session.find<Role>(120)!!
                val adminUsers = adminRole.users(session).map { it.name }.sorted()
                assertEquals(listOf("User1", "User3"), adminUsers)

                // Check Editor role's users
                val editorRole = session.find<Role>(121)!!
                val editorUsers = editorRole.users(session).map { it.name }.sorted()
                assertEquals(listOf("User1", "User2"), editorUsers)

                // Check Viewer role's users
                val viewerRole = session.find<Role>(122)!!
                val viewerUsers = viewerRole.users(session).map { it.name }.sorted()
                assertEquals(listOf("User2", "User3"), viewerUsers)
            }
        }
    }

    @Test
    fun `many-to-many - loading relationships uses identity map`() {
        // Insert test data
        withConnection {
            executeUpdate(
                """
                INSERT INTO users (id, name, email)
                VALUES (6600, 'IdentityUser', 'identity@example.com')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO roles (id, name)
                VALUES (130, 'IdentityRole')
                """.trimIndent()
            )
            executeUpdate(
                """
                INSERT INTO user_roles (user_id, role_id)
                VALUES (6600, 130)
                """.trimIndent()
            )
        }

        withConnection {
            EntitySession(this.connection).use { session ->
                val user = session.find<User>(6600)!!

                // Load roles first time
                val roles1 = user.roles(session)
                assertEquals(1, roles1.size)

                // Load roles second time - should use identity map
                val roles2 = user.roles(session)
                assertEquals(1, roles2.size)

                // Both should be the exact same instance (identity map guarantee)
                assertTrue(roles1[0] === roles2[0], "Role should be same instance from identity map")
            }
        }
    }
}
