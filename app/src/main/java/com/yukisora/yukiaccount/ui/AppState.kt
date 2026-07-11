package com.yukisora.yukiaccount.ui

import com.yukisora.yukiaccount.domain.model.Account
import com.yukisora.yukiaccount.domain.model.AccountType
import com.yukisora.yukiaccount.domain.model.InvestmentAsset
import com.yukisora.yukiaccount.domain.model.InvestmentType
import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.Transaction
import com.yukisora.yukiaccount.domain.model.TransactionType
import com.yukisora.yukiaccount.domain.service.LedgerCalculator
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.UUID

data class AppState(
    val accounts: List<Account> = defaultAccounts,
    val investments: List<InvestmentAsset> = defaultInvestments,
    val transactions: List<Transaction> = emptyList(),
) {
    val monthlyConsumption: Money =
        LedgerCalculator.monthlyRealConsumption(transactions, YearMonth.now())

    val netWorth: Money =
        LedgerCalculator.netWorth(accounts, investments)
}

fun AppState.addExpense(amount: Money, accountId: String, note: String): AppState =
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

fun AppState.addIncome(amount: Money, accountId: String, note: String): AppState =
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

fun AppState.addInvestmentBuy(amount: Money, accountId: String, investmentId: String, note: String): AppState =
    addTransaction(
        Transaction(
            id = UUID.randomUUID().toString(),
            type = TransactionType.INVESTMENT_BUY,
            amount = amount,
            accountId = accountId,
            investmentAssetId = investmentId,
            date = LocalDate.now(),
            note = note,
        )
    )

fun AppState.updateInvestmentValue(investmentId: String, value: Money): AppState =
    copy(
        investments = investments.map { investment ->
            if (investment.id == investmentId) {
                investment.copy(currentValue = value, lastValuationDate = LocalDate.now())
            } else {
                investment
            }
        }
    )

private fun AppState.addTransaction(transaction: Transaction): AppState {
    val ledger = LedgerCalculator.applyTransaction(accounts, investments, transaction)
    return copy(
        accounts = ledger.accounts,
        investments = ledger.investments,
        transactions = listOf(transaction) + transactions,
    )
}

fun Money.formatCurrency(): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.CHINA)
    return formatter.format(cents / 100.0)
}

fun String.toMoneyOrNull(): Money? {
    val value = toNonNegativeMoneyOrNull() ?: return null
    return value.takeIf { it > Money.ZERO }
}

fun String.toNonNegativeMoneyOrNull(): Money? {
    val normalized = trim()
    if (normalized.isEmpty()) return null
    val value = normalized.toBigDecimalOrNull() ?: return null
    if (value < java.math.BigDecimal.ZERO) return null
    return runCatching {
        Money.cents(
            value
                .movePointRight(2)
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact()
        )
    }.getOrNull()
}

private val defaultAccounts = listOf(
    Account(
        id = "cash",
        name = "现金",
        type = AccountType.CASH,
        balance = Money.cents(0),
    ),
    Account(
        id = "bank",
        name = "银行卡",
        type = AccountType.BANK_CARD,
        balance = Money.cents(0),
    ),
    Account(
        id = "credit-card",
        name = "信用卡",
        type = AccountType.CREDIT_CARD,
        balance = Money.cents(0),
    ),
)

private val defaultInvestments = listOf(
    InvestmentAsset(
        id = "fund",
        name = "基金",
        type = InvestmentType.FUND,
        principal = Money.ZERO,
        currentValue = Money.ZERO,
    ),
    InvestmentAsset(
        id = "gold",
        name = "黄金",
        type = InvestmentType.GOLD,
        principal = Money.ZERO,
        currentValue = Money.ZERO,
    ),
)
