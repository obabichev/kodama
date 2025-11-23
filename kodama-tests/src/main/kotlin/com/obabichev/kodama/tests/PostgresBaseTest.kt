package com.obabichev.kodama.tests

import com.obabichev.kodama.execute.JdbcTransaction
import kotlin.test.BeforeTest

open class PostgresBaseTest {
    private val url = "jdbc:postgresql://localhost:5454/kodama"
    private val user = "kodama"
    private val password = "kodama"

    private fun createTransaction(): JdbcTransaction {
        return JdbcTransaction(
            url = url,
            user = user,
            password = password
        )
    }

    fun <T> withConnection(block: JdbcTransaction.() -> T): T {
        val transaction = createTransaction()
        try {
            val result = block(transaction)
            transaction.commit()
            return result
        } catch (e: Exception) {
            transaction.rollback()
            throw e
        } finally {
            transaction.close()
        }
    }

    @BeforeTest
    fun before() {
        withConnection {
            // Create person table
            executeUpdate(
                """
                create table if not exists person
                (
                    name text primary key not null,
                    age  integer          not null
                )
                """.trimIndent()
            )

            // Create order table (note: "order" is a reserved keyword in SQL, so we use quotes)
            executeUpdate(
                """
                create table if not exists "order"
                (
                    id       integer primary key not null,
                    user_name  text             not null,
                    product  text                not null,
                    cost     integer             not null
                )
                """.trimIndent()
            )

            // Create profile table
            executeUpdate(
                """
                create table if not exists profile
                (
                    user_name text not null,
                    contact   text not null,
                    photo     text not null
                )
                """.trimIndent()
            )

            // Create company table
            executeUpdate(
                """
                create table if not exists company
                (
                    id           integer primary key not null,
                    company_name text                not null
                )
                """.trimIndent()
            )

            // Clear existing data
            executeUpdate("delete from person")
            executeUpdate("""delete from "order"""")
            executeUpdate("delete from profile")
            executeUpdate("delete from company")

            // Insert test data for person
            executeUpdate("insert into person values ('kodama', 1)")
            executeUpdate("insert into person values ('kokoro', 2)")
            executeUpdate("insert into person values ('pipiru', 2)")

            // Insert test data for order
            executeUpdate("""insert into "order" values (1, 'kodama', 'Laptop', 1000)""")
            executeUpdate("""insert into "order" values (2, 'kodama', 'Mouse', 50)""")
            executeUpdate("""insert into "order" values (3, 'kokoro', 'Keyboard', 100)""")

            // Insert test data for profile
            executeUpdate("insert into profile values ('kodama', 'kodama@example.com', 'photo1.jpg')")
            executeUpdate("insert into profile values ('kokoro', 'kokoro@example.com', 'photo2.jpg')")
            executeUpdate("insert into profile values ('pipiru', 'pipiru@example.com', 'photo3.jpg')")

            // Insert test data for company
            executeUpdate("insert into company values (1, 'Acme Corp')")
            executeUpdate("insert into company values (2, 'Tech Solutions')")
            executeUpdate("insert into company values (3, 'Global Industries')")
        }
    }
}
