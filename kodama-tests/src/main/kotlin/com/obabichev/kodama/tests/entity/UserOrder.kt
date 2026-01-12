package com.obabichev.kodama.tests.entity

import com.obabichev.kodama.entity.EntitySession

/**
 * UserOrder entity - represents an order placed by a user.
 * Used to test interface-based entities and relationships.
 *
 * This interface defines the contract for UserOrder entities including relationships.
 * The implementation and factory function are generated automatically.
 * Relationship method implementations are generated from EntityTable declarations.
 */
interface UserOrder {
    val id: Int
    val userId: Int  // Foreign key to Users.id
    val product: String
    val amount: Int

    /**
     * Get the user who placed this order (many-to-one relationship).
     * Implementation is generated based on UserOrders.user manyToOne declaration.
     */
    fun user(session: EntitySession): User
}
