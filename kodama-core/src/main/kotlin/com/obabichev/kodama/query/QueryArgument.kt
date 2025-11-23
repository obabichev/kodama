package com.obabichev.kodama.query

import com.obabichev.kodama.components.ColumnType

class QueryArgument<T>(val value: T, val columnType: ColumnType<T>)