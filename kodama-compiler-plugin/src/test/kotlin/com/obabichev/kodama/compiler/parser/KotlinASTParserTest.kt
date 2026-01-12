package com.obabichev.kodama.compiler.parser

import kotlin.test.*

/**
 * Tests for KotlinASTParser and QueryDiscoveryVisitor.
 *
 * Validates that the AST parser correctly discovers query patterns from Kotlin code.
 */
class KotlinASTParserTest {

    private lateinit var parser: KotlinASTParser

    @BeforeTest
    fun setup() {
        parser = KotlinASTParser()
    }

    @AfterTest
    fun teardown() {
        parser.dispose()
    }

    @Test
    fun `test parse simple query - single table`() {
        val source = """
            package test

            fun testQuery() {
                from(Person)
                    .selectAll(Person)
            }
        """.trimIndent()

        val ktFile = parser.parseText(source)
        val visitor = QueryDiscoveryVisitor()

        ktFile.accept(visitor)

        assertEquals(1, visitor.discoveredQueries.size, "Should discover one query")

        val query = visitor.discoveredQueries.first()
        assertEquals("Person", query.baseTable)
        assertEquals(listOf("Person"), query.getTables())
        assertEquals("Person", query.buildCombinationKey())
    }

    @Test
    fun `test parse query with single join`() {
        val source = """
            from(Person)
                .join(Order) { order.userName eq person.name }
                .selectAll(Person)
                .selectAll(Order)
        """.trimIndent()

        val ktFile = parser.parseText(source)
        val visitor = QueryDiscoveryVisitor()

        ktFile.accept(visitor)

        val query = visitor.discoveredQueries.first()
        assertEquals("Person", query.baseTable)
        assertEquals(listOf("Person", "Order"), query.getTables())
        assertEquals("Person_Order", query.buildCombinationKey())

        val joins = query.getJoins()
        assertEquals(1, joins.size)
        assertEquals("Order", joins.first().table)
        assertEquals(JoinType.INNER, joins.first().joinType)
        assertNotNull(joins.first().condition)
    }

    @Test
    fun `test parse query with multiple joins`() {
        val source = """
            from(Person)
                .join(Order) { order.userName eq person.name }
                .join(Company) { company.id eq order.companyId }
                .selectAll(Person)
                .selectAll(Order)
                .selectAll(Company)
        """.trimIndent()

        val ktFile = parser.parseText(source)
        val visitor = QueryDiscoveryVisitor()

        ktFile.accept(visitor)

        val query = visitor.discoveredQueries.first()
        assertEquals(listOf("Person", "Order", "Company"), query.getTables())
        assertEquals("Person_Order_Company", query.buildCombinationKey())

        val joins = query.getJoins()
        assertEquals(2, joins.size)
    }

    @Test
    fun `test parse query with LEFT JOIN`() {
        val source = """
            from(Person)
                .leftJoin(Order) { order.userName eq person.name }
                .selectAll(Person)
                .selectAll(Order)
        """.trimIndent()

        val ktFile = parser.parseText(source)
        val visitor = QueryDiscoveryVisitor()

        ktFile.accept(visitor)

        val query = visitor.discoveredQueries.first()
        val joins = query.getJoins()

        assertEquals(1, joins.size)
        assertEquals(JoinType.LEFT, joins.first().joinType)
    }

    @Test
    fun `test parse query with WHERE clause`() {
        val source = """
            from(Person)
                .selectAll(Person)
                .where { person.age eq 25 }
        """.trimIndent()

        val ktFile = parser.parseText(source)
        val visitor = QueryDiscoveryVisitor()

        ktFile.accept(visitor)

        val query = visitor.discoveredQueries.first()
        val whereOp = query.operations.find { it.type == OperationType.WHERE }

        assertNotNull(whereOp)
        assertNotNull(whereOp!!.condition)
        assertEquals("person.age eq 25", whereOp.condition!!.body)
    }

    @Test
    fun `test parse query with GROUP BY`() {
        val source = """
            from(Order)
                .select { order.userName }
                .select { sum(order.cost) }
                .groupBy { order.userName }
        """.trimIndent()

        val ktFile = parser.parseText(source)
        val visitor = QueryDiscoveryVisitor()

        ktFile.accept(visitor)

        val query = visitor.discoveredQueries.first()
        val groupByOp = query.operations.find { it.type == OperationType.GROUP_BY }

        assertNotNull(groupByOp)
        assertNotNull(groupByOp!!.lambda)
    }

    @Test
    fun `test parse query with ORDER BY`() {
        val source = """
            from(Person)
                .selectAll(Person)
                .orderBy { person.age.desc() }
        """.trimIndent()

        val ktFile = parser.parseText(source)
        val visitor = QueryDiscoveryVisitor()

        ktFile.accept(visitor)

        val query = visitor.discoveredQueries.first()
        val orderByOp = query.operations.find { it.type == OperationType.ORDER_BY }

        assertNotNull(orderByOp)
        assertNotNull(orderByOp!!.lambda)
    }

    @Test
    fun `test parse query with LIMIT and OFFSET`() {
        val source = """
            from(Person)
                .selectAll(Person)
                .limit(10)
                .offset(5)
        """.trimIndent()

        val ktFile = parser.parseText(source)
        val visitor = QueryDiscoveryVisitor()

        ktFile.accept(visitor)

        val query = visitor.discoveredQueries.first()
        val limitOp = query.operations.find { it.type == OperationType.LIMIT }
        val offsetOp = query.operations.find { it.type == OperationType.OFFSET }

        assertNotNull(limitOp)
        assertEquals(10, limitOp!!.intValue)

        assertNotNull(offsetOp)
        assertEquals(5, offsetOp!!.intValue)
    }

