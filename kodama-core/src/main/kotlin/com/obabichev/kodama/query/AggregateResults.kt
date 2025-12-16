package com.obabichev.kodama.query

import com.obabichev.kodama.components.Column
import java.sql.ResultSet

/**
 * Base interface for all aggregate result classes.
 * Provides common functionality for accessing aggregates by name.
 */
interface AggregateResultBase {
    val resultSet: ResultSet
    val relations: RelationsContainer
    val selectedColumns: List<Column<*>>
    val aggregates: List<AggregateFunction<*>>

    /**
     * Get an aggregate value by its alias name.
     * Performs runtime lookup and type casting.
     *
     * @param alias The alias name of the aggregate (e.g., "totalRevenue")
     * @return The aggregate value cast to the specified type
     * @throws IllegalArgumentException if aggregate with given alias is not found
     */
    fun <T> get(alias: String): T {
        val agg = aggregates.find { it.accessorName == alias }
        requireNotNull(agg) { "Aggregate '$alias' not found in query. Available: ${aggregates.map { it.accessorName }}" }
        val index = selectedColumns.size + aggregates.indexOf(agg) + 1
        @Suppress("UNCHECKED_CAST")
        return resultSet.getObject(index) as T
    }

    /**
     * Helper to find the result set index for an aggregate.
     */
    fun findAggregateIndex(alias: String): Int {
        val agg = aggregates.find { it.accessorName == alias }
        requireNotNull(agg) { "Aggregate '$alias' not found" }
        return selectedColumns.size + aggregates.indexOf(agg) + 1
    }
}

/**
 * Result class for queries with exactly 1 aggregate function.
 *
 * Provides:
 * - Positional access: row.agg1
 * - Named access: row.get<BigDecimal>("totalRevenue")
 * - Extension properties for named access (generated per query)
 */
class AggregateResult1(
    override val resultSet: ResultSet,
    override val relations: RelationsContainer,
    override val selectedColumns: List<Column<*>>,
    override val aggregates: List<AggregateFunction<*>>
) : QueryResult, AggregateResultBase {

    init {
        require(aggregates.size == 1) { "AggregateResult1 requires exactly 1 aggregate, got ${aggregates.size}" }
    }

    /**
     * Positional accessor for the first aggregate.
     */
    val agg1: Any?
        get() {
            val index = selectedColumns.size + 1
            return resultSet.getObject(index)
        }
}

/**
 * Result class for queries with exactly 2 aggregate functions.
 */
class AggregateResult2(
    override val resultSet: ResultSet,
    override val relations: RelationsContainer,
    override val selectedColumns: List<Column<*>>,
    override val aggregates: List<AggregateFunction<*>>
) : QueryResult, AggregateResultBase {

    init {
        require(aggregates.size == 2) { "AggregateResult2 requires exactly 2 aggregates, got ${aggregates.size}" }
    }

    val agg1: Any?
        get() {
            val index = selectedColumns.size + 1
            return resultSet.getObject(index)
        }

    val agg2: Any?
        get() {
            val index = selectedColumns.size + 2
            return resultSet.getObject(index)
        }
}

/**
 * Result class for queries with exactly 3 aggregate functions.
 */
class AggregateResult3(
    override val resultSet: ResultSet,
    override val relations: RelationsContainer,
    override val selectedColumns: List<Column<*>>,
    override val aggregates: List<AggregateFunction<*>>
) : QueryResult, AggregateResultBase {

    init {
        require(aggregates.size == 3) { "AggregateResult3 requires exactly 3 aggregates, got ${aggregates.size}" }
    }

    val agg1: Any? get() = resultSet.getObject(selectedColumns.size + 1)
    val agg2: Any? get() = resultSet.getObject(selectedColumns.size + 2)
    val agg3: Any? get() = resultSet.getObject(selectedColumns.size + 3)
}

/**
 * Result class for queries with exactly 4 aggregate functions.
 */
class AggregateResult4(
    override val resultSet: ResultSet,
    override val relations: RelationsContainer,
    override val selectedColumns: List<Column<*>>,
    override val aggregates: List<AggregateFunction<*>>
) : QueryResult, AggregateResultBase {

    init {
        require(aggregates.size == 4) { "AggregateResult4 requires exactly 4 aggregates, got ${aggregates.size}" }
    }

    val agg1: Any? get() = resultSet.getObject(selectedColumns.size + 1)
    val agg2: Any? get() = resultSet.getObject(selectedColumns.size + 2)
    val agg3: Any? get() = resultSet.getObject(selectedColumns.size + 3)
    val agg4: Any? get() = resultSet.getObject(selectedColumns.size + 4)
}

/**
 * Result class for queries with exactly 5 aggregate functions.
 */
class AggregateResult5(
    override val resultSet: ResultSet,
    override val relations: RelationsContainer,
    override val selectedColumns: List<Column<*>>,
    override val aggregates: List<AggregateFunction<*>>
) : QueryResult, AggregateResultBase {

    init {
        require(aggregates.size == 5) { "AggregateResult5 requires exactly 5 aggregates, got ${aggregates.size}" }
    }

    val agg1: Any? get() = resultSet.getObject(selectedColumns.size + 1)
    val agg2: Any? get() = resultSet.getObject(selectedColumns.size + 2)
    val agg3: Any? get() = resultSet.getObject(selectedColumns.size + 3)
    val agg4: Any? get() = resultSet.getObject(selectedColumns.size + 4)
    val agg5: Any? get() = resultSet.getObject(selectedColumns.size + 5)
}

/**
 * Result class for queries with exactly 6 aggregate functions.
 */
