package com.obabichev.kodama.entity

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.schema.EntityTable

/**
 * Define a one-to-many relationship on an EntityTable.
 *
 * This function creates a relationship where one entity (source) has many related entities (target).
 * The relationship is defined by a foreign key in the target table that references the primary key
 * in the source table.
 *
 * Usage:
 * ```kotlin
 * object Users : EntityTable<User>("users") {
 *     val id = integer("id").primaryKey()
 *     val name = varchar("name", 255)
 *
 *     init {
 *         oneToMany("orders", UserOrders, UserOrders.userId, this.id)
 *     }
 * }
 * ```
 *
 * This will generate an extension method:
 * ```kotlin
 * fun User.orders(session: EntitySession): List<UserOrder>
 * ```
 *
 * @param E Entity type of the "many" side
 * @param FK Foreign key/primary key type (must match)
 * @param name Relationship name (used for generated accessor method)
 * @param targetTable The target EntityTable (the "many" side)
 * @param foreignKeyColumn Column in target table that references source table
 * @param primaryKeyColumn Primary key column in source table
 * @return The created relationship metadata
 */
fun <E : Any, FK : Any> EntityTable<*>.oneToMany(
    name: String,
    targetTable: EntityTable<E>,
    foreignKeyColumn: Column<FK>,
    primaryKeyColumn: Column<FK>
): OneToManyRelationship<E, FK> {
    val relationship = OneToManyRelationship(
        name = name,
        targetTable = targetTable,
        foreignKeyColumn = foreignKeyColumn,
        primaryKeyColumn = primaryKeyColumn
    )

    registerOneToMany(relationship)
    return relationship
}

/**
 * Define a many-to-one relationship on an EntityTable.
 *
 * This function creates a relationship where many entities (source) reference one related entity (target).
 * The relationship is defined by a foreign key in the source table that references the primary key
 * in the target table.
 *
 * Usage:
 * ```kotlin
 * object UserOrders : EntityTable<UserOrder>("user_orders") {
 *     val id = integer("id").primaryKey()
 *     val userId = integer("user_id")
 *     val product = varchar("product", 255)
 *
 *     init {
 *         manyToOne("user", Users, this.userId, Users.id)
 *     }
 * }
 * ```
 *
 * This will generate an extension method:
 * ```kotlin
 * fun UserOrder.user(session: EntitySession): User
 * ```
 *
 * @param E Entity type of the "one" side
 * @param FK Foreign key/primary key type (must match)
 * @param name Relationship name (used for generated accessor method)
 * @param targetTable The target EntityTable (the "one" side)
 * @param foreignKeyColumn Column in source table that references target table
 * @param primaryKeyColumn Primary key column in target table
 * @return The created relationship metadata
 */
fun <E : Any, FK : Any> EntityTable<*>.manyToOne(
    name: String,
    targetTable: EntityTable<E>,
    foreignKeyColumn: Column<FK>,
    primaryKeyColumn: Column<FK>
): ManyToOneRelationship<E, FK> {
    val relationship = ManyToOneRelationship(
        name = name,
        targetTable = targetTable,
        foreignKeyColumn = foreignKeyColumn,
        primaryKeyColumn = primaryKeyColumn
    )

    registerManyToOne(relationship)
    return relationship
}
