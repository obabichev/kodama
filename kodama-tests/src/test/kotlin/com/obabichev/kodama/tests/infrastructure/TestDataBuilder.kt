package com.obabichev.kodama.tests.infrastructure

import com.obabichev.kodama.execute.JdbcTransaction
import com.obabichev.kodama.tests.schema.*
import com.obabichev.kodama.tests.schema.generated.*
import java.math.BigDecimal

/**
 * Type-safe DSL for inserting test data.
 *
 * Usage:
 * ```kotlin
 * testData {
 *     val user = person("kodama", age = 1)
 *     order(1, user.name, "Laptop", 1000)
 *     profile(user.name, "kodama@example.com")
 * }
 * ```
 *
 * Benefits:
 * - Type-safe: Compiler prevents typos and wrong types
 * - No SQL injection: Uses prepared statements
 * - Returns inserted data: Can use in subsequent inserts
 * - Named parameters: Clear intent
 */
class TestDataBuilder(private val transaction: JdbcTransaction) {

    /**
     * Insert a person record using generated insert method.
     *
     * @param name Primary key, must be unique
     * @param age Must be a valid integer
     * @return The inserted person data for reference
     */
    fun person(
        name: String,
        age: Int
    ): InsertedPerson {
        Person.insert(transaction, name, age)
        return InsertedPerson(name, age)
    }

    /**
     * Insert an order record using generated insert method.
     *
     * @param id Primary key, must be unique
     * @param userName Foreign key reference to person.name
     * @param product Product description
     * @param cost Cost in cents/minor currency units
     * @return The inserted order data for reference
     */
    fun order(
        id: Int,
        userName: String,
        product: String,
        cost: Int,
        companyId: Int? = null
    ): InsertedOrder {
        Order.insert(transaction, id, userName, product, cost, companyId)
        return InsertedOrder(id, userName, product, cost)
    }

    /**
     * Insert a profile record using generated insert method.
     *
     * @param userName Reference to person.name
     * @param contact Contact information (email, phone, etc.)
     * @param photo Optional photo URL/path
     * @return The inserted profile data for reference
     */
    fun profile(
        userName: String,
        contact: String,
        photo: String? = null
    ): InsertedProfile {
        Profile.insert(transaction, userName, contact, photo)
        return InsertedProfile(userName, contact, photo)
    }

    /**
     * Insert a company record using generated insert method.
     *
     * @param id Primary key, must be unique
     * @param companyName Company name
     * @return The inserted company data for reference
     */
    fun company(
        id: Int,
        companyName: String
    ): InsertedCompany {
        Company.insert(transaction, id, companyName)
        return InsertedCompany(id, companyName)
    }

    /**
     * Insert a product record using generated insert method.
     *
     * @param id Primary key, must be unique
     * @param name Product name
     * @param description Optional product description
     * @param price Price in cents/minor currency units
     * @param discount Optional discount percentage
     * @return The inserted product data for reference
     */
    fun product(
        id: Int,
        name: String,
        description: String? = null,
        price: Int,
        discount: Int? = null
    ): InsertedProduct {
        Product.insert(transaction, id, name, description, price, discount)
        return InsertedProduct(id, name, description, price, discount)
    }

    /**
     * Insert a settings record using generated insert method.
     *
     * @param id Primary key, must be unique
     * @param key Settings key/name
     * @param enabled Boolean flag (non-nullable)
     * @param verified Optional boolean verification status
     * @return The inserted settings data for reference
     */
    fun settings(
        id: Int,
        key: String,
        enabled: Boolean,
        verified: Boolean? = null
    ): InsertedSettings {
        Settings.insert(transaction, id, key, enabled, verified)
        return InsertedSettings(id, key, enabled, verified)
    }

    /**
     * Insert a numerics record using generated insert method.
     *
     * @param id Primary key, must be unique
     * @param smallIntValue SMALLINT value
     * @param intValue INTEGER value
     * @param bigIntValue BIGINT value
     * @param decimalValue DECIMAL/NUMERIC value
     * @param realValue REAL (float) value
     * @param doubleValue DOUBLE PRECISION value
     * @param nullableSmallInt Optional SMALLINT
     * @param nullableBigInt Optional BIGINT
     * @param nullableDecimal Optional DECIMAL
     * @param nullableReal Optional REAL
     * @param nullableDouble Optional DOUBLE PRECISION
     * @return The inserted numeric types data for reference
     */
    fun numerics(
        id: Int,
        smallIntValue: Short,
        intValue: Int,
        bigIntValue: Long,
        decimalValue: BigDecimal,
        realValue: Float,
        doubleValue: Double,
        nullableSmallInt: Short? = null,
        nullableBigInt: Long? = null,
        nullableDecimal: BigDecimal? = null,
        nullableReal: Float? = null,
        nullableDouble: Double? = null
    ): InsertedNumerics {
        Numerics.insert(
            transaction, id, smallIntValue, intValue, bigIntValue, decimalValue, realValue, doubleValue,
            nullableSmallInt, nullableBigInt, nullableDecimal, nullableReal, nullableDouble
        )
        return InsertedNumerics(
            id, smallIntValue, intValue, bigIntValue, decimalValue, realValue, doubleValue,
            nullableSmallInt, nullableBigInt, nullableDecimal, nullableReal, nullableDouble
        )
    }

