package com.obabichev.kodama.components


/**
 * Represents a database column.
 *
 * @param T The Kotlin type of the column value (String, Int, etc.)
 */
class Column<T>(
    val name: String,
    val relation: Relation,
    val type: ColumnType<T>,
    val nullable: Boolean = true  // Default to nullable for safety
) {
}

/**
 * Typed wrapper for Column that adds compile-time type markers.
 * Used for type-safe column selection in queries.
 *
 * @param T The Kotlin type of the column value (String, Int, etc.)
 * @param TableMarker Phantom type for tracking which table this column belongs to (PersonTable, OrderTable, etc.)
 * @param ColumnMarker Phantom type for tracking which column this is at compile time (Name, Age, etc.)
 */
/**
 * Marker interface for all selection types (columns and all-markers)
 */
interface SelectionMarker

class TypedColumn<T, TableMarker, ColumnMarker>(val column: Column<T>) : SelectionMarker {
    val name: String get() = column.name
    val relation: Relation get() = column.relation
    val type: ColumnType<T> get() = column.type
}