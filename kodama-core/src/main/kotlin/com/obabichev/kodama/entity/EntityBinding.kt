package com.obabichev.kodama.entity

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.schema.EntityTable
import java.sql.ResultSet
import kotlin.reflect.KClass

/**
 * Binding between an entity type and its database table.
 *
 * This interface defines how to:
 * - Convert database rows to entity instances (toEntity)
 * - Convert entity instances to column-value pairs for INSERT (toInsertValues)
 * - Extract the primary key from an entity (entityId)
 *
 * In Phase 1, bindings are written manually.
 * In Phase 5, bindings will be generated automatically by code generator.
 *
 * Example implementation:
 * ```kotlin
 * data class User(val id: Int, val name: String, val email: String)
 *
 * object Users : EntityTable<User>("users") {
 *     val id = integer("id").primaryKey()
 *     val name = varchar("name", 255)
 *     val email = varchar("email", 255)
 * }
 *
 * object UserEntityBinding : EntityBinding<User, Int> {
 *     override val table = Users
 *
 *     override fun entityId(entity: User): Int = entity.id
 *
 *     override fun toEntity(resultSet: ResultSet): User {
 *         return User(
 *             id = resultSet.getInt("id"),
 *             name = resultSet.getString("name"),
 *             email = resultSet.getString("email")
 *         )
 *     }
 *
 *     override fun toInsertValues(entity: User): Map<Column<*>, Any?> {
 *         return mapOf(
 *             Users.id to entity.id,
 *             Users.name to entity.name,
 *             Users.email to entity.email
 *         )
 *     }
 *
 *     override fun primaryKeyColumns(): List<Column<*>> {
 *         return listOf(Users.id)
 *     }
 * }
 * ```
 *
 * @param E Entity type (e.g., User)
 * @param ID Primary key type (e.g., Int, Long, String)
 */
interface EntityBinding<E : Any, ID : Any> {

    /**
     * The entity class this binding is for.
     * Used for automatic binding registration.
     */
    val entityClass: KClass<E>

    /**
     * The EntityTable this binding is for.
     */
    val table: EntityTable<E>

    /**
     * Extract the primary key value from an entity.
     *
     * @param entity The entity instance
     * @return The primary key value
     */
    fun entityId(entity: E): ID

    /**
     * Convert a database row (ResultSet) to an entity instance.
     *
     * The ResultSet cursor should already be positioned at the row to read.
     *
     * @param resultSet The result set positioned at the row
     * @return The entity instance
     */
    fun toEntity(resultSet: ResultSet): E

    /**
     * Convert an entity to column-value pairs for INSERT statement.
     *
     * @param entity The entity instance
     * @return Map of columns to their values
     */
    fun toInsertValues(entity: E): Map<Column<*>, Any?>

    /**
     * Convert entity changes to column-value pairs for UPDATE statement.
     *
     * Phase 3: Compares current entity with original snapshot and returns
     * only the changed fields. Primary key columns should NOT be included.
     *
     * Example:
     * ```kotlin
     * override fun toUpdateValues(entity: User, original: User): Map<Column<*>, Any?> {
     *     val changes = mutableMapOf<Column<*>, Any?>()
     *     if (entity.name != original.name) {
     *         changes[Users.name] = entity.name
     *     }
     *     if (entity.email != original.email) {
     *         changes[Users.email] = entity.email
     *     }
     *     return changes
     * }
     * ```
     *
     * @param entity The current entity instance with modifications
     * @param original The original snapshot of the entity from database
     * @return Map of changed columns to their new values (excluding primary key)
     */
    fun toUpdateValues(entity: E, original: E): Map<Column<*>, Any?>

    /**
     * Get the primary key column(s) for this entity.
     *
     * Phase 1 supports single-column primary keys only.
     * Future: Support composite keys.
     *
     * @return List of primary key columns
     */
    fun primaryKeyColumns(): List<Column<*>>
}
