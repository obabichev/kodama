package com.obabichev.kodama.query

/**
 * Type-level marker interfaces for compile-time type safety with type accumulation.
 *
 * This system tracks ALL selections in the type system, allowing:
 * - Chainable selectors: .selectPersonName().selectPersonAge()
 * - Complete type information: SelectedColumns<Person, Name_Age, ...>
 * - True compile-time safety: only selected columns accessible
 *
 * Design: Uses HList-style type accumulation where each selection adds to the type.
 */

// ============================================================================
// Base Selection State
// ============================================================================

/**
 * Marker: No selection made yet (initial state after from())
 */
interface NoSelection

/**
 * Base interface for column selection states.
 * Tracks which columns have been selected from a table.
 */
interface ColumnSelectionState

/**
 * All columns selected via .selectTableAll()
 */
interface AllColumnsSelected : ColumnSelectionState

/**
 * No columns selected from this table yet
 */
interface NoColumnsSelected : ColumnSelectionState

/**
 * Specific columns selected - accumulates column names
 *
 * Example:
 * - After .selectPersonName(): SelectedColumns<Name, NoColumnsSelected>
 * - After .selectPersonName().selectPersonAge(): SelectedColumns<Age, SelectedColumns<Name, NoColumnsSelected>>
 *
 * This creates a type-level linked list of all selected columns!
 *
 * @param Column The newly selected column
 * @param Prev The previous selection state (nested accumulation)
 */
interface SelectedColumns<Column, Prev : ColumnSelectionState> : ColumnSelectionState

// ============================================================================
// Column Name Markers
// ============================================================================

/**
 * Column name markers (e.g., Name, Age, Product, Cost)
 * These are generated per table/column in QueryExtensions.kt
 *
 * Example generated markers:
 * - interface Name  (for person.name)
 * - interface Age   (for person.age)
 * - interface Product (for order.product)
 */
