package com.yukisora.yukiaccount.data.repository

import androidx.room.withTransaction
import com.yukisora.yukiaccount.data.backup.BackupImportResult
import com.yukisora.yukiaccount.data.backup.BackupMapper
import com.yukisora.yukiaccount.data.backup.BackupService
import com.yukisora.yukiaccount.data.db.YukiAccountDatabase
import com.yukisora.yukiaccount.data.model.AccountEntity
import com.yukisora.yukiaccount.data.model.InvestmentAssetEntity
import com.yukisora.yukiaccount.data.model.ValuationSnapshotEntity
import com.yukisora.yukiaccount.data.model.toDomain
import com.yukisora.yukiaccount.data.model.toEntity
import com.yukisora.yukiaccount.domain.model.Account
import com.yukisora.yukiaccount.domain.model.AccountType
import com.yukisora.yukiaccount.domain.model.InvestmentAsset
import com.yukisora.yukiaccount.domain.model.InvestmentType
import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.Transaction
import com.yukisora.yukiaccount.domain.model.TransactionType
import com.yukisora.yukiaccount.domain.service.LedgerCalculator
import com.yukisora.yukiaccount.domain.service.RecurringGenerator
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class AccountingRepository(
    private val database: YukiAccountDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val backupService: BackupService = BackupService(),
) {
    suspend fun ensureSeedData() {
        database.withTransaction {
            val now = clock()
            if (database.accountDao().activeAccounts().isEmpty()) {
                defaultAccounts(now).forEach { database.accountDao().upsert(it) }
            }
            if (database.investmentDao().activeInvestments().isEmpty()) {
                defaultInvestments(now).forEach { database.investmentDao().upsertAsset(it) }
            }
        }
    }

    fun observeDashboard(): Flow<DashboardSummary> =
        combine(
            database.accountDao().observeActiveAccounts().map { entities -> entities.map { it.toDomain() } },
            database.investmentDao().observeActiveInvestments().map { entities -> entities.map { it.toDomain() } },
            database.transactionDao().observeTransactions().map { entities -> entities.map { it.toDomain() } },
        ) { accounts, investments, transactions ->
            DashboardSummary(
                monthlyConsumption = LedgerCalculator.monthlyRealConsumption(transactions, YearMonth.now()),
                netWorth = LedgerCalculator.netWorth(accounts, investments),
            )
        }

    fun observeTransactions(): Flow<List<Transaction>> =
        database.transactionDao().observeTransactions().map { entities -> entities.map { it.toDomain() } }

    fun observeAccounts(): Flow<List<Account>> =
        database.accountDao().observeActiveAccounts().map { entities -> entities.map { it.toDomain() } }

    fun observeInvestments(): Flow<List<InvestmentAsset>> =
        database.investmentDao().observeActiveInvestments().map { entities -> entities.map { it.toDomain() } }

    suspend fun addTransaction(transaction: Transaction) {
        database.withTransaction {
            addTransactionInCurrentTransaction(transaction)
        }
    }

    suspend fun addExpense(amount: Money, accountId: String, note: String) {
        addTransaction(
            Transaction(
                id = UUID.randomUUID().toString(),
                type = TransactionType.EXPENSE,
                amount = amount,
                accountId = accountId,
                date = LocalDate.now(),
                note = note,
            )
        )
    }

    suspend fun addIncome(amount: Money, accountId: String, note: String) {
        addTransaction(
            Transaction(
                id = UUID.randomUUID().toString(),
                type = TransactionType.INCOME,
                amount = amount,
                accountId = accountId,
                date = LocalDate.now(),
                note = note,
            )
        )
    }

    suspend fun addInvestmentBuy(amount: Money, accountId: String, investmentAssetId: String, note: String) {
        addTransaction(
            Transaction(
                id = UUID.randomUUID().toString(),
                type = TransactionType.INVESTMENT_BUY,
                amount = amount,
                accountId = accountId,
                investmentAssetId = investmentAssetId,
                date = LocalDate.now(),
                note = note,
            )
        )
    }


    suspend fun updateInvestmentValue(investmentAssetId: String, value: Money, date: LocalDate = LocalDate.now()) {
        database.withTransaction {
            val now = clock()
            val asset = requireNotNull(database.investmentDao().getAsset(investmentAssetId)) {
                "Investment asset not found: $investmentAssetId"
            }
            database.investmentDao().updateAsset(
                InvestmentAssetEntity(
                    id = asset.id,
                    name = asset.name,
                    type = asset.type,
                    principalCents = asset.principalCents,
                    currentValueCents = value.cents,
                    lastValuationDate = date,
                    isArchived = asset.isArchived,
                    createdAt = asset.createdAt,
                    updatedAt = now,
                )
            )
            database.investmentDao().insertValuation(
                ValuationSnapshotEntity(
                    id = UUID.randomUUID().toString(),
                    investmentAssetId = investmentAssetId,
                    date = date,
                    valueCents = value.cents,
                    note = "",
                    createdAt = now,
                )
            )
        }
    }

    suspend fun generateRecurringTransactions(today: LocalDate = LocalDate.now()): Int =
        database.withTransaction {
            val now = clock()
            val result = RecurringGenerator.generate(
                rules = database.recurringRuleDao().enabledRules().map { it.toDomain() },
                existingTransactions = database.transactionDao().allTransactions().map { it.toDomain() },
                skippedOccurrences = database.recurringRuleDao().skippedOccurrences().map { it.toDomain() },
                today = today,
            )

            result.transactions.forEach { addTransactionInCurrentTransaction(it) }
            result.updatedRules.forEach { database.recurringRuleDao().update(it.toEntity(now)) }
            result.transactions.size
        }

    suspend fun exportBackupJson(): String =
        database.withTransaction {
            backupService.export(
                BackupMapper.toDocument(
                    accounts = database.accountDao().allAccounts(),
                    categories = database.categoryDao().allCategories(),
                    transactions = database.transactionDao().allTransactions(),
                    recurringRules = database.recurringRuleDao().allRules(),
                    investmentAssets = database.investmentDao().allInvestments(),
                    valuationSnapshots = database.investmentDao().allValuations(),
                    skippedOccurrences = database.recurringRuleDao().skippedOccurrences(),
                )
            )
        }

    suspend fun importBackupJson(rawJson: String): BackupImportResult {
        val result = backupService.parseForImport(rawJson)
        if (result !is BackupImportResult.Valid) {
            return result
        }

        val entities = try {
            BackupMapper.toEntities(result.document)
        } catch (error: IllegalArgumentException) {
            return BackupImportResult.Invalid("备份文件内容无效")
        } catch (error: DateTimeParseException) {
            return BackupImportResult.Invalid("备份文件日期无效")
        }

        database.withTransaction {
            database.accountDao().upsertAll(entities.accounts)
            database.categoryDao().upsertAll(entities.categories)
            database.recurringRuleDao().upsertAll(entities.recurringRules)
            database.investmentDao().upsertAssets(entities.investmentAssets)
            database.transactionDao().upsertAll(entities.transactions)
            database.investmentDao().upsertValuations(entities.valuationSnapshots)
            database.recurringRuleDao().upsertSkippedAll(entities.skippedOccurrences)
        }

        return result
    }

    private suspend fun addTransactionInCurrentTransaction(transaction: Transaction) {
        val accountEntities = database.accountDao().activeAccounts()
        val investmentEntities = database.investmentDao().activeInvestments()
        val accounts = accountEntities.map { it.toDomain() }
        val investments = investmentEntities.map { it.toDomain() }
        val ledger = LedgerCalculator.applyTransaction(accounts, investments, transaction)
        val now = clock()

        ledger.accounts.forEach { account ->
            val original = accountEntities.first { it.id == account.id }
            database.accountDao().update(
                account.toEntity(now).copy(createdAt = original.createdAt)
            )
        }
        ledger.investments.forEach { investment ->
            val original = investmentEntities.firstOrNull { it.id == investment.id } ?: return@forEach
            database.investmentDao().updateAsset(
                investment.toEntity(now).copy(createdAt = original.createdAt)
            )
        }
        database.transactionDao().insert(transaction.toEntity(now))
    }
}

