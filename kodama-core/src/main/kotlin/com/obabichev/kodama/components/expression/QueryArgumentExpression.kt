package com.obabichev.kodama.components.expression

import com.obabichev.kodama.components.ColumnType
import com.obabichev.kodama.query.QueryArgument

class QueryArgumentExpression<T>(val value: T, val columnType: ColumnType<T>) : Expression {
    override fun toSql(): String {
        return "?"
    }

    override fun arguments(): List<QueryArgument<*>> {
        return listOf(QueryArgument(value, columnType))
    }
}