package com.yukisora.yukiaccount.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yukisora.yukiaccount.data.db.YukiAccountDatabase
import com.yukisora.yukiaccount.data.model.toEntity
import com.yukisora.yukiaccount.domain.model.InvestmentType
import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.RecurringFrequency
import com.yukisora.yukiaccount.domain.service.RecurringRuleFactory
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AccountingRepositoryTest {
    private lateinit var database: YukiAccountDatabase
    private lateinit var repository: AccountingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, YukiAccountDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AccountingRepository(database, clock = { 100L })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun seedDataIsNotRestoredWhenAllAccountsAndInvestmentsAreArchived() = runBlocking {
        repository.ensureSeedData()
        database.accountDao().allAccounts().forEach { account ->
            database.accountDao().update(account.copy(isArchived = true))
        }
        database.investmentDao().allInvestments().forEach { investment ->
            database.investmentDao().updateAsset(investment.copy(isArchived = true))
        }

        repository.ensureSeedData()

        assertTrue(database.accountDao().activeAccounts().isEmpty())
        assertTrue(database.investmentDao().activeInvestments().isEmpty())
        assertTrue(database.accountDao().allAccounts().all { it.isArchived })
        assertTrue(database.investmentDao().allInvestments().all { it.isArchived })
    }

    @Test
    fun recurringUndoRestoresArchivedAccountAndInvestment() = runBlocking {
        val today = LocalDate.of(2026, 7, 10)
        repository.ensureSeedData()
        repository.addInvestmentBuyRule(
            name = "每日基金定投",
            amount = Money.cents(1_000),
            accountId = "bank",
            investmentAssetId = "fund",
            frequency = RecurringFrequency.DAILY,
            startDate = today,
        )
        val generated = repository.generateRecurringTransactions(today)
        repository.archiveAccount("bank")
        repository.archiveInvestmentAsset("fund")

        val undoneCount = repository.undoGeneratedTransactions(generated.transactionIds)

        assertEquals(1, undoneCount)
        assertEquals(0L, database.accountDao().getById("bank")?.balanceCents)
        assertEquals(0L, database.investmentDao().getAsset("fund")?.principalCents)
        assertTrue(database.transactionDao().allTransactions().isEmpty())
    }

    @Test
    fun archivingDoesNotChangeNetWorth() = runBlocking {
        repository.ensureSeedData()
        repository.addIncome(
            amount = Money.cents(10_000),
            accountId = "bank",
            categoryId = "salary",
            note = "期初资金",
        )
        repository.updateInvestmentValue("fund", Money.cents(5_000))
        val beforeArchive = repository.observeDashboard().first().netWorth

        repository.archiveAccount("bank")
        repository.archiveInvestmentAsset("fund")
        val afterArchive = repository.observeDashboard().first().netWorth

        assertEquals(beforeArchive, afterArchive)
    }

    @Test
    fun systemCategoriesAreRestoredBeforeInvestmentAndSubscriptionOperations() = runBlocking {
        val today = LocalDate.of(2026, 7, 10)
        repository.ensureSeedData()
        database.categoryDao().clearAll()

        repository.addInvestmentBuy(
            amount = Money.cents(1_000),
            accountId = "bank",
            investmentAssetId = "fund",
            date = today,
            note = "基金定投",
        )
        repository.addSubscriptionRule(
            name = "视频会员",
            amount = Money.cents(2_000),
            accountId = "bank",
            frequency = RecurringFrequency.MONTHLY,
            startDate = today,
        )

        val categories = database.categoryDao().allCategories().associateBy { it.id }
        assertEquals("investment", categories.getValue("investment-input").type)
        assertEquals("expense", categories.getValue("subscription").type)
        assertTrue(categories.getValue("subscription").isFixedExpense)
        assertTrue(
            database.transactionDao().allTransactions().all { transaction ->
                transaction.categoryId == null || transaction.categoryId in categories
            }
        )
    }

    @Test
    fun archivingCustomCategoryDisablesDependentRulesAndPreservesHistory() = runBlocking {
        val today = LocalDate.of(2026, 7, 10)
        repository.ensureSeedData()
        repository.addCategory("房租", "expense", isFixedExpense = true)
        val category = database.categoryDao().allCategories().first { it.name == "房租" }
        val rule = RecurringRuleFactory.subscriptionExpense(
            id = "rent-rule",
            name = "房租",
            amount = Money.cents(200_000),
            accountId = "bank",
            categoryId = category.id,
            frequency = RecurringFrequency.MONTHLY,
            startDate = today,
        )
        database.recurringRuleDao().upsert(rule.toEntity(now = 100L))

        repository.archiveCategory(category.id)

        assertTrue(requireNotNull(database.categoryDao().getById(category.id)).isArchived)
        assertEquals(false, requireNotNull(database.recurringRuleDao().getRule(rule.id)).enabled)
    }

    @Test(expected = IllegalArgumentException::class)
    fun systemCategoryCannotBeArchived() = runBlocking {
        repository.ensureSeedData()

        repository.archiveCategory("subscription")
    }

    @Test
    fun archivingFixedExpenseCategoryDoesNotRewriteDashboardHistory() = runBlocking {
        repository.ensureSeedData()
        repository.addCategory("房租", "expense", isFixedExpense = true)
        val category = database.categoryDao().allCategories().first { it.name == "房租" }
        repository.addExpense(
            amount = Money.cents(200_000),
            accountId = "bank",
            categoryId = category.id,
            date = YearMonth.now().atDay(1),
            note = "本月房租",
        )
        val beforeArchive = repository.observeDashboard().first().monthlyFixedExpense

        repository.archiveCategory(category.id)
        val afterArchive = repository.observeDashboard().first().monthlyFixedExpense

        assertEquals(Money.cents(200_000), beforeArchive)
        assertEquals(beforeArchive, afterArchive)
    }

    @Test
    fun valuationHistoryIsObservedNewestFirst() = runBlocking {
        repository.ensureSeedData()
        repository.updateInvestmentValue(
            investmentAssetId = "fund",
            value = Money.cents(10_000),
            date = LocalDate.of(2026, 7, 9),
        )
        repository.updateInvestmentValue(
            investmentAssetId = "fund",
            value = Money.cents(10_500),
            date = LocalDate.of(2026, 7, 10),
        )

        val history = repository.observeValuations().first()

        assertEquals(2, history.size)
        assertEquals(LocalDate.of(2026, 7, 10), history[0].date)
        assertEquals(Money.cents(10_500), history[0].value)
        assertEquals(LocalDate.of(2026, 7, 9), history[1].date)
        assertEquals("fund", history[1].investmentAssetId)
    }

    @Test
    fun creatingInvestmentAssetAddsInitialValuationSnapshot() = runBlocking {
        val valuationDate = LocalDate.of(2026, 7, 10)

        repository.addInvestmentAsset(
            name = "沪深300",
            type = InvestmentType.FUND,
            principal = Money.cents(20_000),
            currentValue = Money.cents(21_500),
            valuationDate = valuationDate,
        )

        val asset = database.investmentDao().allInvestments().single()
        val snapshot = database.investmentDao().allValuations().single()
        assertEquals(asset.id, snapshot.investmentAssetId)
        assertEquals(valuationDate, asset.lastValuationDate)
        assertEquals(valuationDate, snapshot.date)
        assertEquals(asset.currentValueCents, snapshot.valueCents)
    }

    @Test
    fun archivedCategoriesRemainAvailableForTransactionHistory() = runBlocking {
        repository.ensureSeedData()
        repository.addCategory("房租", "expense", isFixedExpense = true)
        val category = database.categoryDao().allCategories().first { it.name == "房租" }

        repository.archiveCategory(category.id)

        assertTrue(repository.observeCategories().first().none { it.id == category.id })
        val historicalCategory = repository.observeAllCategories().first().single { it.id == category.id }
        assertEquals("房租", historicalCategory.name)
        assertTrue(historicalCategory.isArchived)
    }

    @Test
    fun resetLocalDataClearsUserDataAndRestoresDefaults() = runBlocking {
        val today = LocalDate.of(2026, 7, 10)
        repository.ensureSeedData()
        repository.addIncome(
            amount = Money.cents(50_000),
            accountId = "bank",
            categoryId = "salary",
            date = today,
            note = "测试收入",
        )
        repository.addSubscriptionRule(
            name = "视频会员",
            amount = Money.cents(1_500),
            accountId = "bank",
            frequency = RecurringFrequency.MONTHLY,
            startDate = today,
        )
        val rule = database.recurringRuleDao().allRules().single()
        repository.skipNextRecurringOccurrence(rule.id)
        repository.updateInvestmentValue("fund", Money.cents(12_345), today)
        repository.addCategory("房租", "expense", isFixedExpense = true)

        repository.resetLocalData()

        assertTrue(database.transactionDao().allTransactions().isEmpty())
        assertTrue(database.recurringRuleDao().allRules().isEmpty())
        assertTrue(database.recurringRuleDao().skippedOccurrences().isEmpty())
        assertTrue(database.investmentDao().allValuations().isEmpty())
        assertEquals(setOf("cash", "bank", "credit-card"), database.accountDao().allAccounts().map { it.id }.toSet())
        assertTrue(database.accountDao().allAccounts().all { !it.isArchived && it.balanceCents == 0L })
        assertEquals(setOf("food", "subscription", "salary", "investment-input"), database.categoryDao().allCategories().map { it.id }.toSet())
        assertEquals(setOf("fund", "gold"), database.investmentDao().allInvestments().map { it.id }.toSet())
        assertTrue(
            database.investmentDao().allInvestments().all {
                !it.isArchived && it.principalCents == 0L && it.currentValueCents == 0L
            }
        )
    }

    @Test
    fun disabledRuleCannotBeEnabledWhenAnyReferenceIsArchived() = runBlocking {
        val today = LocalDate.of(2026, 7, 10)
        repository.ensureSeedData()
        repository.addSubscriptionRule(
            name = "视频会员",
            amount = Money.cents(1_500),
            accountId = "bank",
            frequency = RecurringFrequency.MONTHLY,
            startDate = today,
        )
        val accountRuleId = database.recurringRuleDao().allRules().single().id
        repository.archiveAccount("bank")
        assertCannotEnable(accountRuleId)

        repository.resetLocalData()
        repository.addInvestmentBuyRule(
            name = "基金定投",
            amount = Money.cents(1_000),
            accountId = "bank",
            investmentAssetId = "fund",
            frequency = RecurringFrequency.DAILY,
            startDate = today,
        )
        val investmentRuleId = database.recurringRuleDao().allRules().single().id
        repository.archiveInvestmentAsset("fund")
        assertCannotEnable(investmentRuleId)

        repository.resetLocalData()
        repository.addCategory("房租", "expense", isFixedExpense = true)
        val category = database.categoryDao().allCategories().first { it.name == "房租" }
        val categoryRule = RecurringRuleFactory.subscriptionExpense(
            id = "rent-rule",
            name = "房租",
            amount = Money.cents(200_000),
            accountId = "bank",
            categoryId = category.id,
            frequency = RecurringFrequency.MONTHLY,
            startDate = today,
        )
        database.recurringRuleDao().upsert(categoryRule.toEntity(now = 100L))
        repository.archiveCategory(category.id)
        assertCannotEnable(categoryRule.id)
    }

    @Test
    fun ruleCreationRejectsArchivedAccountAndInvestment() = runBlocking {
        val today = LocalDate.of(2026, 7, 10)
        repository.ensureSeedData()
        repository.archiveAccount("bank")
        assertRejected {
            repository.addSubscriptionRule(
                name = "视频会员",
                amount = Money.cents(1_500),
                accountId = "bank",
                frequency = RecurringFrequency.MONTHLY,
                startDate = today,
            )
        }

        repository.resetLocalData()
        repository.archiveInvestmentAsset("fund")
        assertRejected {
            repository.addInvestmentBuyRule(
                name = "基金定投",
                amount = Money.cents(1_000),
                accountId = "bank",
                investmentAssetId = "fund",
                frequency = RecurringFrequency.DAILY,
                startDate = today,
            )
        }
    }

    @Test
    fun generationDisablesLegacyRuleWithArchivedReference() = runBlocking {
        val today = LocalDate.of(2026, 7, 10)
        repository.ensureSeedData()
        repository.addSubscriptionRule(
            name = "视频会员",
            amount = Money.cents(1_500),
            accountId = "bank",
            frequency = RecurringFrequency.MONTHLY,
            startDate = today,
        )
        val rule = database.recurringRuleDao().allRules().single()
        val account = requireNotNull(database.accountDao().getById("bank"))
        database.accountDao().update(account.copy(isArchived = true))

        val generated = repository.generateRecurringTransactions(today)

        assertEquals(0, generated.count)
        assertEquals(false, requireNotNull(database.recurringRuleDao().getRule(rule.id)).enabled)
        assertTrue(database.transactionDao().allTransactions().isEmpty())
    }

    private suspend fun assertCannotEnable(ruleId: String) {
        assertRejected {
            repository.setRecurringRuleEnabled(ruleId, enabled = true)
        }
    }

    private suspend fun assertRejected(block: suspend () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Expected operation with archived reference to be rejected", rejected)
    }
}
