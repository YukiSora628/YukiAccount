package com.yukisora.yukiaccount.domain.service

import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.RecurringFrequency
import com.yukisora.yukiaccount.domain.model.RecurringRule
import com.yukisora.yukiaccount.domain.model.SkippedOccurrence
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

    fun skipNextOccurrence(rule: RecurringRule, reason: String): RecurringSkipResult {
        val occurrenceDate = rule.nextOccurrenceDate
        return RecurringSkipResult(
            skippedOccurrence = SkippedOccurrence(
                recurringRuleId = rule.id,
                occurrenceDate = occurrenceDate,
                reason = reason,
            ),
            updatedRule = rule.copy(nextOccurrenceDate = occurrenceDate.next(rule.frequency)),
        )
    }

    private fun LocalDate.next(frequency: RecurringFrequency): LocalDate =
        when (frequency) {
            RecurringFrequency.DAILY -> plusDays(1)
            RecurringFrequency.WEEKLY -> plusWeeks(1)
            RecurringFrequency.MONTHLY -> plusMonths(1)
        }
}

data class RecurringSkipResult(
    val skippedOccurrence: SkippedOccurrence,
    val updatedRule: RecurringRule,
)
