package com.obabichev.kodama.tests.entity

/**
 * UserRole junction entity - links Users and Roles for many-to-many relationships.
 *
 * This entity represents the join/junction table that connects users to roles.
 * It holds foreign keys to both sides of the many-to-many relationship.
 */
interface UserRole {
    val userId: Int
    val roleId: Int
}
