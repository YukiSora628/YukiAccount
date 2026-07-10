package com.yukisora.yukiaccount.domain.service

import com.yukisora.yukiaccount.domain.model.Account
import com.yukisora.yukiaccount.domain.model.AccountType
import com.yukisora.yukiaccount.domain.model.InvestmentAsset
import com.yukisora.yukiaccount.domain.model.LedgerState
import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.Transaction
import com.yukisora.yukiaccount.domain.model.TransactionType
import java.time.YearMonth

object LedgerCalculator {
    fun applyTransaction(
        accounts: List<Account>,
        investments: List<InvestmentAsset>,
        transaction: Transaction,
    ): LedgerState {
        validateTransactionTargets(accounts, investments, transaction)

        val updatedAccounts = when (transaction.type) {
            TransactionType.EXPENSE -> accounts.updateAccount(transaction.accountId) { account ->
                if (account.type == AccountType.CREDIT_CARD) {
                    account.copy(balance = account.balance + transaction.amount)
                } else {
                    account.copy(balance = account.balance - transaction.amount)
                }
            }
            TransactionType.INCOME -> accounts.updateAccount(transaction.accountId) { account ->
                account.copy(balance = account.balance + transaction.amount)
            }
            TransactionType.REFUND -> accounts.updateAccount(transaction.accountId) { account ->
                if (account.type == AccountType.CREDIT_CARD) {
                    account.copy(balance = account.balance - transaction.amount)
                } else {
                    account.copy(balance = account.balance + transaction.amount)
                }
            }
            TransactionType.TRANSFER -> accounts
                .updateAccount(transaction.accountId) { it.copy(balance = it.balance - transaction.amount) }
                .updateAccount(requireNotNull(transaction.targetAccountId)) { it.copy(balance = it.balance + transaction.amount) }
            TransactionType.CREDIT_CARD_REPAYMENT -> accounts
                .updateAccount(transaction.accountId) { it.copy(balance = it.balance - transaction.amount) }
                .updateAccount(requireNotNull(transaction.targetAccountId)) { it.copy(balance = it.balance - transaction.amount) }
            TransactionType.INVESTMENT_BUY -> accounts
                .updateAccount(transaction.accountId) { it.copy(balance = it.balance - transaction.amount) }
        }

        val updatedInvestments = if (transaction.type == TransactionType.INVESTMENT_BUY) {
            investments.updateInvestment(requireNotNull(transaction.investmentAssetId)) { investment ->
                investment.copy(principal = investment.principal + transaction.amount)
            }
        } else {
            investments
        }

        return LedgerState(updatedAccounts, updatedInvestments)
    }

    fun revertTransaction(
        accounts: List<Account>,
        investments: List<InvestmentAsset>,
        transaction: Transaction,
    ): LedgerState {
        validateTransactionTargets(accounts, investments, transaction)

        val updatedAccounts = when (transaction.type) {
            TransactionType.EXPENSE -> accounts.updateAccount(transaction.accountId) { account ->
                if (account.type == AccountType.CREDIT_CARD) {
                    account.copy(balance = account.balance - transaction.amount)
                } else {
                    account.copy(balance = account.balance + transaction.amount)
                }
            }
            TransactionType.INCOME -> accounts.updateAccount(transaction.accountId) { account ->
                account.copy(balance = account.balance - transaction.amount)
            }
            TransactionType.REFUND -> accounts.updateAccount(transaction.accountId) { account ->
                if (account.type == AccountType.CREDIT_CARD) {
                    account.copy(balance = account.balance + transaction.amount)
                } else {
                    account.copy(balance = account.balance - transaction.amount)
                }
            }
            TransactionType.TRANSFER -> accounts
                .updateAccount(transaction.accountId) { it.copy(balance = it.balance + transaction.amount) }
                .updateAccount(requireNotNull(transaction.targetAccountId)) { it.copy(balance = it.balance - transaction.amount) }
            TransactionType.CREDIT_CARD_REPAYMENT -> accounts
                .updateAccount(transaction.accountId) { it.copy(balance = it.balance + transaction.amount) }
                .updateAccount(requireNotNull(transaction.targetAccountId)) { it.copy(balance = it.balance + transaction.amount) }
            TransactionType.INVESTMENT_BUY -> accounts
                .updateAccount(transaction.accountId) { it.copy(balance = it.balance + transaction.amount) }
        }

        val updatedInvestments = if (transaction.type == TransactionType.INVESTMENT_BUY) {
            investments.updateInvestment(requireNotNull(transaction.investmentAssetId)) { investment ->
                investment.copy(principal = investment.principal - transaction.amount)
            }
        } else {
            investments
        }

        return LedgerState(updatedAccounts, updatedInvestments)
    }

