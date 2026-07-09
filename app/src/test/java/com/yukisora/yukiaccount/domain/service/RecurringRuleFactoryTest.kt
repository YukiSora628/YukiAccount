package com.yukisora.yukiaccount.domain.service

import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.RecurringFrequency
import com.yukisora.yukiaccount.domain.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecurringRuleFactoryTest {
    @Test
    fun monthlySubscriptionCreatesExpenseRuleFromStartDate() {
        val rule = RecurringRuleFactory.subscriptionExpense(
            id = "rule-subscription",
            name = "视频会员",
            amount = Money.cents(1_500),
            accountId = "bank",
            categoryId = "subscription",
            frequency = RecurringFrequency.MONTHLY,
            startDate = LocalDate.of(2026, 7, 8),
        )

        assertEquals("rule-subscription", rule.id)
        assertEquals("视频会员", rule.name)
        assertEquals(TransactionType.EXPENSE, rule.transactionType)
        assertEquals(Money.cents(1_500), rule.amount)
        assertEquals("bank", rule.accountId)
        assertEquals("subscription", rule.categoryId)
        assertEquals(RecurringFrequency.MONTHLY, rule.frequency)
        assertEquals(LocalDate.of(2026, 7, 8), rule.startDate)
        assertEquals(LocalDate.of(2026, 7, 8), rule.nextOccurrenceDate)
        assertTrue(rule.enabled)
    }

    @Test
    fun dailyInvestmentPlanCreatesInvestmentBuyRule() {
        val rule = RecurringRuleFactory.investmentBuy(
            id = "rule-dca",
            name = "黄金定投",
            amount = Money.cents(2_000),
            accountId = "bank",
            investmentAssetId = "gold",
            categoryId = "investment-input",
            frequency = RecurringFrequency.DAILY,
            startDate = LocalDate.of(2026, 7, 8),
        )

        assertEquals(TransactionType.INVESTMENT_BUY, rule.transactionType)
        assertEquals("gold", rule.investmentAssetId)
        assertEquals("investment-input", rule.categoryId)
        assertEquals(RecurringFrequency.DAILY, rule.frequency)
        assertEquals(LocalDate.of(2026, 7, 8), rule.nextOccurrenceDate)
    }

    @Test
    fun skipNextOccurrenceMarksCurrentNextDateAndAdvancesRule() {
        val rule = RecurringRuleFactory.subscriptionExpense(
            id = "rule-subscription",
            name = "video",
            amount = Money.cents(1_500),
            accountId = "bank",
            categoryId = "subscription",
            frequency = RecurringFrequency.MONTHLY,
            startDate = LocalDate.of(2026, 7, 8),
        )

        val result = RecurringRuleFactory.skipNextOccurrence(rule, reason = "pause")

        assertEquals(rule.id, result.skippedOccurrence.recurringRuleId)
        assertEquals(LocalDate.of(2026, 7, 8), result.skippedOccurrence.occurrenceDate)
        assertEquals("pause", result.skippedOccurrence.reason)
        assertEquals(LocalDate.of(2026, 8, 8), result.updatedRule.nextOccurrenceDate)
    }

    @Test
    fun subscriptionRuleKeepsOptionalEndDate() {
        val rule = RecurringRuleFactory.subscriptionExpense(
            id = "rule-subscription",
            name = "视频会员",
            amount = Money.cents(1_500),
            accountId = "bank",
            categoryId = "subscription",
            frequency = RecurringFrequency.MONTHLY,
            startDate = LocalDate.of(2026, 7, 8),
            endDate = LocalDate.of(2026, 12, 8),
        )

        assertEquals(LocalDate.of(2026, 7, 8), rule.startDate)
        assertEquals(LocalDate.of(2026, 12, 8), rule.endDate)
        assertEquals(LocalDate.of(2026, 7, 8), rule.nextOccurrenceDate)
    }
}