    /**
     * Insert a trading strategy record using generated insert method.
     *
     * @param id Primary key, must be unique
     * @param strategyName Strategy name
     * @param description Optional strategy description
     * @return The inserted trading strategy data for reference
     */
    fun tradingStrategy(
        id: Int,
        strategyName: String,
        description: String? = null
    ): InsertedTradingStrategy {
        TradingStrategy.insert(transaction, id, strategyName, description)
        return InsertedTradingStrategy(id, strategyName, description)
    }

    /**
     * Insert a market data record using generated insert method.
     *
     * @param id Primary key, must be unique
     * @param strategyId Foreign key reference to trading_strategy.id
     * @param timestamp Timestamp of the market data
     * @param price Price value
     * @return The inserted market data for reference
     */
    fun marketData(
        id: Int,
        strategyId: Int,
        timestamp: String,
        price: Int
    ): InsertedMarketData {
        MarketData.insert(transaction, id, strategyId, timestamp, price)
        return InsertedMarketData(id, strategyId, timestamp, price)
    }

    /**
     * Insert an event record using generated insert method.
     * Tests all datetime column types.
     *
     * @param id Primary key, must be unique
     * @param eventDate DATE column (LocalDate)
     * @param eventTime TIME column (LocalTime)
     * @param createdAt TIMESTAMP column (LocalDateTime)
     * @param scheduledFor Optional TIMESTAMP column (LocalDateTime?)
     * @param eventTimestamp TIMESTAMP WITH TIME ZONE column (OffsetDateTime)
     * @param reminderTime TIME WITH TIME ZONE column (OffsetTime)
     * @param duration INTERVAL column (Duration)
     * @param optionalDuration Optional INTERVAL column (Duration?)
     * @return The inserted event data for reference
     */
    fun events(
        id: Int,
        eventDate: java.time.LocalDate,
        eventTime: java.time.LocalTime,
        createdAt: java.time.LocalDateTime,
        scheduledFor: java.time.LocalDateTime? = null,
        eventTimestamp: java.time.OffsetDateTime,
        reminderTime: java.time.OffsetTime,
        duration: java.time.Duration,
        optionalDuration: java.time.Duration? = null
    ): InsertedEvents {
        Events.insert(
            transaction, id, eventDate, eventTime, createdAt, scheduledFor,
            eventTimestamp, reminderTime, duration, optionalDuration
        )
        return InsertedEvents(
            id, eventDate, eventTime, createdAt, scheduledFor,
            eventTimestamp, reminderTime, duration, optionalDuration
        )
    }
}

/**
 * Data classes representing inserted records.
 * These can be used in tests for assertions or building relationships.
 */
data class InsertedPerson(val name: String, val age: Int)
data class InsertedOrder(val id: Int, val userName: String, val product: String, val cost: Int)
data class InsertedBook(val id: Int, val title: String, val content: String, val pages: Int)
data class InsertedProfile(val userName: String, val contact: String, val photo: String?)
data class InsertedCompany(val id: Int, val companyName: String)
data class InsertedProduct(
    val id: Int,
    val name: String,
    val description: String?,
    val price: Int,
    val discount: Int?
)

data class InsertedSettings(
    val id: Int,
    val key: String,
    val enabled: Boolean,
    val verified: Boolean?
)

data class InsertedNumerics(
    val id: Int,
    val smallIntValue: Short,
    val intValue: Int,
    val bigIntValue: Long,
    val decimalValue: BigDecimal,
    val realValue: Float,
    val doubleValue: Double,
    val nullableSmallInt: Short?,
    val nullableBigInt: Long?,
    val nullableDecimal: BigDecimal?,
    val nullableReal: Float?,
    val nullableDouble: Double?
)

data class InsertedTradingStrategy(
    val id: Int,
    val strategyName: String,
    val description: String?
)

data class InsertedMarketData(
    val id: Int,
    val strategyId: Int,
    val timestamp: String,
    val price: Int
)

data class InsertedEvents(
    val id: Int,
    val eventDate: java.time.LocalDate,
    val eventTime: java.time.LocalTime,
    val createdAt: java.time.LocalDateTime,
    val scheduledFor: java.time.LocalDateTime?,
    val eventTimestamp: java.time.OffsetDateTime,
    val reminderTime: java.time.OffsetTime,
    val duration: java.time.Duration,
    val optionalDuration: java.time.Duration?
)