data class DashboardSummary(
    val monthlyConsumption: Money,
    val netWorth: Money,
)

private fun defaultAccounts(now: Long): List<AccountEntity> =
    listOf(
        AccountEntity(
            id = "cash",
            name = "现金",
            type = AccountType.CASH,
            balanceCents = 0,
            creditLimitCents = null,
            billingDay = null,
            repaymentDay = null,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        ),
        AccountEntity(
            id = "bank",
            name = "银行卡",
            type = AccountType.BANK_CARD,
            balanceCents = 0,
            creditLimitCents = null,
            billingDay = null,
            repaymentDay = null,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        ),
        AccountEntity(
            id = "credit-card",
            name = "信用卡",
            type = AccountType.CREDIT_CARD,
            balanceCents = 0,
            creditLimitCents = null,
            billingDay = 1,
            repaymentDay = 20,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        ),
    )

private fun defaultInvestments(now: Long): List<InvestmentAssetEntity> =
    listOf(
        InvestmentAssetEntity(
            id = "fund",
            name = "基金",
            type = InvestmentType.FUND,
            principalCents = 0,
            currentValueCents = 0,
            lastValuationDate = null,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        ),
        InvestmentAssetEntity(
            id = "gold",
            name = "黄金",
            type = InvestmentType.GOLD,
            principalCents = 0,
            currentValueCents = 0,
            lastValuationDate = null,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        ),
    )