    fun monthlyRealConsumption(transactions: List<Transaction>, month: YearMonth): Money =
        transactions
            .filter { YearMonth.from(it.date) == month }
            .fold(Money.ZERO) { total, transaction ->
                when (transaction.type) {
                    TransactionType.EXPENSE -> total + transaction.amount
                    TransactionType.REFUND -> total - transaction.amount
                    TransactionType.INCOME,
                    TransactionType.TRANSFER,
                    TransactionType.CREDIT_CARD_REPAYMENT,
                    TransactionType.INVESTMENT_BUY -> total
                }
            }

    fun monthlyFixedExpense(
        transactions: List<Transaction>,
        fixedExpenseCategoryIds: Set<String>,
        month: YearMonth,
    ): Money =
        transactions
            .filter { YearMonth.from(it.date) == month && it.categoryId in fixedExpenseCategoryIds }
            .fold(Money.ZERO) { total, transaction ->
                when (transaction.type) {
                    TransactionType.EXPENSE -> total + transaction.amount
                    TransactionType.REFUND -> total - transaction.amount
                    TransactionType.INCOME,
                    TransactionType.TRANSFER,
                    TransactionType.CREDIT_CARD_REPAYMENT,
                    TransactionType.INVESTMENT_BUY -> total
                }
            }

    fun monthlyInvestmentInput(transactions: List<Transaction>, month: YearMonth): Money =
        transactions
            .filter { YearMonth.from(it.date) == month && it.type == TransactionType.INVESTMENT_BUY }
            .fold(Money.ZERO) { total, transaction -> total + transaction.amount }

    fun netWorth(accounts: List<Account>, investments: List<InvestmentAsset>): Money {
        val accountTotal = accounts.fold(Money.ZERO) { total, account ->
            if (account.type == AccountType.CREDIT_CARD) {
                total - account.balance
            } else {
                total + account.balance
            }
        }
        val investmentTotal = investments.fold(Money.ZERO) { total, investment ->
            total + investment.currentValue
        }
        return accountTotal + investmentTotal
    }

    fun investmentGainLoss(investment: InvestmentAsset): Money =
        investment.currentValue - investment.principal

    fun totalInvestmentGainLoss(investments: List<InvestmentAsset>): Money =
        investments.fold(Money.ZERO) { total, investment ->
            total + investmentGainLoss(investment)
        }

    private fun validateTransactionTargets(
        accounts: List<Account>,
        investments: List<InvestmentAsset>,
        transaction: Transaction,
    ) {
        require(transaction.amount > Money.ZERO) { "Transaction amount must be positive" }
        val sourceAccount = requireNotNull(accounts.firstOrNull { it.id == transaction.accountId }) {
            "Transaction source account not found: ${transaction.accountId}"
        }

        when (transaction.type) {
            TransactionType.EXPENSE,
            TransactionType.REFUND -> Unit
            TransactionType.INCOME -> require(sourceAccount.type != AccountType.CREDIT_CARD) {
                "Income account must be an asset account"
            }
            TransactionType.TRANSFER -> {
                require(sourceAccount.type != AccountType.CREDIT_CARD) {
                    "Transfer source must be an asset account"
                }
                val targetAccountId = requireNotNull(transaction.targetAccountId) {
                    "Transfer target account is required"
                }
                require(targetAccountId != sourceAccount.id) {
                    "Transfer source and target accounts must differ"
                }
                val targetAccount = requireNotNull(accounts.firstOrNull { it.id == targetAccountId }) {
                    "Transfer target account not found: $targetAccountId"
                }
                require(targetAccount.type != AccountType.CREDIT_CARD) {
                    "Transfer target must be an asset account"
                }
            }
            TransactionType.CREDIT_CARD_REPAYMENT -> {
                require(sourceAccount.type != AccountType.CREDIT_CARD) {
                    "Repayment source must be an asset account"
                }
                val targetAccountId = requireNotNull(transaction.targetAccountId) {
                    "Repayment target account is required"
                }
                require(targetAccountId != sourceAccount.id) {
                    "Repayment source and target accounts must differ"
                }
                val targetAccount = requireNotNull(accounts.firstOrNull { it.id == targetAccountId }) {
                    "Repayment target account not found: $targetAccountId"
                }
                require(targetAccount.type == AccountType.CREDIT_CARD) {
                    "Repayment target must be a credit card account"
                }
            }
            TransactionType.INVESTMENT_BUY -> {
                require(sourceAccount.type != AccountType.CREDIT_CARD) {
                    "Investment source must be an asset account"
                }
                val investmentAssetId = requireNotNull(transaction.investmentAssetId) {
                    "Investment asset is required"
                }
                require(investments.any { it.id == investmentAssetId }) {
                    "Investment asset not found: $investmentAssetId"
                }
            }
        }
    }

    private fun List<Account>.updateAccount(id: String, transform: (Account) -> Account): List<Account> =
        map { account -> if (account.id == id) transform(account) else account }

    private fun List<InvestmentAsset>.updateInvestment(
        id: String,
        transform: (InvestmentAsset) -> InvestmentAsset,
    ): List<InvestmentAsset> =
        map { investment -> if (investment.id == id) transform(investment) else investment }
}
