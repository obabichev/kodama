package com.obabichev.kodama.components


enum class JoinType { INNER, LEFT, RIGHT }

class Join(
    val type: JoinType,
    val relation: Relation,
    val condition: Pair<Column<*>, Column<*>>
)
