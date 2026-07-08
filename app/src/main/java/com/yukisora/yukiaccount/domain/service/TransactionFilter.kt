package com.yukisora.yukiaccount.domain.service

import com.yukisora.yukiaccount.domain.model.Transaction
import com.yukisora.yukiaccount.domain.model.TransactionType
import java.time.YearMonth

object TransactionFilter {
    fun filter(
        transactions: List<Transaction>,
        month: YearMonth? = null,
        type: TransactionType? = null,
        categoryId: String? = null,
    ): List<Transaction> =
        transactions.filter { transaction ->
            (month == null || YearMonth.from(transaction.date) == month) &&
                (type == null || transaction.type == type) &&
                (categoryId == null || transaction.categoryId == categoryId)
        }
}
