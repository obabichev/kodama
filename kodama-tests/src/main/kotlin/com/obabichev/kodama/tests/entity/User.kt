package com.obabichev.kodama.tests.entity

import com.obabichev.kodama.entity.EntitySession

/**
 * User entity - example domain entity for testing entity layer.
 *
 * This interface defines the contract for User entities including relationships.
 * The implementation and factory function are generated automatically.
 * Relationship method implementations are generated from EntityTable oneToMany declarations.
 */
interface User {
    val id: Int
    val name: String
    val email: String

    /**
     * Get all orders for this user.
     * Implementation is generated based on Users.orders oneToMany declaration.
     */
    fun orders(session: EntitySession): List<UserOrder>
}
