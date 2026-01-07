package com.obabichev.kodama.components

import com.obabichev.kodama.components.expression.Expression

enum class JoinType { INNER, LEFT, RIGHT, FULL }

class Join(
    val type: JoinType,
    val relation: Relation,
    val condition: Expression
)
