package com.yukisora.yukiaccount.domain.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CategoryFactoryTest {
    @Test
    fun categoryKeepsProvidedValues() {
        val category = CategoryFactory.category(
            id = "rent",
            name = "房租",
            type = "expense",
            isFixedExpense = true,
            sortOrder = 50,
        )

        assertEquals("rent", category.id)
        assertEquals("房租", category.name)
        assertEquals("expense", category.type)
        assertEquals(true, category.isFixedExpense)
        assertEquals(50, category.sortOrder)
        assertFalse(category.isArchived)
    }

    @Test
    fun archivePreservesCategoryDetails() {
        val category = CategoryFactory.category(
            id = "rent",
            name = "房租",
            type = "expense",
            isFixedExpense = true,
            sortOrder = 50,
        )

        val archived = CategoryFactory.archive(category)

        assertEquals(category.copy(isArchived = true), archived)
    }
}
