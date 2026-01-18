package com.obabichev.kodama.tests.entity

import com.obabichev.kodama.entity.EntitySession

/**
 * Role entity - for testing many-to-many relationships.
 *
 * This interface defines the contract for Role entities.
 * Users can have many roles through the UserRole junction table.
 */
interface Role {
    val id: Int
    val name: String

    /**
     * Get all users that have this role.
     * Implementation is generated based on Roles.users manyToMany declaration.
     */
    fun users(session: EntitySession): List<User>
}
