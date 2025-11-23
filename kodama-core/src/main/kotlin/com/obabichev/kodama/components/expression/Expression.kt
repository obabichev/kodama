package com.obabichev.kodama.components.expression

import com.obabichev.kodama.query.QueryArgument

interface Expression {
    fun toSql(): String

    fun arguments(): List<QueryArgument<*>> = emptyList()
}