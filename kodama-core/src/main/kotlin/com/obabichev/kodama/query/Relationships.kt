package com.obabichev.kodama.query

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.schema.Table

/**
 * Represents a declared relationship between two tables for query joins.
 * This is distinct from entity relationships - these are used for compile-time join validation.
 */
sealed interface QueryRelationship<Target : Table, FK, PK> {
    val name: String?
    val targetTable: Target
    val foreignKey: Column<FK>
    val primaryKey: Column<PK>
    val joinType: JoinType
}

/**
 * One-to-many relationship: One record in source table has many records in target table.
 * Example: Person (one) → Orders (many)
 *
 * @param Target The target table type
 * @param FK The foreign key type
 * @param PK The primary key type
 */
data class OneToMany<Target : Table, FK, PK>(
    override val name: String?,
    override val targetTable: Target,
    override val foreignKey: Column<FK>,
    override val primaryKey: Column<PK>,
    override val joinType: JoinType = JoinType.INNER
) : QueryRelationship<Target, FK, PK>

/**
 * Many-to-one relationship: Many records in source table reference one record in target table.
 * Example: Order (many) → Person (one)
 *
 * @param Target The target table type
 * @param FK The foreign key type
 * @param PK The primary key type
 */
data class ManyToOne<Target : Table, FK, PK>(
    override val name: String?,
    override val targetTable: Target,
    override val foreignKey: Column<FK>,
    override val primaryKey: Column<PK>,
    override val joinType: JoinType = JoinType.INNER
) : QueryRelationship<Target, FK, PK>

/**
 * Join type enumeration.
 */
enum class JoinType {
    INNER,
    LEFT,
    RIGHT,
    FULL
}

/**
 * DSL function to declare a one-to-many relationship on a table.
 * Registers the relationship for code generation.
 *
 * Example:
 * ```kotlin
 * object Person : Table("person") {
 *     val name = varchar("name", 255).primaryKey()
 *
 *     // Declare that Person has many Orders
 *     val orders = oneToMany(Order, Order.userName, this.name)
 * }
 * ```
 *
 * @param Target The target table type
 * @param FK The foreign key column type
 * @param PK The primary key column type
 * @param target The target table instance
 * @param foreignKey The foreign key column in the target table
 * @param primaryKey The primary key column in this table
 * @param name Optional custom relationship name
 * @return The registered relationship
 */
fun <Target : Table, FK, PK> Table.oneToMany(
    target: Target,
    foreignKey: Column<FK>,
    primaryKey: Column<PK>,
    name: String? = null
): OneToMany<Target, FK, PK> {
    val relationship = OneToMany(name, target, foreignKey, primaryKey)
    registerQueryRelationship(relationship)
    return relationship
}

/**
 * DSL function to declare a many-to-one relationship on a table.
 * Registers the relationship for code generation.
 *
 * Example:
 * ```kotlin
 * object Order : Table("order") {
 *     val id = integer("id").primaryKey()
 *     val userName = varchar("user_name", 255)
 *
 *     // Declare that Order belongs to one Person
 *     val person = manyToOne(Person, this.userName, Person.name)
 * }
 * ```
 *
 * @param Target The target table type
 * @param FK The foreign key column type
 * @param PK The primary key column type
 * @param target The target table instance
 * @param foreignKey The foreign key column in this table
 * @param primaryKey The primary key column in the target table
 * @param name Optional custom relationship name
 * @return The registered relationship
 */
fun <Target : Table, FK, PK> Table.manyToOne(
    target: Target,
    foreignKey: Column<FK>,
    primaryKey: Column<PK>,
    name: String? = null
): ManyToOne<Target, FK, PK> {
    val relationship = ManyToOne(name, target, foreignKey, primaryKey)
    registerQueryRelationship(relationship)
    return relationship
}

/**
 * Internal: Register a query relationship on a table.
 * This is called by the DSL functions and stores the relationship for code generation.
 */
private fun Table.registerQueryRelationship(relationship: QueryRelationship<*, *, *>) {
    QueryRelationshipRegistry.register(this, relationship)
}

/**
 * Global registry of query relationships.
 * Used by code generator to discover declared relationships.
 */
object QueryRelationshipRegistry {
    private val relationships = mutableMapOf<Table, MutableList<QueryRelationship<*, *, *>>>()

    /**
     * Register a relationship for a table.
     */
    fun register(table: Table, relationship: QueryRelationship<*, *, *>) {
        relationships.getOrPut(table) { mutableListOf() }.add(relationship)
    }

    /**
     * Get all relationships declared on a table.
     */
    fun getRelationships(table: Table): List<QueryRelationship<*, *, *>> {
        return relationships[table]?.toList() ?: emptyList()
    }

    /**
     * Get all tables with declared relationships.
     */
    fun getAllTables(): Set<Table> {
        return relationships.keys
    }

    /**
     * Clear all relationships (for testing).
     */
    fun clear() {
        relationships.clear()
    }
}
