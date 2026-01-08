package com.obabichev.kodama.tests.debug

import com.obabichev.kodama.tests.schema.Order
import com.obabichev.kodama.tests.schema.generated.*
import com.obabichev.kodama.query.NoColumnsSelected
import com.obabichev.kodama.query.NoSelections

// Debug file to check types
fun debugTypes() {
    // Step 1: from(Order)
    val step1 = from(Order)
    val step1Type: AfterFromQueryBuilder_Order<NoColumnsSelected, NoSelections, JoinPattern_NONE> = step1

    // Step 2: selectAs with TotalRevenue marker
    val step2 = step1.selectAs(TotalRevenue) { sum(order.cost) }
    val step2Type: AfterFromQueryBuilder_Order<NoColumnsSelected, SelectionSet_TotalRevenue, JoinPattern_NONE> = step2

    // Step 3: execute - what do we get?
    val step3 = step2.execute(null!!)
    val step3Type: SelectionResult_TotalRevenue = step3.first()
}
