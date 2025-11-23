package com.obabichev.kodama.execute

import com.obabichev.kodama.query.Query
import java.sql.ResultSet

interface Transaction {
    fun execute(sql: Query): ResultSet
    fun rollback()
    fun commit()
}