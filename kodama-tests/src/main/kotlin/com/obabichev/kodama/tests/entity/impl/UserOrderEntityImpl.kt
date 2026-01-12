package com.obabichev.kodama.tests.entity.impl

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.entity.EntityBinding
import com.obabichev.kodama.entity.EntitySession
import com.obabichev.kodama.tests.entity.User
import com.obabichev.kodama.tests.entity.UserOrder
import com.obabichev.kodama.tests.schema.Users
import com.obabichev.kodama.tests.schema.UserOrders
import java.sql.ResultSet

/**
 * Internal implementation of UserOrder entity.
 * Generated automatically by Kodama compiler plugin.
 */
private data class UserOrderImpl(
    override val id: Int,
    override val userId: Int,
    override val product: String,
    override val amount: Int
) : UserOrder {
    override fun user(session: EntitySession): User {
        // Load related User by userId (many-to-one relationship)
        return session.find<User>(userId)
            ?: error("User with id $userId not found")
    }
}

/**
 * Factory function for creating UserOrder entities.
 * Used by tests to create new UserOrder instances.
 */
fun UserOrder(id: Int, userId: Int, product: String, amount: Int): UserOrder {
    return UserOrderImpl(id, userId, product, amount)
}

/**
 * Copy method for UserOrder entities.
 * Allows immutable updates.
 */
fun UserOrder.copy(
    id: Int = this.id,
    userId: Int = this.userId,
    product: String = this.product,
    amount: Int = this.amount
): UserOrder {
    return UserOrderImpl(id, userId, product, amount)
}

/**
 * Entity binding for UserOrder.
 * Maps between UserOrder entities and database rows.
 */
object UserOrderEntityBinding : EntityBinding<UserOrder, Int> {
    override val table = UserOrders

    override fun entityId(entity: UserOrder): Int = entity.id

    override fun toEntity(resultSet: ResultSet): UserOrder {
        return UserOrderImpl(
            id = resultSet.getInt("id"),
            userId = resultSet.getInt("user_id"),
            product = resultSet.getString("product"),
            amount = resultSet.getInt("amount")
        )
    }

    override fun toInsertValues(entity: UserOrder): Map<Column<*>, Any?> {
        return mapOf(
            UserOrders.id to entity.id,
            UserOrders.userId to entity.userId,
            UserOrders.product to entity.product,
            UserOrders.amount to entity.amount
        )
    }

    override fun toUpdateValues(entity: UserOrder, original: UserOrder): Map<Column<*>, Any?> {
        val changes = mutableMapOf<Column<*>, Any?>()
        if (entity.userId != original.userId) {
            changes[UserOrders.userId] = entity.userId
        }
        if (entity.product != original.product) {
            changes[UserOrders.product] = entity.product
        }
        if (entity.amount != original.amount) {
            changes[UserOrders.amount] = entity.amount
        }
        return changes
    }

    override fun primaryKeyColumns(): List<Column<*>> {
        return listOf(UserOrders.id)
    }
}
