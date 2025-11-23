package com.obabichev.kodama.annotations

import com.obabichev.kodama.components.ColumnType
import com.obabichev.kodama.components.types.StringColumnType
import kotlin.reflect.KClass

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Column(val name: String, val klass: KClass<out ColumnType<*>>)
