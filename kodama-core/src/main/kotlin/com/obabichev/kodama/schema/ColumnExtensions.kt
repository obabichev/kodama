package com.obabichev.kodama.schema

import com.obabichev.kodama.components.Column

/**
 * Marks a numeric column as GENERATED ALWAYS AS IDENTITY (SQL standard auto-increment).
 *
 * IDENTITY is the SQL standard (SQL:2003) way to create auto-incrementing columns.
 * It is more portable than PostgreSQL's SERIAL and provides more control over the sequence.
 *
 * Values are generated automatically by the database and should NOT be provided during INSERT.
 * Columns marked with .identity() are excluded from generated insert() method parameters.
 *
 * SQL: GENERATED ALWAYS AS IDENTITY
 *
 * Supported column types:
 * - integer() → INTEGER GENERATED ALWAYS AS IDENTITY
 * - bigint() → BIGINT GENERATED ALWAYS AS IDENTITY
 * - smallint() → SMALLINT GENERATED ALWAYS AS IDENTITY
 *
 * Example:
 * ```kotlin
 * object Products : Table("products") {
 *     val id = integer("id").identity().primaryKey()  // SQL standard
 *     val name = varchar("name", 255)
 * }
 *
 * object Orders : Table("orders") {
 *     val id = bigint("id").identity().primaryKey()  // For larger IDs
 *     val total = decimal("total", 10, 2)
 * }
 * ```
 *
 * **SERIAL vs IDENTITY:**
 * - SERIAL: PostgreSQL-specific, simpler syntax
 * - IDENTITY: SQL standard, more portable to other databases
 *
 * @return A new Column with GenerationStrategy.AlwaysGenerated
 */
fun <T : Number> Column<T>.identity(): Column<T> {
    val newColumn = Column(
        name = this.name,
        relation = this.relation,
        type = this.type,
        nullable = this.nullable,
        generationStrategy = GenerationStrategy.AlwaysGenerated
    )
    // Update the registered column in the relation
    relation.replaceColumn(this, newColumn)
    return newColumn
}
