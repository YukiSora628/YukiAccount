package com.yukisora.yukiaccount.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yukisora.yukiaccount.data.backup.BackupImportResult
import com.yukisora.yukiaccount.data.backup.BackupMapper
import com.yukisora.yukiaccount.data.backup.BackupService
import com.yukisora.yukiaccount.data.db.YukiAccountDatabase
import com.yukisora.yukiaccount.data.model.AccountEntity
import com.yukisora.yukiaccount.domain.model.AccountType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountingRepositoryImportTest {
    private lateinit var database: YukiAccountDatabase
    private lateinit var repository: AccountingRepository

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
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
    fun validBackupReplacesExistingDatabaseContents() = runBlocking {
        repository.ensureSeedData()
        assertTrue(database.accountDao().allAccounts().size > 1)

        val document = BackupMapper.toDocument(
            accounts = listOf(restoredAccount()),
            categories = emptyList(),
            transactions = emptyList(),
            recurringRules = emptyList(),
            investmentAssets = emptyList(),
            valuationSnapshots = emptyList(),
            skippedOccurrences = emptyList(),
        )

        val result = repository.importBackupJson(BackupService().export(document))

        assertTrue(result is BackupImportResult.Valid)
        assertEquals(listOf("restored-account"), database.accountDao().allAccounts().map { it.id })
        assertTrue(database.categoryDao().allCategories().isEmpty())
        assertTrue(database.transactionDao().allTransactions().isEmpty())
        assertTrue(database.recurringRuleDao().allRules().isEmpty())
        assertTrue(database.investmentDao().allInvestments().isEmpty())
    }

    @Test
    fun invalidBackupDoesNotChangeExistingDatabaseContents() = runBlocking {
        repository.ensureSeedData()
        val accountIdsBeforeImport = database.accountDao().allAccounts().map { it.id }
        val invalidJson =
            """{"schemaVersion":999,"accounts":[],"categories":[],"transactions":[],"recurringRules":[],"investmentAssets":[],"valuationSnapshots":[],"skippedOccurrences":[]}"""

        val result = repository.importBackupJson(invalidJson)

        assertTrue(result is BackupImportResult.Invalid)
        assertEquals(accountIdsBeforeImport, database.accountDao().allAccounts().map { it.id })
    }

    private fun restoredAccount() = AccountEntity(
        id = "restored-account",
        name = "恢复账户",
        type = AccountType.BANK_CARD,
        balanceCents = 12_345,
        creditLimitCents = null,
        billingDay = null,
        repaymentDay = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 2,
    )
}
