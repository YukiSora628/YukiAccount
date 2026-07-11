package com.yukisora.yukiaccount.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yukisora.yukiaccount.data.db.YukiAccountDatabase
import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.RecurringFrequency
import java.time.LocalDate
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
}
