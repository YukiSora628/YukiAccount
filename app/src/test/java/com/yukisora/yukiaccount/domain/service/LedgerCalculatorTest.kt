package com.yukisora.yukiaccount.domain.service

import com.yukisora.yukiaccount.domain.model.Account
import com.yukisora.yukiaccount.domain.model.AccountType
import com.yukisora.yukiaccount.domain.model.InvestmentAsset
import com.yukisora.yukiaccount.domain.model.InvestmentType
import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.Transaction
import com.yukisora.yukiaccount.domain.model.TransactionType
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class LedgerCalculatorTest {
    private val month = java.time.YearMonth.of(2026, 7)

    @Test
    fun assetExpenseReducesAccountBalance() {
        val account = assetAccount(balance = 10_000)
        val result = LedgerCalculator.applyTransaction(
            accounts = listOf(account),
            investments = emptyList(),
            transaction = transaction(
                type = TransactionType.EXPENSE,
                amount = 2_500,
                accountId = account.id,
            )
        )

        assertEquals(Money.cents(7_500), result.accounts.single().balance)
    }

    @Test
    fun creditCardExpenseIncreasesUnpaidLiability() {
        val card = creditCard(balance = 0)
        val result = LedgerCalculator.applyTransaction(
            accounts = listOf(card),
            investments = emptyList(),
            transaction = transaction(
                type = TransactionType.EXPENSE,
                amount = 3_600,
                accountId = card.id,
            )
        )

        assertEquals(Money.cents(3_600), result.accounts.single().balance)
    }

    @Test
    fun creditCardRepaymentReducesSourceAssetAndCardLiabilityButNotConsumption() {
        val bank = assetAccount(id = "bank", balance = 20_000)
        val card = creditCard(id = "card", balance = 8_000)
        val repayment = transaction(
            type = TransactionType.CREDIT_CARD_REPAYMENT,
            amount = 5_000,
            accountId = bank.id,
            targetAccountId = card.id,
        )

        val result = LedgerCalculator.applyTransaction(listOf(bank, card), emptyList(), repayment)

        assertEquals(Money.cents(15_000), result.accounts.first { it.id == bank.id }.balance)
        assertEquals(Money.cents(3_000), result.accounts.first { it.id == card.id }.balance)
        assertEquals(Money.ZERO, LedgerCalculator.monthlyRealConsumption(listOf(repayment), month))
    }

    @Test
    fun transferIsExcludedFromIncomeAndConsumption() {
        val transfer = transaction(
            type = TransactionType.TRANSFER,
            amount = 7_000,
            accountId = "a",
            targetAccountId = "b",
        )

        assertEquals(Money.ZERO, LedgerCalculator.monthlyRealConsumption(listOf(transfer), month))
    }

    @Test
    fun investmentBuyReducesAssetAccountAndIncreasesPrincipalButNotConsumption() {
        val bank = assetAccount(id = "bank", balance = 30_000)
        val fund = investment(id = "fund", principal = 10_000, currentValue = 10_500)
        val buy = transaction(
            type = TransactionType.INVESTMENT_BUY,
            amount = 4_000,
            accountId = bank.id,
            investmentAssetId = fund.id,
        )

        val result = LedgerCalculator.applyTransaction(listOf(bank), listOf(fund), buy)

        assertEquals(Money.cents(26_000), result.accounts.single().balance)
        assertEquals(Money.cents(14_000), result.investments.single().principal)
        assertEquals(Money.ZERO, LedgerCalculator.monthlyRealConsumption(listOf(buy), month))
    }

    @Test
    fun netWorthUsesAssetBalancesPlusInvestmentValueMinusCreditCardLiability() {
        val bank = assetAccount(balance = 50_000)
        val wallet = assetAccount(id = "wallet", type = AccountType.ALIPAY, balance = 12_000)
        val card = creditCard(balance = 8_500)
        val fund = investment(currentValue = 22_000)

        val netWorth = LedgerCalculator.netWorth(
            accounts = listOf(bank, wallet, card),
            investments = listOf(fund),
        )

        assertEquals(Money.cents(75_500), netWorth)
    }

    @Test
    fun monthlyRealConsumptionIncludesExpenseSubtractsRefundAndExcludesNonConsumption() {
        val transactions = listOf(
            transaction(type = TransactionType.EXPENSE, amount = 10_000),
            transaction(type = TransactionType.REFUND, amount = 1_500),
            transaction(type = TransactionType.INCOME, amount = 30_000),
            transaction(type = TransactionType.TRANSFER, amount = 5_000),
            transaction(type = TransactionType.CREDIT_CARD_REPAYMENT, amount = 3_000),
            transaction(type = TransactionType.INVESTMENT_BUY, amount = 2_000),
            transaction(type = TransactionType.EXPENSE, amount = 9_999, date = LocalDate.of(2026, 6, 30)),
        )

        assertEquals(Money.cents(8_500), LedgerCalculator.monthlyRealConsumption(transactions, month))
    }

    @Test
    fun investmentGainLossIsCurrentValueMinusPrincipal() {
        val fund = investment(principal = 20_000, currentValue = 18_250)

        assertEquals(Money.cents(-1_750), LedgerCalculator.investmentGainLoss(fund))
    }

    @Test
    fun monthlyFixedExpenseOnlyIncludesExpenseInFixedCategories() {
        val transactions = listOf(
            transaction(type = TransactionType.EXPENSE, amount = 1_500, categoryId = "subscription"),
            transaction(type = TransactionType.EXPENSE, amount = 3_000, categoryId = "food"),
            transaction(type = TransactionType.REFUND, amount = 500, categoryId = "subscription"),
            transaction(
                type = TransactionType.EXPENSE,
                amount = 9_999,
                categoryId = "subscription",
                date = LocalDate.of(2026, 6, 30),
            ),
        )

        val fixedExpense = LedgerCalculator.monthlyFixedExpense(
            transactions = transactions,
            fixedExpenseCategoryIds = setOf("subscription"),
            month = month,
        )

        assertEquals(Money.cents(1_000), fixedExpense)
    }

    @Test
    fun monthlyInvestmentInputOnlyIncludesInvestmentBuyInMonth() {
        val transactions = listOf(
            transaction(type = TransactionType.INVESTMENT_BUY, amount = 2_000),
            transaction(type = TransactionType.INVESTMENT_BUY, amount = 3_000),
            transaction(type = TransactionType.EXPENSE, amount = 4_000),
            transaction(
                type = TransactionType.INVESTMENT_BUY,
                amount = 9_999,
                date = LocalDate.of(2026, 6, 30),
            ),
        )

        assertEquals(Money.cents(5_000), LedgerCalculator.monthlyInvestmentInput(transactions, month))
    }

    @Test
    fun totalInvestmentGainLossSumsAllInvestmentAssets() {
        val investments = listOf(
            investment(id = "fund", principal = 20_000, currentValue = 21_500),
            investment(id = "gold", principal = 10_000, currentValue = 9_200),
        )

        assertEquals(Money.cents(700), LedgerCalculator.totalInvestmentGainLoss(investments))
    }

    private fun assetAccount(
        id: String = "asset",
        type: AccountType = AccountType.BANK_CARD,
        balance: Long,
    ) = Account(id = id, name = id, type = type, balance = Money.cents(balance))

    private fun creditCard(id: String = "card", balance: Long) =
        Account(id = id, name = id, type = AccountType.CREDIT_CARD, balance = Money.cents(balance))

    private fun investment(
        id: String = "investment",
        principal: Long = 0,
        currentValue: Long,
    ) = InvestmentAsset(
        id = id,
        name = id,
        type = InvestmentType.FUND,
        principal = Money.cents(principal),
        currentValue = Money.cents(currentValue),
    )

    private fun transaction(
        type: TransactionType,
        amount: Long,
        accountId: String = "asset",
        targetAccountId: String? = null,
        categoryId: String? = null,
        investmentAssetId: String? = null,
        date: LocalDate = LocalDate.of(2026, 7, 7),
    ) = Transaction(
        id = "tx-$type-$amount-$date",
        type = type,
        amount = Money.cents(amount),
        accountId = accountId,
        targetAccountId = targetAccountId,
        categoryId = categoryId,
        investmentAssetId = investmentAssetId,
        date = date,
    )
}
