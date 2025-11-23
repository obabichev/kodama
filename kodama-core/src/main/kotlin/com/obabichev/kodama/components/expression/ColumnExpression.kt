package com.obabichev.kodama.components.expression

import com.obabichev.kodama.components.Column

class ColumnExpression<T>(val column: Column<T>) : Expression {
    override fun toSql(): String {
        return column.name
    }
}