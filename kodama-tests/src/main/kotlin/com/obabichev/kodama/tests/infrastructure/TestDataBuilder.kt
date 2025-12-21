package com.obabichev.kodama.tests.infrastructure

import com.obabichev.kodama.execute.JdbcTransaction
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
     * Insert a person record.
     *
     * @param name Primary key, must be unique
     * @param age Must be a valid integer
     * @return The inserted person data for reference
     */
    fun person(
        name: String,
        age: Int
    ): InsertedPerson {
        transaction.executeUpdate(
            "INSERT INTO person (name, age) VALUES (?, ?)",
            name, age
        )
        return InsertedPerson(name, age)
    }

    /**
     * Insert an order record.
     * Note: "order" is a SQL reserved keyword, table name is quoted.
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
        cost: Int
    ): InsertedOrder {
        transaction.executeUpdate(
            """INSERT INTO "order" (id, user_name, product, cost) VALUES (?, ?, ?, ?)""",
            id, userName, product, cost
        )
        return InsertedOrder(id, userName, product, cost)
    }

    /**
     * Insert a profile record.
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
        transaction.executeUpdate(
            "INSERT INTO profile (user_name, contact, photo) VALUES (?, ?, ?)",
            userName, contact, photo
        )
        return InsertedProfile(userName, contact, photo)
    }

    /**
     * Insert a company record.
     *
     * @param id Primary key, must be unique
     * @param companyName Company name
     * @return The inserted company data for reference
     */
    fun company(
        id: Int,
        companyName: String
    ): InsertedCompany {
        transaction.executeUpdate(
            "INSERT INTO company (id, company_name) VALUES (?, ?)",
            id, companyName
        )
        return InsertedCompany(id, companyName)
    }

    /**
     * Insert a product record.
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
        transaction.executeUpdate(
            "INSERT INTO product (id, name, description, price, discount) VALUES (?, ?, ?, ?, ?)",
            id, name, description, price, discount
        )
        return InsertedProduct(id, name, description, price, discount)
    }

    /**
     * Insert a settings record.
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
        transaction.executeUpdate(
            "INSERT INTO settings (id, key, enabled, verified) VALUES (?, ?, ?, ?)",
            id, key, enabled, verified
        )
        return InsertedSettings(id, key, enabled, verified)
    }

    /**
     * Insert a numerics record for testing all numeric column types.
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
        transaction.executeUpdate(
            """
            INSERT INTO numerics (
                id, small_int_value, int_value, big_int_value, decimal_value, real_value, double_value,
                nullable_small_int, nullable_big_int, nullable_decimal, nullable_real, nullable_double
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id, smallIntValue, intValue, bigIntValue, decimalValue, realValue, doubleValue,
            nullableSmallInt, nullableBigInt, nullableDecimal, nullableReal, nullableDouble
        )
        return InsertedNumerics(
            id, smallIntValue, intValue, bigIntValue, decimalValue, realValue, doubleValue,
            nullableSmallInt, nullableBigInt, nullableDecimal, nullableReal, nullableDouble
        )
    }

    /**
     * Insert a trading strategy record.
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
        transaction.executeUpdate(
            "INSERT INTO trading_strategy (id, strategy_name, description) VALUES (?, ?, ?)",
            id, strategyName, description
        )
        return InsertedTradingStrategy(id, strategyName, description)
    }

    /**
     * Insert a market data record.
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
        transaction.executeUpdate(
            "INSERT INTO market_data (id, strategy_id, timestamp, price) VALUES (?, ?, ?, ?)",
            id, strategyId, timestamp, price
        )
        return InsertedMarketData(id, strategyId, timestamp, price)
    }
}

/**
 * Data classes representing inserted records.
 * These can be used in tests for assertions or building relationships.
 */
data class InsertedPerson(val name: String, val age: Int)
data class InsertedOrder(val id: Int, val userName: String, val product: String, val cost: Int)
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
