package com.obabichev.kodama.tests.schema

import com.obabichev.kodama.schema.Table
import com.obabichev.kodama.schema.primaryKey
import com.obabichev.kodama.schema.nullable

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
