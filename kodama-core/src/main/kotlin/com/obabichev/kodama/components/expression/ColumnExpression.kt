package com.obabichev.kodama.components.expression

import com.obabichev.kodama.components.Column

class ColumnExpression<T>(val column: Column<T>) : Expression {
    override fun toSql(): String {
        // Always qualify column names with table names to avoid ambiguity in JOINs
        // Quote both table and column names for case sensitivity
        return "\"${column.relation.name}\".\"${column.name}\""
    }
}