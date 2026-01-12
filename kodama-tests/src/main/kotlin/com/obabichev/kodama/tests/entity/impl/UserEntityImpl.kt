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
 * Internal implementation of User entity.
 * Generated automatically by Kodama compiler plugin.
 */
private data class UserImpl(
    override val id: Int,
    override val name: String,
    override val email: String
) : User {
    override fun orders(session: EntitySession): List<UserOrder> {
        // Load related UserOrders using findByForeignKey
        return session.findByForeignKey<UserOrder, Int, Int>(
            UserOrders,
            UserOrders.userId,
            id
        )
    }
}

/**
 * Factory function for creating User entities.
 * Used by tests to create new User instances.
 */
fun User(id: Int, name: String, email: String): User {
    return UserImpl(id, name, email)
}

/**
 * Copy method for User entities.
 * Allows immutable updates.
 */
fun User.copy(
    id: Int = this.id,
    name: String = this.name,
    email: String = this.email
): User {
    return UserImpl(id, name, email)
}

/**
 * Entity binding for User.
 * Maps between User entities and database rows.
 */
object UserEntityBinding : EntityBinding<User, Int> {
    override val table = Users

    override fun entityId(entity: User): Int = entity.id

    override fun toEntity(resultSet: ResultSet): User {
        return UserImpl(
            id = resultSet.getInt("id"),
            name = resultSet.getString("name"),
            email = resultSet.getString("email")
        )
    }

    override fun toInsertValues(entity: User): Map<Column<*>, Any?> {
        return mapOf(
            Users.id to entity.id,
            Users.name to entity.name,
            Users.email to entity.email
        )
    }

    override fun toUpdateValues(entity: User, original: User): Map<Column<*>, Any?> {
        val changes = mutableMapOf<Column<*>, Any?>()
        if (entity.name != original.name) {
            changes[Users.name] = entity.name
        }
        if (entity.email != original.email) {
            changes[Users.email] = entity.email
        }
        return changes
    }

    override fun primaryKeyColumns(): List<Column<*>> {
        return listOf(Users.id)
    }
}
