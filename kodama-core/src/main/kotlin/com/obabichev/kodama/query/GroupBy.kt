package com.obabichev.kodama.query

/**
 * Context for building GROUP BY clause in a type-safe way
 *
 * Usage:
 * ```
 * .groupBy { person.name }
 * .groupBy { person.age }
 * ```
 *
 * Each groupBy call selects exactly one column and can be chained.
 */
abstract class GroupByContext
