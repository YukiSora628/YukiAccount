package com.yukisora.yukiaccount.domain.service

import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.Transaction
import com.yukisora.yukiaccount.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionFilterTest {
    @Test
    fun filtersTransactionsByMonthTypeAndCategory() {
        val target = transaction(
            id = "target",
            type = TransactionType.EXPENSE,
            categoryId = "food",
            date = LocalDate.of(2026, 7, 8),
        )
        val transactions = listOf(
            target,
            transaction(
                id = "wrong-month",
                type = TransactionType.EXPENSE,
                categoryId = "food",
                date = LocalDate.of(2026, 6, 30),
            ),
            transaction(
                id = "wrong-type",
                type = TransactionType.INCOME,
                categoryId = "food",
                date = LocalDate.of(2026, 7, 8),
            ),
            transaction(
                id = "wrong-category",
                type = TransactionType.EXPENSE,
                categoryId = "subscription",
                date = LocalDate.of(2026, 7, 8),
            ),
        )

        val result = TransactionFilter.filter(
            transactions = transactions,
            month = YearMonth.of(2026, 7),
            type = TransactionType.EXPENSE,
            categoryId = "food",
        )

        assertEquals(listOf(target), result)
    }

    @Test
    fun filtersTransactionsByAccountAsSourceOrTarget() {
        val sourceTransaction = transaction(
            id = "source",
            type = TransactionType.EXPENSE,
            accountId = "bank",
            categoryId = "food",
            date = LocalDate.of(2026, 7, 8),
        )
        val targetTransaction = transaction(
            id = "target",
            type = TransactionType.TRANSFER,
            accountId = "cash",
            targetAccountId = "bank",
            categoryId = null,
            date = LocalDate.of(2026, 7, 9),
        )
        val unrelated = transaction(
            id = "unrelated",
            type = TransactionType.INCOME,
            accountId = "alipay",
            categoryId = "salary",
            date = LocalDate.of(2026, 7, 10),
        )

        val result = TransactionFilter.filter(
            transactions = listOf(sourceTransaction, targetTransaction, unrelated),
            accountId = "bank",
        )

        assertEquals(listOf(sourceTransaction, targetTransaction), result)
    }

    private fun transaction(
        id: String,
        type: TransactionType,
        accountId: String = "bank",
        targetAccountId: String? = null,
        categoryId: String?,
        date: LocalDate,
    ) = Transaction(
        id = id,
        type = type,
        amount = Money.cents(1_000),
        accountId = accountId,
        targetAccountId = targetAccountId,
        categoryId = categoryId,
        date = date,
    )
}
