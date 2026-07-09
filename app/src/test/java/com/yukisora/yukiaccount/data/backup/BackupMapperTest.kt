package com.yukisora.yukiaccount.data.backup

import com.yukisora.yukiaccount.data.model.AccountEntity
import com.yukisora.yukiaccount.data.model.CategoryEntity
import com.yukisora.yukiaccount.data.model.InvestmentAssetEntity
import com.yukisora.yukiaccount.data.model.RecurringRuleEntity
import com.yukisora.yukiaccount.data.model.SkippedOccurrenceEntity
import com.yukisora.yukiaccount.data.model.TransactionEntity
import com.yukisora.yukiaccount.data.model.ValuationSnapshotEntity
import com.yukisora.yukiaccount.domain.model.AccountType
import com.yukisora.yukiaccount.domain.model.InvestmentType
import com.yukisora.yukiaccount.domain.model.RecurringFrequency
import com.yukisora.yukiaccount.domain.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupMapperTest {
    @Test
    fun entitiesConvertToBackupDocumentWithSchemaVersion() {
        val document = BackupMapper.toDocument(
            accounts = listOf(account()),
            categories = listOf(category()),
            transactions = listOf(transaction()),
            recurringRules = listOf(recurringRule()),
            investmentAssets = listOf(investment()),
            valuationSnapshots = listOf(valuation()),
            skippedOccurrences = listOf(skippedOccurrence()),
        )

        assertEquals(CURRENT_BACKUP_SCHEMA_VERSION, document.schemaVersion)
        assertEquals("bank", document.accounts.single().id)
        assertEquals("BANK_CARD", document.accounts.single().type)
        assertEquals("EXPENSE", document.transactions.single().type)
        assertEquals("2026-07-08", document.transactions.single().date)
        assertEquals("MONTHLY", document.recurringRules.single().frequency)
        assertEquals("FUND", document.investmentAssets.single().type)
        assertEquals("2026-07-08", document.valuationSnapshots.single().date)
        assertEquals("2026-07-08", document.skippedOccurrences.single().occurrenceDate)
    }

    @Test
    fun unsupportedBackupVersionIsRejected() {
        val invalidJson = """{"schemaVersion":999,"accounts":[],"categories":[],"transactions":[],"recurringRules":[],"investmentAssets":[],"valuationSnapshots":[],"skippedOccurrences":[]}"""

        val result = BackupService().parseForImport(invalidJson)

        assertTrue(result is BackupImportResult.Invalid)
        assertEquals("不支持的备份版本：999", result.reason)
    }

    @Test
    fun duplicateEntityIdsAreRejectedBeforeImport() {
        val document = BackupMapper.toDocument(
            accounts = listOf(account(), account().copy(name = "另一张银行卡")),
            categories = emptyList(),
            transactions = emptyList(),
            recurringRules = emptyList(),
            investmentAssets = emptyList(),
            valuationSnapshots = emptyList(),
            skippedOccurrences = emptyList(),
        )
        val duplicateAccountJson = BackupService().export(document)

        val result = BackupService().parseForImport(duplicateAccountJson)

        assertTrue(result is BackupImportResult.Invalid)
        assertEquals("备份文件包含重复账户 ID", result.reason)
    }

    @Test
    fun transactionWithMissingAccountIsRejectedBeforeImport() {
        val document = BackupMapper.toDocument(
            accounts = emptyList(),
            categories = listOf(category()),
            transactions = listOf(transaction()),
            recurringRules = emptyList(),
            investmentAssets = emptyList(),
            valuationSnapshots = emptyList(),
            skippedOccurrences = emptyList(),
        )
        val invalidReferenceJson = BackupService().export(document)

        val result = BackupService().parseForImport(invalidReferenceJson)

        assertTrue(result is BackupImportResult.Invalid)
        assertEquals("备份文件包含不存在的流水账户 ID", result.reason)
    }

    @Test
    fun transactionWithMissingOptionalReferencesIsRejectedBeforeImport() {
        assertInvalidImport(
            document = backupDocument(
                accounts = listOf(account()),
                transactions = listOf(transaction().copy(targetAccountId = "missing-account")),
            ),
            reason = "备份文件包含不存在的流水目标账户 ID",
        )
        assertInvalidImport(
            document = backupDocument(
                categories = emptyList(),
                transactions = listOf(transaction()),
            ),
            reason = "备份文件包含不存在的流水分类 ID",
        )
        assertInvalidImport(
            document = backupDocument(
                investmentAssets = emptyList(),
                transactions = listOf(transaction().copy(investmentAssetId = "missing-investment")),
            ),
            reason = "备份文件包含不存在的流水投资资产 ID",
        )
        assertInvalidImport(
            document = backupDocument(
                recurringRules = emptyList(),
                transactions = listOf(transaction().copy(recurringRuleId = "missing-rule")),
            ),
            reason = "备份文件包含不存在的流水周期规则 ID",
        )
    }

    @Test
    fun backupDocumentConvertsBackToEntities() {
        val document = BackupMapper.toDocument(
            accounts = listOf(account()),
            categories = listOf(category()),
            transactions = listOf(transaction()),
            recurringRules = listOf(recurringRule()),
            investmentAssets = listOf(investment()),
            valuationSnapshots = listOf(valuation()),
            skippedOccurrences = listOf(skippedOccurrence()),
        )

        val entities = BackupMapper.toEntities(document)

        assertEquals(AccountType.BANK_CARD, entities.accounts.single().type)
        assertEquals(LocalDate.of(2026, 7, 8), entities.transactions.single().date)
        assertEquals(RecurringFrequency.MONTHLY, entities.recurringRules.single().frequency)
        assertEquals(InvestmentType.FUND, entities.investmentAssets.single().type)
        assertEquals(LocalDate.of(2026, 7, 8), entities.valuationSnapshots.single().date)
        assertEquals(LocalDate.of(2026, 7, 8), entities.skippedOccurrences.single().occurrenceDate)
    }

    private fun assertInvalidImport(document: BackupDocument, reason: String) {
        val result = BackupService().parseForImport(BackupService().export(document))

        assertTrue(result is BackupImportResult.Invalid)
        assertEquals(reason, result.reason)
    }

    private fun backupDocument(
        accounts: List<AccountEntity> = listOf(account()),
        categories: List<CategoryEntity> = listOf(category()),
        transactions: List<TransactionEntity> = listOf(transaction()),
        recurringRules: List<RecurringRuleEntity> = listOf(recurringRule()),
        investmentAssets: List<InvestmentAssetEntity> = listOf(investment()),
        valuationSnapshots: List<ValuationSnapshotEntity> = emptyList(),
        skippedOccurrences: List<SkippedOccurrenceEntity> = emptyList(),
    ): BackupDocument =
        BackupMapper.toDocument(
            accounts = accounts,
            categories = categories,
            transactions = transactions,
            recurringRules = recurringRules,
            investmentAssets = investmentAssets,
            valuationSnapshots = valuationSnapshots,
            skippedOccurrences = skippedOccurrences,
        )

    private fun account() = AccountEntity(
        id = "bank",
        name = "银行卡",
        type = AccountType.BANK_CARD,
        balanceCents = 1000,
        creditLimitCents = null,
        billingDay = null,
        repaymentDay = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 2,
    )

    private fun category() = CategoryEntity(
        id = "food",
        name = "餐饮",
        type = "expense",
        isFixedExpense = false,
        sortOrder = 1,
        isArchived = false,
    )

    private fun transaction() = TransactionEntity(
        id = "tx",
        type = TransactionType.EXPENSE,
        amountCents = 500,
        accountId = "bank",
        targetAccountId = null,
        categoryId = "food",
        investmentAssetId = null,
        date = LocalDate.of(2026, 7, 8),
        note = "lunch",
        autoGenerated = false,
        recurringRuleId = null,
        occurrenceDate = null,
        createdAt = 3,
        updatedAt = 4,
    )

    private fun recurringRule() = RecurringRuleEntity(
        id = "rule",
        name = "会员",
        transactionType = TransactionType.EXPENSE,
        amountCents = 1500,
        accountId = "bank",
        targetAccountId = null,
        categoryId = "fixed",
        investmentAssetId = null,
        frequency = RecurringFrequency.MONTHLY,
        startDate = LocalDate.of(2026, 1, 1),
        endDate = null,
        nextOccurrenceDate = LocalDate.of(2026, 8, 1),
        enabled = true,
        createdAt = 5,
        updatedAt = 6,
    )

    private fun investment() = InvestmentAssetEntity(
        id = "fund",
        name = "基金",
        type = InvestmentType.FUND,
        principalCents = 10000,
        currentValueCents = 10200,
        lastValuationDate = LocalDate.of(2026, 7, 8),
        isArchived = false,
        createdAt = 7,
        updatedAt = 8,
    )

    private fun valuation() = ValuationSnapshotEntity(
        id = "valuation",
        investmentAssetId = "fund",
        date = LocalDate.of(2026, 7, 8),
        valueCents = 10200,
        note = "",
        createdAt = 9,
    )

    private fun skippedOccurrence() = SkippedOccurrenceEntity(
        id = "skip",
        recurringRuleId = "rule",
        occurrenceDate = LocalDate.of(2026, 7, 8),
        reason = "手动跳过",
        createdAt = 10,
    )
}
