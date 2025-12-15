package com.obabichev.kodama.components


class Relation(val name: String) {
    val _columns = mutableListOf<Column<*>>()

    val columns: List<Column<*>>
        get() = _columns

    fun registerColumn(column: Column<*>) {
        _columns.add(column)
    }

    fun replaceColumn(oldColumn: Column<*>, newColumn: Column<*>) {
        val index = _columns.indexOf(oldColumn)
        if (index != -1) {
            _columns[index] = newColumn
        } else {
            _columns.add(newColumn)
        }
    }
}

