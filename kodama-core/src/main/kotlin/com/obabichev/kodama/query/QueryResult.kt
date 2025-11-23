package com.obabichev.kodama.query

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.components.ColumnType
import java.sql.ResultSet

/**
 * Base interface for query results.
 * Query results provide type-safe access to selected columns only.
 */
interface QueryResult {
    val resultSet: ResultSet
    val relations: RelationsContainer
}

/**
 * Iterator wrapper for query results that ensures ResultSet is properly positioned.
 */
class QueryResultIterator<T : QueryResult>(
    private val resultSet: ResultSet,
    private val relations: RelationsContainer,
    private val resultFactory: (ResultSet, RelationsContainer) -> T
) : Iterator<T> {
    private var hasNextCached: Boolean? = null

    override fun hasNext(): Boolean {
        if (hasNextCached == null) {
            hasNextCached = resultSet.next()
        }
        return hasNextCached!!
    }

    override fun next(): T {
        if (hasNextCached == null) {
            if (!resultSet.next()) {
                throw NoSuchElementException()
            }
        }
        hasNextCached = null
        return resultFactory(resultSet, relations)
    }
}

/**
 * Iterable wrapper for query results.
 */
class QueryResultIterable<T : QueryResult>(
    private val resultSet: ResultSet,
    private val relations: RelationsContainer,
    private val resultFactory: (ResultSet, RelationsContainer) -> T
) : Iterable<T> {
    override fun iterator(): Iterator<T> {
        return QueryResultIterator(resultSet, relations, resultFactory)
    }
}

/**
 * Base class for table accessors in query results.
 * Provides access to selected columns from a specific table.
 */
abstract class TableResultAccessor(
    protected val resultSet: ResultSet,
    protected val relations: RelationsContainer,
    protected val selectedColumns: List<Column<*>>
) {
    protected fun <T> readColumn(column: Column<T>): T {
        val columnIndex = selectedColumns.indexOfFirst { it.name == column.name && it.relation == column.relation }
        if (columnIndex == -1) {
            throw IllegalStateException("Column ${column.name} from table ${column.relation.name} was not selected in the query")
        }
        @Suppress("UNCHECKED_CAST")
        val columnType = column.type as ColumnType<T>
        return columnType.readValue(resultSet, columnIndex + 1)
    }
}
