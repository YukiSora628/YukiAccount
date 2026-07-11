package com.yukisora.yukiaccount.domain.service

import com.yukisora.yukiaccount.domain.model.Category

object CategoryFactory {
    fun category(
        id: String,
        name: String,
        type: String,
        isFixedExpense: Boolean,
        sortOrder: Int,
    ): Category =
        Category(
            id = id,
            name = name,
            type = type,
            isFixedExpense = isFixedExpense,
            sortOrder = sortOrder,
        )

    fun archive(category: Category): Category =
        category.copy(isArchived = true)
}