    @Test
    fun `test parse query with selectAliased`() {
        val source = """
            from(Order)
                .selectAliased(TotalRevenue) { sum(order.cost) }
                .selectAliased(OrderCount) { count(order.id) }
                .groupBy { order.userName }
        """.trimIndent()

        val ktFile = parser.parseText(source)
        val visitor = QueryDiscoveryVisitor()

        ktFile.accept(visitor)

        val query = visitor.discoveredQueries.first()
        val aliasedSelections = query.operations.filter { it.type == OperationType.SELECT_ALIASED }

        assertEquals(2, aliasedSelections.size)
        assertEquals("TotalRevenue", aliasedSelections[0].marker)
        assertEquals("OrderCount", aliasedSelections[1].marker)
    }

    @Test
    fun `test parse query with inline subquery`() {
        val source = """
            from(Person)
                .joinAliased(
                    from(Order)
                        .selectAs(OrderUserName) { order.userName }
                        .selectAs(TotalCost) { sum(order.cost) }
                        .groupBy { order.userName }
                        .build()
                        .aliasAs<UserTotalSubquery>()
                ) { userTotalSubquery.orderUserName eq person.name }
                .selectAll(Person)
        """.trimIndent()

        val ktFile = parser.parseText(source)
        val visitor = QueryDiscoveryVisitor()

        ktFile.accept(visitor)

        val query = visitor.discoveredQueries.first()
        assertTrue(query.hasSubqueries(), "Query should contain subqueries")

        val subqueries = query.getSubqueries()
        assertEquals(1, subqueries.size)

        val subquery = subqueries.first()
        assertEquals("UserTotalSubquery", subquery.alias)
        assertEquals("Order", subquery.getBaseTable())

        val subquerySelections = subquery.getSelections()
        assertTrue(subquerySelections.size >= 2, "Subquery should have selections")
    }

    @Test
    fun `test prefix combinations generation`() {
        val source = """
            from(Person)
                .join(Order) { order.userName eq person.name }
                .join(Profile) { profile.userName eq person.name }
                .selectAll(Person)
                .selectAll(Order)
                .selectAll(Profile)
        """.trimIndent()

        val ktFile = parser.parseText(source)
        val visitor = QueryDiscoveryVisitor()

        ktFile.accept(visitor)

        val query = visitor.discoveredQueries.first()
        val prefixes = query.generatePrefixCombinations()

        assertEquals(3, prefixes.size)
        assertEquals("Person", prefixes[0])
        assertEquals("Person_Order", prefixes[1])
        assertEquals("Person_Order_Profile", prefixes[2])
    }

    @Test
    fun `test multiple queries in single file`() {
        val source = """
            package test

            fun query1() {
                from(Person).selectAll(Person)
            }

            fun query2() {
                from(Order).selectAll(Order)
            }

            fun query3() {
                from(Person)
                    .join(Order) { order.userName eq person.name }
                    .selectAll(Person)
                    .selectAll(Order)
            }
        """.trimIndent()

        val ktFile = parser.parseText(source)
        val visitor = QueryDiscoveryVisitor()

        ktFile.accept(visitor)

        assertEquals(3, visitor.discoveredQueries.size, "Should discover three queries")

        val combinations = visitor.discoveredQueries
            .flatMap { it.generatePrefixCombinations() }
            .toSet()

        assertTrue(combinations.contains("Person"))
        assertTrue(combinations.contains("Order"))
        assertTrue(combinations.contains("Person_Order"))
    }

    @Test
    fun `test discovery statistics`() {
        val source = """
            fun query1() {
                from(Person).selectAll(Person)
            }

            fun query2() {
                from(Person)
                    .join(Order) { order.userName eq person.name }
                    .selectAll(Person)
                    .selectAll(Order)
            }

            fun query3() {
                from(Person)
                    .joinAliased(
                        from(Order)
                            .selectAs(Total) { sum(order.cost) }
                            .groupBy { order.userName }
                            .build()
                            .aliasAs<UserTotals>()
                    ) { userTotals.userName eq person.name }
                    .selectAll(Person)
            }
        """.trimIndent()

        val ktFile = parser.parseText(source)
        val visitor = QueryDiscoveryVisitor()

        ktFile.accept(visitor)

        val stats = DiscoveryStatistics.from(visitor.discoveredQueries)

        assertEquals(3, stats.totalQueries)
        assertEquals(2, stats.queriesWithJoins)
        assertEquals(1, stats.queriesWithSubqueries)
        assertTrue(stats.uniqueTables.contains("Person"))
        assertTrue(stats.uniqueTables.contains("Order"))
        assertTrue(stats.subqueryAliases.contains("UserTotals"))
    }

    @Test
    fun `test complex nested query`() {
        val source = """
            from(Person)
                .join(Order) { order.userName eq person.name }
                .join(Company) { company.id eq order.companyId }
                .selectAll(Person)
                .selectAll(Order)
                .selectAll(Company)
                .where { person.age gt 18 }
                .orderBy {
                    person.name.asc()
                    order.cost.desc()
                }
                .limit(100)
                .offset(10)
        """.trimIndent()

        val ktFile = parser.parseText(source)
        val visitor = QueryDiscoveryVisitor()

        ktFile.accept(visitor)

        val query = visitor.discoveredQueries.first()

        // Verify structure
        assertEquals(3, query.getTables().size)
        assertEquals(2, query.getJoins().size)
        assertNotNull(query.operations.find { it.type == OperationType.WHERE })
        assertNotNull(query.operations.find { it.type == OperationType.ORDER_BY })
        assertNotNull(query.operations.find { it.type == OperationType.LIMIT })
        assertNotNull(query.operations.find { it.type == OperationType.OFFSET })
    }
}
