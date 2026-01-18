package com.obabichev.kodama.tests.schema

import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.schema.EntityTable
import com.obabichev.kodama.schema.primaryKey
import com.obabichev.kodama.schema.nullable
import com.obabichev.kodama.schema.identity
import com.obabichev.kodama.tests.entity.User
import com.obabichev.kodama.tests.entity.UserOrder
import com.obabichev.kodama.tests.entity.Role
import com.obabichev.kodama.tests.entity.UserRole
import com.obabichev.kodama.entity.oneToMany as entityOneToMany
import com.obabichev.kodama.entity.manyToOne as entityManyToOne
import com.obabichev.kodama.entity.manyToMany as entityManyToMany
import com.obabichev.kodama.query.oneToMany
import com.obabichev.kodama.query.manyToOne

/**
 * Person table definition with query relationships
 */
object Person : Table("person") {
    val name = varchar("name", 255).primaryKey()
    val age = integer("age")

    // Query relationships for compile-time join validation
    val orders = oneToMany(Order, Order.userName, this.name)
    val profile = oneToMany(Profile, Profile.userName, this.name)
}

/**
 * Order table definition with query relationships
 */
object Order : Table("order") {
    val id = integer("id").primaryKey()
    val userName = varchar("user_name", 255)
    val product = varchar("product", 255)
    val cost = integer("cost")
    val companyId = integer("company_id").nullable()  // Optional company reference

    // Query relationships:
    // - Order belongs to Person (many-to-one)
    // - Order belongs to Company (many-to-one) - enables transitive joins: Person → Order → Company
    val person = manyToOne(Person, this.userName, Person.name)
    val company = manyToOne(Company, this.companyId, Company.id)
}

/**
 * Profile table definition with query relationships
 */
object Profile : Table("profile") {
    val userName = varchar("user_name", 255)
    val contact = varchar("contact", 255)
    val photo = varchar("photo", 255).nullable()  // Photo can be null

    // Query relationship: Profile belongs to Person
    val person = manyToOne(Person, this.userName, Person.name)
}

/**
 * Company table definition with query relationships
 */
object Company : Table("company") {
    val id = integer("id").primaryKey()
    val companyName = varchar("company_name", 255)

    // Query relationship: Company has many Orders
    val orders = oneToMany(Order, Order.companyId, this.id)
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
        entityManyToOne("user", Users, this.userId, Users.id)
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
 * from(Users).select { +users.name }
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
     * Relationships defined in init block to ensure columns are initialized first.
     */
    init {
        // One-to-many: User has many UserOrders
        entityOneToMany("orders", UserOrders, UserOrders.userId, this.id)

        // Many-to-many: User has many Roles through UserRoles junction table
        entityManyToMany(
            name = "roles",
            targetTable = Roles,
            junctionTable = UserRoles,
            sourceForeignKeyColumn = UserRoles.userId,
            targetForeignKeyColumn = UserRoles.roleId,
            sourcePrimaryKeyColumn = this.id,
            targetPrimaryKeyColumn = Roles.id
        )
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

    // Query relationship: TradingStrategy has many MarketData
    val marketDataPoints = oneToMany(MarketData, MarketData.strategyId, this.id)
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

    // Query relationship: MarketData belongs to TradingStrategy
    val strategy = manyToOne(TradingStrategy, this.strategyId, TradingStrategy.id)
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

/**
 * SerialTest table - Testing SERIAL auto-increment (PostgreSQL-specific).
 * The id column uses SERIAL type and is auto-generated by the database.
 * INSERT operations should NOT include the id parameter.
 */
object SerialTest : Table("serial_test") {
    val id = serial("id").primaryKey()
    val name = varchar("name", 255)
    val value = integer("value")
}

/**
 * IdentityTest table - Testing IDENTITY auto-increment (SQL standard).
 * The id column uses INTEGER with GENERATED ALWAYS AS IDENTITY modifier.
 * INSERT operations should NOT include the id parameter.
 */
object IdentityTest : Table("identity_test") {
    val id = integer("id").identity().primaryKey()
    val name = varchar("name", 255)
    val value = integer("value")
}

/**
 * BigSerialTest table - Testing BIGSERIAL for large auto-increment IDs.
 */
object BigSerialTest : Table("bigserial_test") {
    val id = bigserial("id").primaryKey()
    val description = varchar("description", 255)
}

/**
 * SmallSerialTest table - Testing SMALLSERIAL for small auto-increment IDs.
 */
object SmallSerialTest : Table("smallserial_test") {
    val id = smallserial("id").primaryKey()
    val tag = varchar("tag", 50)
}

object Org : Table("org") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 100)
}

/**
 * Roles entity table - for testing many-to-many relationships.
 */
object Roles : EntityTable<Role>("roles") {
    val id = integer("id").primaryKey()
    val name = varchar("name", 100)

    /**
     * Many-to-many relationship: Role has many Users through UserRoles junction table.
     * Defined in init block to ensure columns are initialized first.
     */
    init {
        entityManyToMany(
            name = "users",
            targetTable = Users,
            junctionTable = UserRoles,
            sourceForeignKeyColumn = UserRoles.roleId,
            targetForeignKeyColumn = UserRoles.userId,
            sourcePrimaryKeyColumn = this.id,
            targetPrimaryKeyColumn = Users.id
        )
    }
}

/**
 * UserRoles junction table - links Users and Roles for many-to-many relationships.
 */
object UserRoles : EntityTable<UserRole>("user_roles") {
    val userId = integer("user_id")  // FK to Users.id
    val roleId = integer("role_id")  // FK to Roles.id
}