class AggregateResult6(
    override val resultSet: ResultSet,
    override val relations: RelationsContainer,
    override val selectedColumns: List<Column<*>>,
    override val aggregates: List<AggregateFunction<*>>
) : QueryResult, AggregateResultBase {

    init {
        require(aggregates.size == 6) { "AggregateResult6 requires exactly 6 aggregates, got ${aggregates.size}" }
    }

    val agg1: Any? get() = resultSet.getObject(selectedColumns.size + 1)
    val agg2: Any? get() = resultSet.getObject(selectedColumns.size + 2)
    val agg3: Any? get() = resultSet.getObject(selectedColumns.size + 3)
    val agg4: Any? get() = resultSet.getObject(selectedColumns.size + 4)
    val agg5: Any? get() = resultSet.getObject(selectedColumns.size + 5)
    val agg6: Any? get() = resultSet.getObject(selectedColumns.size + 6)
}

/**
 * Result class for queries with exactly 7 aggregate functions.
 */
class AggregateResult7(
    override val resultSet: ResultSet,
    override val relations: RelationsContainer,
    override val selectedColumns: List<Column<*>>,
    override val aggregates: List<AggregateFunction<*>>
) : QueryResult, AggregateResultBase {

    init {
        require(aggregates.size == 7) { "AggregateResult7 requires exactly 7 aggregates, got ${aggregates.size}" }
    }

    val agg1: Any? get() = resultSet.getObject(selectedColumns.size + 1)
    val agg2: Any? get() = resultSet.getObject(selectedColumns.size + 2)
    val agg3: Any? get() = resultSet.getObject(selectedColumns.size + 3)
    val agg4: Any? get() = resultSet.getObject(selectedColumns.size + 4)
    val agg5: Any? get() = resultSet.getObject(selectedColumns.size + 5)
    val agg6: Any? get() = resultSet.getObject(selectedColumns.size + 6)
    val agg7: Any? get() = resultSet.getObject(selectedColumns.size + 7)
}

/**
 * Result class for queries with exactly 8 aggregate functions.
 */
class AggregateResult8(
    override val resultSet: ResultSet,
    override val relations: RelationsContainer,
    override val selectedColumns: List<Column<*>>,
    override val aggregates: List<AggregateFunction<*>>
) : QueryResult, AggregateResultBase {

    init {
        require(aggregates.size == 8) { "AggregateResult8 requires exactly 8 aggregates, got ${aggregates.size}" }
    }

    val agg1: Any? get() = resultSet.getObject(selectedColumns.size + 1)
    val agg2: Any? get() = resultSet.getObject(selectedColumns.size + 2)
    val agg3: Any? get() = resultSet.getObject(selectedColumns.size + 3)
    val agg4: Any? get() = resultSet.getObject(selectedColumns.size + 4)
    val agg5: Any? get() = resultSet.getObject(selectedColumns.size + 5)
    val agg6: Any? get() = resultSet.getObject(selectedColumns.size + 6)
    val agg7: Any? get() = resultSet.getObject(selectedColumns.size + 7)
    val agg8: Any? get() = resultSet.getObject(selectedColumns.size + 8)
}

/**
 * Result class for queries with exactly 9 aggregate functions.
 */
class AggregateResult9(
    override val resultSet: ResultSet,
    override val relations: RelationsContainer,
    override val selectedColumns: List<Column<*>>,
    override val aggregates: List<AggregateFunction<*>>
) : QueryResult, AggregateResultBase {

    init {
        require(aggregates.size == 9) { "AggregateResult9 requires exactly 9 aggregates, got ${aggregates.size}" }
    }

    val agg1: Any? get() = resultSet.getObject(selectedColumns.size + 1)
    val agg2: Any? get() = resultSet.getObject(selectedColumns.size + 2)
    val agg3: Any? get() = resultSet.getObject(selectedColumns.size + 3)
    val agg4: Any? get() = resultSet.getObject(selectedColumns.size + 4)
    val agg5: Any? get() = resultSet.getObject(selectedColumns.size + 5)
    val agg6: Any? get() = resultSet.getObject(selectedColumns.size + 6)
    val agg7: Any? get() = resultSet.getObject(selectedColumns.size + 7)
    val agg8: Any? get() = resultSet.getObject(selectedColumns.size + 8)
    val agg9: Any? get() = resultSet.getObject(selectedColumns.size + 9)
}

/**
 * Result class for queries with exactly 10 aggregate functions.
 */
class AggregateResult10(
    override val resultSet: ResultSet,
    override val relations: RelationsContainer,
    override val selectedColumns: List<Column<*>>,
    override val aggregates: List<AggregateFunction<*>>
) : QueryResult, AggregateResultBase {

    init {
        require(aggregates.size == 10) { "AggregateResult10 requires exactly 10 aggregates, got ${aggregates.size}" }
    }

    val agg1: Any? get() = resultSet.getObject(selectedColumns.size + 1)
    val agg2: Any? get() = resultSet.getObject(selectedColumns.size + 2)
    val agg3: Any? get() = resultSet.getObject(selectedColumns.size + 3)
    val agg4: Any? get() = resultSet.getObject(selectedColumns.size + 4)
    val agg5: Any? get() = resultSet.getObject(selectedColumns.size + 5)
    val agg6: Any? get() = resultSet.getObject(selectedColumns.size + 6)
    val agg7: Any? get() = resultSet.getObject(selectedColumns.size + 7)
    val agg8: Any? get() = resultSet.getObject(selectedColumns.size + 8)
    val agg9: Any? get() = resultSet.getObject(selectedColumns.size + 9)
    val agg10: Any? get() = resultSet.getObject(selectedColumns.size + 10)
}
