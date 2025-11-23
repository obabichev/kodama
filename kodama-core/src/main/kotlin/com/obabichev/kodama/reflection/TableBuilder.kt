package com.obabichev.kodama.reflection

import com.obabichev.kodama.components.Column
import com.obabichev.kodama.components.ColumnType
import com.obabichev.kodama.components.Relation
import com.obabichev.kodama.annotations.Column as ColumnAnnotation
import com.obabichev.kodama.annotations.Table as TableAnnotation
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties


fun <T : Any> buildTable(klass: KClass<T>): Relation {
    val tableAnnotation = klass.findAnnotation<TableAnnotation>()
        ?: throw IllegalArgumentException("Class ${klass.simpleName} must be annotated with @Table")

    val tableName = tableAnnotation.name

    val relation = Relation(tableName)

    klass.memberProperties
        .mapNotNull { property ->
            property.findAnnotation<ColumnAnnotation>()?.let { annotation ->
                // Instantiate the ColumnType using reflection
                val columnTypeClass = annotation.klass
                val columnTypeInstance = columnTypeClass.createInstance()

                @Suppress("UNCHECKED_CAST")
                Column(
                    annotation.name,
                    relation,
                    columnTypeInstance as ColumnType<Any?>
                )
            }
        }
        .forEach { relation.registerColumn(it) }

    return relation
}