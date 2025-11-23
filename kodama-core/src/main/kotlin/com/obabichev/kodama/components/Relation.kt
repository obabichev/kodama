package com.obabichev.kodama.components


class Relation(val name: String) {
    val _columns = mutableListOf<Column<*>>()

    val columns: List<Column<*>>
        get() = _columns

    fun registerColumn(column: Column<*>) {
        _columns.add(column)
    }
}

