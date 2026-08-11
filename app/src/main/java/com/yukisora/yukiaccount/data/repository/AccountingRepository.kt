package com.yukisora.yukiaccount.data.repository

import androidx.room.withTransaction
import com.yukisora.yukiaccount.data.backup.BackupImportResult
import com.yukisora.yukiaccount.data.backup.BackupMapper
import com.yukisora.yukiaccount.data.backup.BackupService
import com.yukisora.yukiaccount.data.db.YukiAccountDatabase
import com.yukisora.yukiaccount.data.model.AccountEntity
import com.yukisora.yukiaccount.data.model.CategoryEntity
import com.yukisora.yukiaccount.data.model.InvestmentAssetEntity
import com.yukisora.yukiaccount.data.model.ValuationSnapshotEntity
import com.yukisora.yukiaccount.data.model.toDomain
import com.yukisora.yukiaccount.data.model.toEntity
import com.yukisora.yukiaccount.domain.model.Account
import com.yukisora.yukiaccount.domain.model.AccountType
import com.yukisora.yukiaccount.domain.model.Category
import com.yukisora.yukiaccount.domain.model.InvestmentAsset
import com.yukisora.yukiaccount.domain.model.InvestmentType
import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.RecurringFrequency
import com.yukisora.yukiaccount.domain.model.RecurringRule
import com.yukisora.yukiaccount.domain.model.Transaction
import com.yukisora.yukiaccount.domain.model.TransactionType
import com.yukisora.yukiaccount.domain.model.ValuationSnapshot
import com.yukisora.yukiaccount.domain.service.AccountFactory
import com.yukisora.yukiaccount.domain.service.CategoryFactory
import com.yukisora.yukiaccount.domain.service.InvestmentAssetFactory
import com.yukisora.yukiaccount.domain.service.LedgerCalculator
import com.yukisora.yukiaccount.domain.service.RecurringGenerator
import com.yukisora.yukiaccount.domain.service.RecurringRuleFactory
import com.yukisora.yukiaccount.domain.service.TransactionFactory
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
            if (database.accountDao().allAccounts().isEmpty()) {
                defaultAccounts(now).forEach { database.accountDao().upsert(it) }
            }
            if (database.investmentDao().allInvestments().isEmpty()) {
                defaultInvestments(now).forEach { database.investmentDao().upsertAsset(it) }
            }
            if (database.categoryDao().allCategories().isEmpty()) {
                database.categoryDao().upsertAll(defaultCategories())
            }
        }
    }

    fun observeDashboard(): Flow<DashboardSummary> =
        combine(
            database.accountDao().observeAllAccounts().map { entities -> entities.map { it.toDomain() } },
            database.investmentDao().observeAllInvestments().map { entities -> entities.map { it.toDomain() } },
            database.transactionDao().observeTransactions().map { entities -> entities.map { it.toDomain() } },
            database.categoryDao().observeAllCategories(),
        ) { accounts, investments, transactions, categories ->
            val fixedExpenseCategoryIds = categories
                .filter { it.isFixedExpense }
                .map { it.id }
                .toSet()
            DashboardSummary(
                monthlyConsumption = LedgerCalculator.monthlyRealConsumption(transactions, YearMonth.now()),
                monthlyFixedExpense = LedgerCalculator.monthlyFixedExpense(
                    transactions = transactions,
                    fixedExpenseCategoryIds = fixedExpenseCategoryIds,
                    month = YearMonth.now(),
                ),
                monthlyInvestmentInput = LedgerCalculator.monthlyInvestmentInput(transactions, YearMonth.now()),
                investmentGainLoss = LedgerCalculator.totalInvestmentGainLoss(investments),
                netWorth = LedgerCalculator.netWorth(accounts, investments),
            )
        }

    fun observeTransactions(): Flow<List<Transaction>> =
        database.transactionDao().observeTransactions().map { entities -> entities.map { it.toDomain() } }

    fun observeAccounts(): Flow<List<Account>> =
        database.accountDao().observeActiveAccounts().map { entities -> entities.map { it.toDomain() } }

    fun observeAllAccounts(): Flow<List<Account>> =
        database.accountDao().observeAllAccounts().map { entities -> entities.map { it.toDomain() } }

    fun observeCategories(): Flow<List<Category>> =
        database.categoryDao().observeActiveCategories().map { entities -> entities.map { it.toDomain() } }

    fun observeAllCategories(): Flow<List<Category>> =
        database.categoryDao().observeAllCategories().map { entities -> entities.map { it.toDomain() } }

    fun observeInvestments(): Flow<List<InvestmentAsset>> =
        database.investmentDao().observeActiveInvestments().map { entities -> entities.map { it.toDomain() } }

    fun observeAllInvestments(): Flow<List<InvestmentAsset>> =
        database.investmentDao().observeAllInvestments().map { entities -> entities.map { it.toDomain() } }

    fun observeValuations(): Flow<List<ValuationSnapshot>> =
        database.investmentDao().observeValuations().map { entities -> entities.map { it.toDomain() } }

    fun observeRecurringRules(): Flow<List<RecurringRule>> =
        database.recurringRuleDao().observeRules().map { entities -> entities.map { it.toDomain() } }

    suspend fun addCategory(name: String, type: String, isFixedExpense: Boolean) {
        val nextSortOrder = (database.categoryDao().allCategories().maxOfOrNull { it.sortOrder } ?: 0) + 10
        database.categoryDao().upsert(
            CategoryFactory.category(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                type = type,
                isFixedExpense = type == "expense" && isFixedExpense,
                sortOrder = nextSortOrder,
            ).toEntity()
        )
    }

    suspend fun archiveCategory(categoryId: String) {
        require(categoryId !in REQUIRED_SYSTEM_CATEGORY_IDS) {
            "Required system category cannot be archived: $categoryId"
        }
        database.withTransaction {
            val category = requireNotNull(database.categoryDao().getById(categoryId)) {
                "Category not found: $categoryId"
            }.toDomain()
            database.categoryDao().upsert(CategoryFactory.archive(category).toEntity())
            val now = clock()
            database.recurringRuleDao().allRules().forEach { ruleEntity ->
                val rule = ruleEntity.toDomain()
                val updatedRule = RecurringRuleFactory.disableForArchivedCategory(rule, categoryId)
                if (updatedRule != rule) {
                    database.recurringRuleDao().update(
                        updatedRule.toEntity(now).copy(createdAt = ruleEntity.createdAt)
                    )
                }
            }
        }
    }

    suspend fun addInvestmentAsset(
        name: String,
        type: InvestmentType,
        principal: Money,
        currentValue: Money,
        valuationDate: LocalDate? = LocalDate.now(),
    ) {
        database.withTransaction {
            val now = clock()
            val investmentAssetId = UUID.randomUUID().toString()
            database.investmentDao().upsertAsset(
                InvestmentAssetFactory.asset(
                    id = investmentAssetId,
                    name = name,
                    type = type,
                    principal = principal,
                    currentValue = currentValue,
                    valuationDate = valuationDate,
                ).toEntity(now)
            )
            valuationDate?.let { date ->
                database.investmentDao().insertValuation(
                    ValuationSnapshotEntity(
                        id = UUID.randomUUID().toString(),
                        investmentAssetId = investmentAssetId,
                        date = date,
                        valueCents = currentValue.cents,
                        note = "初始市值",
                        createdAt = now,
                    )
                )
            }
        }
    }

    suspend fun addAssetAccount(name: String, type: AccountType, balance: Money) {
        val now = clock()
        database.accountDao().upsert(
            AccountFactory.assetAccount(
                id = UUID.randomUUID().toString(),
                name = name,
                type = type,
                balance = balance,
            ).toEntity(now)
        )
    }

    suspend fun addCreditCardAccount(
        name: String,
        unpaidBalance: Money,
        creditLimit: Money?,
        billingDay: Int?,
        repaymentDay: Int?,
    ) {
        val now = clock()
        database.accountDao().upsert(
            AccountFactory.creditCard(
                id = UUID.randomUUID().toString(),
                name = name,
                unpaidBalance = unpaidBalance,
                creditLimit = creditLimit,
                billingDay = billingDay,
                repaymentDay = repaymentDay,
            ).toEntity(now)
        )
    }

    suspend fun archiveAccount(accountId: String) {
        database.withTransaction {
            val now = clock()
            val entity = requireNotNull(database.accountDao().getById(accountId)) {
                "Account not found: $accountId"
            }
            val archived = AccountFactory.archive(entity.toDomain())
            database.accountDao().update(
                archived.toEntity(now).copy(createdAt = entity.createdAt)
            )
            database.recurringRuleDao().allRules().forEach { ruleEntity ->
                val rule = ruleEntity.toDomain()
                val updatedRule = RecurringRuleFactory.disableForArchivedAccount(rule, accountId)
                if (updatedRule != rule) {
                    database.recurringRuleDao().update(
                        updatedRule.toEntity(now).copy(createdAt = ruleEntity.createdAt)
                    )
                }
            }
        }
    }

    suspend fun archiveInvestmentAsset(investmentAssetId: String) {
        database.withTransaction {
            val now = clock()
            val entity = requireNotNull(database.investmentDao().getAsset(investmentAssetId)) {
                "Investment asset not found: $investmentAssetId"
            }
            val archived = InvestmentAssetFactory.archive(entity.toDomain())
            database.investmentDao().updateAsset(
                archived.toEntity(now).copy(createdAt = entity.createdAt)
            )
            database.recurringRuleDao().allRules().forEach { ruleEntity ->
                val rule = ruleEntity.toDomain()
                val updatedRule = RecurringRuleFactory.disableForArchivedInvestment(rule, investmentAssetId)
                if (updatedRule != rule) {
                    database.recurringRuleDao().update(
                        updatedRule.toEntity(now).copy(createdAt = ruleEntity.createdAt)
                    )
                }
            }
        }
    }

    suspend fun addTransaction(transaction: Transaction) {
        database.withTransaction {
            addTransactionInCurrentTransaction(transaction)
        }
    }

    suspend fun addExpense(
        amount: Money,
        accountId: String,
        categoryId: String?,
        date: LocalDate = LocalDate.now(),
        note: String,
    ) {
        addTransaction(
            TransactionFactory.expense(
                id = UUID.randomUUID().toString(),
                amount = amount,
                accountId = accountId,
                categoryId = categoryId,
                date = date,
                note = note,
            )
        )
    }

    suspend fun addIncome(
        amount: Money,
        accountId: String,
        categoryId: String?,
        date: LocalDate = LocalDate.now(),
        note: String,
    ) {
        addTransaction(
            TransactionFactory.income(
                id = UUID.randomUUID().toString(),
                amount = amount,
                accountId = accountId,
                categoryId = categoryId,
                date = date,
                note = note,
            )
        )
    }

    suspend fun addRefund(
        amount: Money,
        accountId: String,
        categoryId: String? = "food",
        date: LocalDate = LocalDate.now(),
        note: String,
    ) {
        addTransaction(
            TransactionFactory.refund(
                id = UUID.randomUUID().toString(),
                amount = amount,
                accountId = accountId,
                categoryId = categoryId,
                date = date,
                note = note,
            )
        )
    }

    suspend fun addTransfer(
        amount: Money,
        sourceAccountId: String,
        targetAccountId: String,
        categoryId: String?,
        date: LocalDate = LocalDate.now(),
        note: String,
    ) {
        addTransaction(
            TransactionFactory.transfer(
                id = UUID.randomUUID().toString(),
                amount = amount,
                sourceAccountId = sourceAccountId,
                targetAccountId = targetAccountId,
                categoryId = categoryId,
                date = date,
                note = note,
            )
        )
    }

    suspend fun addInvestmentBuy(
        amount: Money,
        accountId: String,
        investmentAssetId: String,
        date: LocalDate = LocalDate.now(),
        note: String,
    ) {
        val transaction = TransactionFactory.investmentBuy(
            id = UUID.randomUUID().toString(),
            amount = amount,
            accountId = accountId,
            categoryId = "investment-input",
            investmentAssetId = investmentAssetId,
            date = date,
            note = note,
        )
        database.withTransaction {
            ensureSystemCategory("investment-input")
            addTransactionInCurrentTransaction(transaction)
        }
    }

    suspend fun addCreditCardRepayment(
        amount: Money,
        sourceAccountId: String,
        creditCardAccountId: String,
        date: LocalDate = LocalDate.now(),
        note: String,
    ) {
        addTransaction(
            TransactionFactory.creditCardRepayment(
                id = UUID.randomUUID().toString(),
                amount = amount,
                sourceAccountId = sourceAccountId,
                creditCardAccountId = creditCardAccountId,
                date = date,
                note = note,
            )
        )
    }

    suspend fun addSubscriptionRule(
        name: String,
        amount: Money,
        accountId: String,
        frequency: RecurringFrequency,
        startDate: LocalDate = LocalDate.now(),
        endDate: LocalDate? = null,
    ) {
        database.withTransaction {
            ensureSystemCategory("subscription")
            val now = clock()
            val rule = RecurringRuleFactory.subscriptionExpense(
                id = UUID.randomUUID().toString(),
                name = name,
                amount = amount,
                accountId = accountId,
                categoryId = "subscription",
                frequency = frequency,
                startDate = startDate,
                endDate = endDate,
            )
            requireActiveRecurringRuleReferences(rule)
            database.recurringRuleDao().upsert(rule.toEntity(now))
        }
    }

    suspend fun addInvestmentBuyRule(
        name: String,
        amount: Money,
        accountId: String,
        investmentAssetId: String,
        frequency: RecurringFrequency,
        startDate: LocalDate = LocalDate.now(),
        endDate: LocalDate? = null,
    ) {
        database.withTransaction {
            ensureSystemCategory("investment-input")
            val now = clock()
            val rule = RecurringRuleFactory.investmentBuy(
                id = UUID.randomUUID().toString(),
                name = name,
                amount = amount,
                accountId = accountId,
                investmentAssetId = investmentAssetId,
                categoryId = "investment-input",
                frequency = frequency,
                startDate = startDate,
                endDate = endDate,
            )
            requireActiveRecurringRuleReferences(rule)
            database.recurringRuleDao().upsert(rule.toEntity(now))
        }
    }

    suspend fun skipNextRecurringOccurrence(ruleId: String, reason: String = "") {
        database.withTransaction {
            val now = clock()
            val ruleEntity = requireNotNull(database.recurringRuleDao().getRule(ruleId)) {
                "Recurring rule not found: $ruleId"
            }
            val result = RecurringRuleFactory.skipNextOccurrence(ruleEntity.toDomain(), reason)
            database.recurringRuleDao().insertSkipped(
                result.skippedOccurrence.toEntity(UUID.randomUUID().toString(), now)
            )
            database.recurringRuleDao().update(
                result.updatedRule.toEntity(now).copy(createdAt = ruleEntity.createdAt)
            )
        }
    }

    suspend fun setRecurringRuleEnabled(ruleId: String, enabled: Boolean) {
        database.withTransaction {
            val now = clock()
            val ruleEntity = requireNotNull(database.recurringRuleDao().getRule(ruleId)) {
                "Recurring rule not found: $ruleId"
            }
            val rule = ruleEntity.toDomain()
            if (enabled) {
                requireActiveRecurringRuleReferences(rule)
            }
            val updatedRule = RecurringRuleFactory.setEnabled(rule, enabled)
            database.recurringRuleDao().update(
                updatedRule.toEntity(now).copy(createdAt = ruleEntity.createdAt)
            )
        }
    }

    suspend fun updateInvestmentValue(investmentAssetId: String, value: Money, date: LocalDate = LocalDate.now()) {
        database.withTransaction {
            val now = clock()
            val asset = requireNotNull(database.investmentDao().getAsset(investmentAssetId)) {
                "Investment asset not found: $investmentAssetId"
            }
            val updatedAsset = InvestmentAssetFactory.updateValuation(
                asset = asset.toDomain(),
                currentValue = value,
                valuationDate = date,
            )
            database.investmentDao().updateAsset(
                updatedAsset.toEntity(now).copy(createdAt = asset.createdAt)
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

    suspend fun generateRecurringTransactions(today: LocalDate = LocalDate.now()): RecurringGenerationSummary =
        database.withTransaction {
            val now = clock()
            val enabledRuleEntities = database.recurringRuleDao().enabledRules()
            val availableRules = enabledRuleEntities.mapNotNull { ruleEntity ->
                val rule = ruleEntity.toDomain()
                if (recurringRuleUnavailableReason(rule) == null) {
                    rule
                } else {
                    database.recurringRuleDao().update(
                        RecurringRuleFactory.setEnabled(rule, enabled = false)
                            .toEntity(now)
                            .copy(createdAt = ruleEntity.createdAt)
                    )
                    null
                }
            }
            val result = RecurringGenerator.generate(
                rules = availableRules,
                existingTransactions = database.transactionDao().allTransactions().map { it.toDomain() },
                skippedOccurrences = database.recurringRuleDao().skippedOccurrences().map { it.toDomain() },
                today = today,
            )

            result.transactions.forEach { addTransactionInCurrentTransaction(it) }
            result.updatedRules.forEach { updatedRule ->
                val original = enabledRuleEntities.first { it.id == updatedRule.id }
                database.recurringRuleDao().update(
                    updatedRule.toEntity(now).copy(createdAt = original.createdAt)
                )
            }
            RecurringGenerationSummary(transactionIds = result.transactions.map { it.id })
        }

    suspend fun undoGeneratedTransactions(transactionIds: List<String>): Int =
        database.withTransaction {
            if (transactionIds.isEmpty()) {
                return@withTransaction 0
            }

            val transactionEntities = database.transactionDao()
                .transactionsByIds(transactionIds)
                .filter { it.autoGenerated }
            if (transactionEntities.isEmpty()) {
                return@withTransaction 0
            }

            val accountEntities = database.accountDao().allAccounts()
            val investmentEntities = database.investmentDao().allInvestments()
            var accounts = accountEntities.map { it.toDomain() }
            var investments = investmentEntities.map { it.toDomain() }

            transactionEntities
                .map { it.toDomain() }
                .forEach { transaction ->
                    val ledger = LedgerCalculator.revertTransaction(accounts, investments, transaction)
                    accounts = ledger.accounts
                    investments = ledger.investments
                }

            val now = clock()
            accounts.forEach { account ->
                val original = accountEntities.first { it.id == account.id }
                database.accountDao().update(
                    account.toEntity(now).copy(createdAt = original.createdAt)
                )
            }
            investments.forEach { investment ->
                val original = investmentEntities.firstOrNull { it.id == investment.id } ?: return@forEach
                database.investmentDao().updateAsset(
                    investment.toEntity(now).copy(createdAt = original.createdAt)
                )
            }

            val earliestOccurrenceByRule = mutableMapOf<String, LocalDate>()
            transactionEntities.forEach { transaction ->
                val ruleId = transaction.recurringRuleId ?: return@forEach
                val occurrenceDate = transaction.occurrenceDate ?: return@forEach
                val currentEarliest = earliestOccurrenceByRule[ruleId]
                if (currentEarliest == null || occurrenceDate.isBefore(currentEarliest)) {
                    earliestOccurrenceByRule[ruleId] = occurrenceDate
                }
            }
            earliestOccurrenceByRule.forEach { (ruleId, earliestOccurrence) ->
                val ruleEntity = database.recurringRuleDao().getRule(ruleId) ?: return@forEach
                val rule = ruleEntity.toDomain()
                val rewoundRule = RecurringRuleFactory.rewindAfterUndo(rule, earliestOccurrence)
                if (rewoundRule != rule) {
                    database.recurringRuleDao().update(
                        rewoundRule.toEntity(now).copy(createdAt = ruleEntity.createdAt)
                    )
                }
            }

            val idsToDelete = transactionEntities.map { it.id }
            database.transactionDao().deleteByIds(idsToDelete)
            idsToDelete.size
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

    suspend fun resetLocalData() {
        database.withTransaction {
            clearAllDataInCurrentTransaction()
            val now = clock()
            database.accountDao().upsertAll(defaultAccounts(now))
            database.categoryDao().upsertAll(defaultCategories())
            database.investmentDao().upsertAssets(defaultInvestments(now))
        }
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
            clearAllDataInCurrentTransaction()

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

    private suspend fun clearAllDataInCurrentTransaction() {
        database.transactionDao().clearAll()
        database.recurringRuleDao().clearSkippedOccurrences()
        database.investmentDao().clearValuations()
        database.recurringRuleDao().clearRules()
        database.investmentDao().clearAssets()
        database.categoryDao().clearAll()
        database.accountDao().clearAll()
    }

    private suspend fun requireActiveRecurringRuleReferences(rule: RecurringRule) {
        recurringRuleUnavailableReason(rule)?.let { reason ->
            throw IllegalArgumentException(reason)
        }
    }

    private suspend fun recurringRuleUnavailableReason(rule: RecurringRule): String? {
        val account = database.accountDao().getById(rule.accountId)
            ?: return "周期规则关联账户不存在"
        if (account.isArchived) return "周期规则关联账户已归档"

        rule.targetAccountId?.let { targetAccountId ->
            val targetAccount = database.accountDao().getById(targetAccountId)
                ?: return "周期规则关联目标账户不存在"
            if (targetAccount.isArchived) return "周期规则关联目标账户已归档"
        }
        rule.categoryId?.let { categoryId ->
            val category = database.categoryDao().getById(categoryId)
                ?: return "周期规则关联分类不存在"
            if (category.isArchived) return "周期规则关联分类已归档"
        }
        rule.investmentAssetId?.let { investmentAssetId ->
            val investment = database.investmentDao().getAsset(investmentAssetId)
                ?: return "周期规则关联投资资产不存在"
            if (investment.isArchived) return "周期规则关联投资资产已归档"
        }
        return null
    }

    private suspend fun addTransactionInCurrentTransaction(transaction: Transaction) {
        transaction.categoryId?.let { categoryId ->
            val category = requireNotNull(database.categoryDao().getById(categoryId)) {
                "Transaction category not found: $categoryId"
            }
            require(!category.isArchived) { "Transaction category is archived: $categoryId" }
            require(category.type == transaction.type.categoryType()) {
                "Transaction category type does not match ${transaction.type}: $categoryId"
            }
        }

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

    private suspend fun ensureSystemCategory(categoryId: String) {
        val canonical = defaultCategories().first { it.id == categoryId }
        val current = database.categoryDao().getById(categoryId)
        if (current == null) {
            database.categoryDao().upsert(canonical)
        } else if (current.type != canonical.type ||
            current.isFixedExpense != canonical.isFixedExpense ||
            current.isArchived
        ) {
            database.categoryDao().upsert(canonical.copy(sortOrder = current.sortOrder))
        }
    }
}

data class DashboardSummary(
    val monthlyConsumption: Money,
    val monthlyFixedExpense: Money,
    val monthlyInvestmentInput: Money,
    val investmentGainLoss: Money,
    val netWorth: Money,
)

data class RecurringGenerationSummary(
    val transactionIds: List<String>,
) {
    val count: Int = transactionIds.size
}

private fun TransactionType.categoryType(): String =
    when (this) {
        TransactionType.EXPENSE,
        TransactionType.REFUND -> "expense"
        TransactionType.INCOME -> "income"
        TransactionType.TRANSFER,
        TransactionType.CREDIT_CARD_REPAYMENT -> "transfer"
        TransactionType.INVESTMENT_BUY -> "investment"
    }

private fun defaultCategories(): List<CategoryEntity> =
    listOf(
        CategoryEntity(
            id = "food",
            name = "餐饮",
            type = "expense",
            isFixedExpense = false,
            sortOrder = 10,
            isArchived = false,
        ),
        CategoryEntity(
            id = "subscription",
            name = "会员订阅",
            type = "expense",
            isFixedExpense = true,
            sortOrder = 20,
            isArchived = false,
        ),
        CategoryEntity(
            id = "salary",
            name = "工资",
            type = "income",
            isFixedExpense = false,
            sortOrder = 30,
            isArchived = false,
        ),
        CategoryEntity(
            id = "investment-input",
            name = "投资投入",
            type = "investment",
            isFixedExpense = false,
            sortOrder = 40,
            isArchived = false,
        ),
    )

private val REQUIRED_SYSTEM_CATEGORY_IDS = setOf("subscription", "investment-input")

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
