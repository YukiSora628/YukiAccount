package com.yukisora.yukiaccount

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import java.time.LocalDate
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class AccountingFlowsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun waitForSeedData() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("记支出") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun recordsNormalExpense() {
        val note = unique("UI测试普通支出")

        composeRule.onNodeWithText("记支出").performClick()
        composeRule.onNodeWithTag("money-entry-amount").performTextInput("12.34")
        composeRule.onNodeWithTag("money-entry-note").performTextInput(note)
        composeRule.onNodeWithText("保存支出").performClick()

        openTransactionsAndAssertNote(note)
    }

    @Test
    fun recordsCreditCardExpenseAndRepayment() {
        val expenseNote = unique("UI测试信用卡消费")
        val repaymentNote = unique("UI测试信用卡还款")

        composeRule.onNodeWithText("记支出").performClick()
        composeRule.onNodeWithTag("money-entry-amount").performTextInput("23.45")
        composeRule.onNodeWithTag("money-entry-account").performClick()
        composeRule.onNodeWithText("信用卡").performClick()
        composeRule.onNodeWithTag("money-entry-note").performTextInput(expenseNote)
        composeRule.onNodeWithText("保存支出").performClick()

        composeRule.onNodeWithText("信用卡还款").performClick()
        composeRule.onNodeWithTag("repayment-amount").performTextInput("10.00")
        composeRule.onNodeWithTag("repayment-note").performTextInput(repaymentNote)
        composeRule.onNodeWithText("保存还款").performClick()

        openTransactionsAndAssertNote(expenseNote)
        assertTransactionNote(repaymentNote)
    }

    @Test
    fun createsRecurringRuleAndShowsGeneratedEntryPrompt() {
        val ruleName = unique("UI测试月度会员")

        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("新增周期规则").performClick()
        composeRule.onNodeWithTag("recurring-name").performTextInput(ruleName)
        composeRule.onNodeWithTag("recurring-amount").performTextInput("15.00")
        composeRule.onNodeWithText("保存规则").performClick()
        composeRule.onNodeWithText(ruleName).assertIsDisplayed()

        composeRule.onNodeWithText("首页").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("已自动补记", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun recordsInvestmentBuy() {
        val note = unique("UI测试基金买入")

        composeRule.onNodeWithText("投资买入").performClick()
        composeRule.onNodeWithTag("investment-buy-amount").performTextInput("30.00")
        composeRule.onNodeWithTag("investment-buy-note").performTextInput(note)
        composeRule.onNodeWithText("保存买入").performClick()

        openTransactionsAndAssertNote(note)
    }

    @Test
    fun updatesInvestmentValuation() {
        composeRule.onNodeWithText("更新市值").performClick()
        composeRule.onNodeWithTag("valuation-value").performTextInput("123.45")
        composeRule.onNodeWithText("更新").performClick()

        composeRule.onNodeWithText("投资").performClick()
        composeRule.onNodeWithText("最近市值 ${LocalDate.now()}").assertIsDisplayed()
    }

    private fun openTransactionsAndAssertNote(note: String) {
        composeRule.onNodeWithText("流水").performClick()
        assertTransactionNote(note)
    }

    private fun assertTransactionNote(note: String) {
        val noteMatcher = hasText(note, substring = true)
        composeRule.onNodeWithTag("transaction-list").performScrollToNode(noteMatcher)
        composeRule.onNode(noteMatcher).assertIsDisplayed()
    }

    private fun unique(prefix: String): String = "$prefix-${System.nanoTime()}"
}
