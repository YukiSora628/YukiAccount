package com.yukisora.yukiaccount

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import com.yukisora.yukiaccount.data.db.YukiAccountDatabase
import com.yukisora.yukiaccount.data.repository.AccountingRepository
import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.RecurringFrequency
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertNull
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
    fun showsCreditCardBillingDetails() {
        composeRule.onNodeWithText("账户").performClick()

        composeRule.onNodeWithText("信用额度 未设置").assertIsDisplayed()
        composeRule.onNodeWithText("账单日 1 日 / 还款日 20 日").assertIsDisplayed()
    }

    @Test
    fun archivedAccountRemainsVisibleWithItsBalance() {
        val accountName = unique("UI测试归档账户")

        composeRule.onNodeWithText("账户").performClick()
        composeRule.onNodeWithText("新增账户").performClick()
        composeRule.onNodeWithTag("account-name").performTextInput(accountName)
        composeRule.onNodeWithTag("account-balance").performTextInput("12.34")
        composeRule.onNodeWithText("保存账户").performClick()

        composeRule.onNodeWithTag("archive-account-$accountName").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("$accountName（已归档）")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("$accountName（已归档）").assertIsDisplayed()
        composeRule.onNodeWithText("余额 ¥12.34").assertIsDisplayed()
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
    fun returningToForegroundGeneratesPendingRecurringTransactions() {
        val ruleName = unique("UI测试恢复补记")
        val today = LocalDate.now()
        val database = YukiAccountDatabase.getInstance(composeRule.activity.applicationContext)
        val repository = AccountingRepository(database)
        val ruleId = runBlocking {
            repository.addSubscriptionRule(
                name = ruleName,
                amount = Money.cents(1_500),
                accountId = "bank",
                frequency = RecurringFrequency.MONTHLY,
                startDate = today,
            )
            database.recurringRuleDao().allRules().first { it.name == ruleName }.id
        }
        assertNull(runBlocking { database.transactionDao().findAutoGenerated(ruleId, today) })

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { database.transactionDao().findAutoGenerated(ruleId, today) != null }
        }
    }

    @Test
    fun createsAndArchivesCustomCategory() {
        val categoryName = unique("UI测试分类")

        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("新增分类").performClick()
        composeRule.onNodeWithTag("category-name").performTextInput(categoryName)
        composeRule.onNodeWithText("保存分类").performClick()
        composeRule.onNodeWithText(categoryName).assertIsDisplayed()

        composeRule.onNodeWithTag("archive-category-$categoryName").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText(categoryName)).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun archivedCategoryNameRemainsVisibleInTransactionHistory() {
        val categoryName = unique("UI测试历史分类")
        val note = unique("UI测试归档分类流水")

        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("新增分类").performClick()
        composeRule.onNodeWithTag("category-name").performTextInput(categoryName)
        composeRule.onNodeWithText("保存分类").performClick()

        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithText("记支出").performClick()
        composeRule.onNodeWithTag("money-entry-amount").performTextInput("8.88")
        composeRule.onNodeWithTag("money-entry-category").performClick()
        composeRule.onNodeWithText(categoryName).performClick()
        composeRule.onNodeWithTag("money-entry-note").performTextInput(note)
        composeRule.onNodeWithText("保存支出").performClick()

        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithTag("archive-category-$categoryName").performScrollTo().performClick()

        composeRule.onNodeWithText("流水").performClick()
        composeRule.onNodeWithTag("transaction-list").performScrollToNode(hasText(note, substring = true))
        composeRule.onNodeWithText("$categoryName（已归档）", substring = true).assertIsDisplayed()
    }

    @Test
    fun resetLocalDataRequiresConfirmation() {
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithTag("reset-local-data").performScrollTo().performClick()

        composeRule.onNodeWithText("重置本地数据").assertIsDisplayed()
        composeRule.onNodeWithText("确认重置").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("新增分类").assertIsDisplayed()
    }

    @Test
    fun recordsInvestmentBuy() {
        val note = unique("UI测试基金买入")

        composeRule.onNodeWithText("投资买入").performClick()
        composeRule.onNodeWithTag("investment-buy-amount").performTextInput("30.00")
        composeRule.onNodeWithTag("investment-buy-note").performTextInput(note)
        composeRule.onNodeWithText("保存买入").performClick()

        openTransactionsAndAssertNote(note)
        val directionMatcher = hasText("投资 银行卡 → 基金", substring = true)
        composeRule.onNodeWithTag("transaction-list").performScrollToNode(directionMatcher)
        composeRule.onNode(directionMatcher).assertIsDisplayed()
    }

    @Test
    fun archivedInvestmentRemainsVisibleWithItsValue() {
        val investmentName = unique("UI测试归档投资")

        composeRule.onNodeWithText("投资").performClick()
        composeRule.onNodeWithText("新增投资资产").performClick()
        composeRule.onNodeWithTag("investment-name").performTextInput(investmentName)
        composeRule.onNodeWithTag("investment-principal").performTextInput("10.00")
        composeRule.onNodeWithTag("investment-current-value").performTextInput("12.00")
        composeRule.onNodeWithText("保存资产").performClick()

        composeRule.onNodeWithTag("archive-investment-$investmentName").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("$investmentName（已归档）")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("$investmentName（已归档）").assertIsDisplayed()
        composeRule.onNodeWithText("本金 ¥10.00 / 市值 ¥12.00 / 浮盈浮亏 ¥2.00").assertIsDisplayed()
    }

    @Test
    fun updatesInvestmentValuation() {
        composeRule.onNodeWithText("更新市值").performClick()
        composeRule.onNodeWithTag("valuation-value").performTextInput("0")
        composeRule.onNodeWithText("更新").performClick()

        composeRule.onNodeWithText("投资").performClick()
        composeRule.onNodeWithText("最近市值 ${LocalDate.now()}").assertIsDisplayed()
        composeRule.onNodeWithTag("valuation-history-toggle-fund").performClick()
        composeRule.onNodeWithText("${LocalDate.now()} 市值 ¥0.00").assertIsDisplayed()
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
