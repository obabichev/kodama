package com.obabichev.kodama.tests

import com.obabichev.kodama.execute.JdbcTransaction
import kotlin.test.Test

//class TestJdbcTransaction {
//    @Test
//    fun testJdbcTransaction() {
//        val query = "SELECT age, name FROM person"
//
//        val transaction = JdbcTransaction(
//            url = "jdbc:postgresql://localhost:5454/kodama",
//            user = "kodama",
//            password = "kodama"
//        )
//
//        try {
//            val resultSet = transaction.execute(query)
//
//            while (resultSet.next()) {
//                val age = resultSet.getInt("age")
//                val name = resultSet.getString("name")
//                println("Name: $name, Age: $age")
//            }
//
//            transaction.commit()
//        } catch (e: Exception) {
//            transaction.rollback()
//            throw e
//        } finally {
//            transaction.close()
//        }
//    }
//}