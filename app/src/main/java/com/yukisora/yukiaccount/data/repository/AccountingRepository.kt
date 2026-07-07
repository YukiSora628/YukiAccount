package com.yukisora.yukiaccount.data.repository

import androidx.room.withTransaction
import com.yukisora.yukiaccount.data.db.YukiAccountDatabase
import com.yukisora.yukiaccount.data.model.InvestmentAssetEntity
import com.yukisora.yukiaccount.data.model.ValuationSnapshotEntity
import com.yukisora.yukiaccount.data.model.toDomain
import com.yukisora.yukiaccount.data.model.toEntity
import com.yukisora.yukiaccount.domain.model.Account
import com.yukisora.yukiaccount.domain.model.InvestmentAsset
import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.Transaction
import com.yukisora.yukiaccount.domain.service.LedgerCalculator
import com.yukisora.yukiaccount.domain.service.RecurringGenerator
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class AccountingRepository(
    private val database: YukiAccountDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
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
            val original = investmentEntities.first { it.id == investment.id }
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
