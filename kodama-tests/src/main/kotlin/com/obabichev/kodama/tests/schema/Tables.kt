package com.obabichev.kodama.tests.schema

import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.schema.EntityTable
import com.obabichev.kodama.schema.primaryKey
import com.obabichev.kodama.schema.nullable
import com.obabichev.kodama.tests.entity.User
import com.obabichev.kodama.tests.entity.UserOrder
import com.obabichev.kodama.entity.oneToMany
import com.obabichev.kodama.entity.manyToOne

/**
 * Person table definition
 */
object Person : Table("person") {
    val name = varchar("name", 255).primaryKey()
    val age = integer("age")
}

/**
 * Order table definition
 */
object Order : Table("order") {
    val id = integer("id").primaryKey()
    val userName = varchar("user_name", 255)
    val product = varchar("product", 255)
    val cost = integer("cost")
}

/**
 * Profile table definition
 */
object Profile : Table("profile") {
    val userName = varchar("user_name", 255)
    val contact = varchar("contact", 255)
    val photo = varchar("photo", 255).nullable()  // Photo can be null
}

/**
 * Company table definition
 */
object Company : Table("company") {
    val id = integer("id").primaryKey()
    val companyName = varchar("company_name", 255)
}

/**
 * Product table definition with nullable columns for testing
 */
object Product : Table("product") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)
    val description = varchar("description", 500).nullable()  // Optional description
    val price = integer("price")
    val discount = integer("discount").nullable()  // Optional discount percentage
}

/**
 * Settings table for testing boolean column types
 */
object Settings : Table("settings") {
    val id = integer("id").primaryKey()
    val key = varchar("key", 255)
    val enabled = boolean("enabled")
    val verified = boolean("verified").nullable()  // Optional boolean
}

/**
 * Numerics table for testing all numeric column types
 */
object Numerics : Table("numerics") {
    val id = integer("id").primaryKey()
    val smallIntValue = smallint("small_int_value")
    val intValue = integer("int_value")
    val bigIntValue = bigint("big_int_value")
    val decimalValue = decimal("decimal_value", 10, 2)  // NUMERIC(10,2)
    val realValue = real("real_value")
    val doubleValue = doublePrecision("double_value")

    // Nullable columns
    val nullableSmallInt = smallint("nullable_small_int").nullable()
    val nullableBigInt = bigint("nullable_big_int").nullable()
    val nullableDecimal = decimal("nullable_decimal", 15, 4).nullable()
    val nullableReal = real("nullable_real").nullable()
    val nullableDouble = doublePrecision("nullable_double").nullable()
}

/**
 * UserOrders entity table - orders placed by users.
 * Demonstrates one-to-many relationship with Users.
 *
 * Relationship:
 * - Users (1) → UserOrders (N)
 * - One user can have many orders
 * - userId foreign key references Users.id
 *
 * Usage:
 * ```kotlin
 * EntitySession(connection).use { session ->
 *     val user = session.find<User>(1)!!
 *     val orders = user.orders(session)  // Load related orders
 * }
 * ```
 */
object UserOrders : EntityTable<UserOrder>("user_orders") {
    val id = integer("id").primaryKey()
    val userId = integer("user_id")  // FK to Users.id
    val product = varchar("product", 255)
    val amount = integer("amount")

    /**
     * Many-to-one relationship: UserOrder belongs to one User.
     * Defined in init block to ensure columns are initialized first.
     */
    init {
        manyToOne("user", Users, this.userId, Users.id)
    }
}

/**
 * Users entity table - example EntityTable with ORM support.
 *
 * This table demonstrates the entity layer:
 * - Extends EntityTable<User> instead of Table
 * - Generic parameter User explicitly connects table to entity class
 * - Can be used in queries like regular tables
 * - Can be used with EntitySession for entity loading/saving
 *
 * Example usage:
 * ```kotlin
 * // Query DSL (works like regular tables)
 * query().from(Users).select { +users.name }
 *
 * // Entity layer
 * EntitySession(connection).use { session ->
 *     session.registerBinding(Users, UserEntityBinding)
 *     val user = session.find(Users, 1)
 * }
 * ```
 */
object Users : EntityTable<User>("users") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 255)
    val email = varchar("email", 255)

    /**
     * One-to-many relationship: User has many UserOrders.
     * Defined in init block to ensure columns are initialized first.
     */
    init {
        oneToMany("orders", UserOrders, UserOrders.userId, this.id)
    }
}

/**
 * TradingStrategy table - PascalCase naming test.
 * Tests that PascalCase table names are preserved correctly.
 */
object TradingStrategy : Table("trading_strategy") {
    val id = integer("id").primaryKey()
    val strategyName = varchar("strategy_name", 255)
    val description = varchar("description", 500).nullable()
}

/**
 * MarketData table - PascalCase naming test.
 * Tests that PascalCase table names work in joins.
 */
object MarketData : Table("market_data") {
    val id = integer("id").primaryKey()
    val strategyId = integer("strategy_id")
    val timestamp = varchar("timestamp", 50)
    val price = integer("price")
}

/**
 * Events table - DateTime column types testing.
 * Tests date, time, timestamp, and interval column types.
 */
object Events : Table("events") {
    val id = integer("id").primaryKey()
    val eventDate = date("event_date")
    val eventTime = time("event_time")
    val createdAt = timestamp("created_at")
    val scheduledFor = timestamp("scheduled_for").nullable()
    val eventTimestamp = timestampWithTimeZone("event_timestamp")
    val reminderTime = timeWithTimeZone("reminder_time")
    val duration = interval("duration")
    val optionalDuration = interval("optional_duration").nullable()
}
