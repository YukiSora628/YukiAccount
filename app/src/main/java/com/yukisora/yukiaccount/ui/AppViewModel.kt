package com.yukisora.yukiaccount.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yukisora.yukiaccount.data.backup.BackupImportResult
import com.yukisora.yukiaccount.data.db.YukiAccountDatabase
import com.yukisora.yukiaccount.data.repository.AccountingRepository
import com.yukisora.yukiaccount.data.repository.DashboardSummary
import com.yukisora.yukiaccount.domain.model.Account
import com.yukisora.yukiaccount.domain.model.AccountType
import com.yukisora.yukiaccount.domain.model.Category
import com.yukisora.yukiaccount.domain.model.InvestmentAsset
import com.yukisora.yukiaccount.domain.model.InvestmentType
import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.RecurringFrequency
import com.yukisora.yukiaccount.domain.model.RecurringRule
import com.yukisora.yukiaccount.domain.model.Transaction
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AccountingRepository(
        YukiAccountDatabase.getInstance(application)
    )
    private val generatedRecurringTransactionIds = MutableStateFlow<List<String>>(emptyList())

    val uiState: StateFlow<AccountingUiState> =
        combine(
            combine(
                repository.observeDashboard(),
                repository.observeTransactions(),
                repository.observeAccounts(),
                repository.observeInvestments(),
                generatedRecurringTransactionIds,
            ) { dashboard, transactions, accounts, investments, recurringTransactionIds ->
                AccountingUiState(
                    dashboard = dashboard,
                    transactions = transactions,
                    accounts = accounts,
                    investments = investments,
                    recurringGenerationCount = recurringTransactionIds.size,
                )
            },
            repository.observeCategories(),
        ) { state, categories ->
            state.copy(categories = categories)
        }.let { baseState ->
            combine(
                baseState,
                repository.observeRecurringRules(),
            ) { state, recurringRules ->
                state.copy(recurringRules = recurringRules)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AccountingUiState(),
        )

    init {
        viewModelScope.launch {
            repository.ensureSeedData()
            generatedRecurringTransactionIds.value = repository.generateRecurringTransactions().transactionIds
        }
    }

    fun addAssetAccount(name: String, type: AccountType, balance: Money) {
        viewModelScope.launch {
            repository.addAssetAccount(name, type, balance)
        }
    }

    fun addCreditCardAccount(
        name: String,
        unpaidBalance: Money,
        creditLimit: Money?,
        billingDay: Int?,
        repaymentDay: Int?,
    ) {
        viewModelScope.launch {
            repository.addCreditCardAccount(name, unpaidBalance, creditLimit, billingDay, repaymentDay)
        }
    }

    fun addInvestmentAsset(name: String, type: InvestmentType, principal: Money, currentValue: Money) {
        viewModelScope.launch {
            repository.addInvestmentAsset(name, type, principal, currentValue)
        }
    }

    fun archiveAccount(account: Account) {
        viewModelScope.launch {
            repository.archiveAccount(account.id)
        }
    }

    fun archiveInvestmentAsset(investment: InvestmentAsset) {
        viewModelScope.launch {
            repository.archiveInvestmentAsset(investment.id)
        }
    }

    fun addCategory(name: String, type: String, isFixedExpense: Boolean) {
        viewModelScope.launch {
            repository.addCategory(name, type, isFixedExpense)
        }
    }

    fun addExpense(amount: Money, account: Account, category: Category?, date: LocalDate, note: String) {
        viewModelScope.launch {
            repository.addExpense(amount, account.id, category?.id, date, note)
        }
    }

    fun addIncome(amount: Money, account: Account, category: Category?, date: LocalDate, note: String) {
        viewModelScope.launch {
            repository.addIncome(amount, account.id, category?.id, date, note)
        }
    }

    fun addRefund(amount: Money, account: Account, category: Category?, date: LocalDate, note: String) {
        viewModelScope.launch {
            repository.addRefund(amount, account.id, category?.id, date, note)
        }
    }

    fun addTransfer(amount: Money, sourceAccount: Account, targetAccount: Account, date: LocalDate, note: String) {
        viewModelScope.launch {
            repository.addTransfer(amount, sourceAccount.id, targetAccount.id, date, note)
        }
    }

    fun addInvestmentBuy(amount: Money, account: Account, investment: InvestmentAsset, date: LocalDate, note: String) {
        viewModelScope.launch {
            repository.addInvestmentBuy(amount, account.id, investment.id, date, note)
        }
    }

    fun addCreditCardRepayment(
        amount: Money,
        sourceAccount: Account,
        creditCardAccount: Account,
        date: LocalDate,
        note: String,
    ) {
        viewModelScope.launch {
            repository.addCreditCardRepayment(amount, sourceAccount.id, creditCardAccount.id, date, note)
        }
    }

    fun addSubscriptionRule(
        name: String,
        amount: Money,
        account: Account,
        frequency: RecurringFrequency,
        startDate: LocalDate,
        endDate: LocalDate?,
    ) {
        viewModelScope.launch {
            repository.addSubscriptionRule(name, amount, account.id, frequency, startDate, endDate)
        }
    }

    fun addInvestmentBuyRule(
        name: String,
        amount: Money,
        account: Account,
        investment: InvestmentAsset,
        frequency: RecurringFrequency,
        startDate: LocalDate,
        endDate: LocalDate?,
    ) {
        viewModelScope.launch {
            repository.addInvestmentBuyRule(name, amount, account.id, investment.id, frequency, startDate, endDate)
        }
    }

    fun skipNextRecurringOccurrence(rule: RecurringRule) {
        viewModelScope.launch {
            repository.skipNextRecurringOccurrence(rule.id)
        }
    }

    fun setRecurringRuleEnabled(rule: RecurringRule, enabled: Boolean) {
        viewModelScope.launch {
            repository.setRecurringRuleEnabled(rule.id, enabled)
        }
    }

    fun updateInvestmentValue(investment: InvestmentAsset, value: Money, date: LocalDate) {
        viewModelScope.launch {
            repository.updateInvestmentValue(investment.id, value, date)
        }
    }

    fun undoLastRecurringGeneration() {
        viewModelScope.launch {
            val transactionIds = generatedRecurringTransactionIds.value
            if (transactionIds.isNotEmpty()) {
                repository.undoGeneratedTransactions(transactionIds)
                generatedRecurringTransactionIds.value = emptyList()
            }
        }
    }

    suspend fun exportBackupJson(): String =
        repository.exportBackupJson()

    suspend fun importBackupJson(rawJson: String): BackupImportResult =
        repository.importBackupJson(rawJson)
}

data class AccountingUiState(
    val dashboard: DashboardSummary = DashboardSummary(
        monthlyConsumption = Money.ZERO,
        monthlyFixedExpense = Money.ZERO,
        monthlyInvestmentInput = Money.ZERO,
        investmentGainLoss = Money.ZERO,
        netWorth = Money.ZERO,
    ),
    val transactions: List<Transaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val investments: List<InvestmentAsset> = emptyList(),
    val recurringRules: List<RecurringRule> = emptyList(),
    val recurringGenerationCount: Int = 0,
)
