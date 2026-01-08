package com.obabichev.kodama.tests.debug

import com.obabichev.kodama.query.*
import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.Person
import com.obabichev.kodama.tests.schema.generated.*

/**
 * Debug file to verify phantom types work correctly at compile time.
 *
 * ## Phantom Types Type System
 *
 * The type system uses phantom type parameters to track query state:
 * - QueryBuilder_1<T1: TableMarker, Sel: SelectionSet> - Single table query
 * - QueryBuilder_2<T1: TableMarker, T2: TableMarker, Sel: SelectionSet> - Two table query
 * - QueryBuilder_3<T1: TableMarker, T2: TableMarker, T3: TableMarker, Sel: SelectionSet> - Three table query
 *
 * Type parameters encode which tables are in the query and which markers are selected at compile time,
 * with zero runtime overhead (phantom types are erased by JVM).
 */
fun debugTypes() {
    // Step 1: from(Order)
    // Type: QueryBuilder_1<OrderMarker, NoSelections>
    val step1 = from(Order)
    val step1Type: QueryBuilder_1<OrderMarker, NoSelections> = step1

    // Step 2: selectAll(Order)
    // Type: QueryBuilder_1<OrderMarker, NoSelections> (unchanged - Sel preserved)
    val step2 = step1.selectAll(Order)
    val step2Type: QueryBuilder_1<OrderMarker, NoSelections> = step2

    // Step 3: join(Person)
    // Type: QueryBuilder_2<OrderMarker, PersonMarker, NoSelections> (Sel preserved through join)
    val step3 = step2.join(Person) { person.name eq order.userName }
    val step3Type: QueryBuilder_2<OrderMarker, PersonMarker, NoSelections> = step3

    // Step 4: execute - returns QueryResultIterable_2
    // Type: QueryResultIterable_2<OrderMarker, PersonMarker>
    // val step4 = step3.execute(transaction)
    // val step4Type: QueryResultIterable_2<OrderMarker, PersonMarker> = step4

    println("Type checking complete - all types match!")
}
