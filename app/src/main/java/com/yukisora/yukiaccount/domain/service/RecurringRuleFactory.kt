package com.yukisora.yukiaccount.domain.service

import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.RecurringFrequency
import com.yukisora.yukiaccount.domain.model.RecurringRule
import com.yukisora.yukiaccount.domain.model.TransactionType
import java.time.LocalDate

object RecurringRuleFactory {
    fun subscriptionExpense(
        id: String,
        name: String,
        amount: Money,
        accountId: String,
        categoryId: String,
        frequency: RecurringFrequency,
        startDate: LocalDate,
    ): RecurringRule =
        RecurringRule(
            id = id,
            name = name,
            transactionType = TransactionType.EXPENSE,
            amount = amount,
            accountId = accountId,
            categoryId = categoryId,
            frequency = frequency,
            startDate = startDate,
            nextOccurrenceDate = startDate,
        )

    fun investmentBuy(
        id: String,
        name: String,
        amount: Money,
        accountId: String,
        investmentAssetId: String,
        categoryId: String,
        frequency: RecurringFrequency,
        startDate: LocalDate,
    ): RecurringRule =
        RecurringRule(
            id = id,
            name = name,
            transactionType = TransactionType.INVESTMENT_BUY,
            amount = amount,
            accountId = accountId,
            categoryId = categoryId,
            investmentAssetId = investmentAssetId,
            frequency = frequency,
            startDate = startDate,
            nextOccurrenceDate = startDate,
        )
}
