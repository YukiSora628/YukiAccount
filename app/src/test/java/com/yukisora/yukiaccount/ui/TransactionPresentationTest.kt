package com.yukisora.yukiaccount.ui

import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.Transaction
import com.yukisora.yukiaccount.domain.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionPresentationTest {
    private val accountNames = mapOf(
        "bank" to "银行卡",
        "wallet" to "支付宝",
        "card" to "信用卡",
    )
    private val investmentNames = mapOf("fund" to "基金")

    @Test
    fun singleAccountTransactionsShowAffectedAccount() {
        assertEquals("支出账户 银行卡", summary(TransactionType.EXPENSE))
        assertEquals("收入账户 银行卡", summary(TransactionType.INCOME))
        assertEquals("退款账户 银行卡", summary(TransactionType.REFUND))
    }

    @Test
    fun transfersAndRepaymentsShowSourceAndTargetAccounts() {
        assertEquals(
            "转账 银行卡 → 支付宝",
            summary(TransactionType.TRANSFER, targetAccountId = "wallet"),
        )
        assertEquals(
            "还款 银行卡 → 信用卡",
            summary(TransactionType.CREDIT_CARD_REPAYMENT, targetAccountId = "card"),
        )
    }

    @Test
    fun investmentBuyShowsSourceAccountAndInvestmentAsset() {
        assertEquals(
            "投资 银行卡 → 基金",
            summary(TransactionType.INVESTMENT_BUY, investmentAssetId = "fund"),
        )
    }

    @Test
    fun archivedHistoryCanUseNamesSuppliedByCompleteReferenceLists() {
        val transaction = transaction(TransactionType.EXPENSE, accountId = "archived-bank")

        assertEquals(
            "支出账户 已归档银行卡",
            transactionAccountSummary(
                transaction = transaction,
                accountNames = mapOf("archived-bank" to "已归档银行卡"),
                investmentNames = emptyMap(),
            ),
        )
    }

    private fun summary(
        type: TransactionType,
        targetAccountId: String? = null,
        investmentAssetId: String? = null,
    ): String = transactionAccountSummary(
        transaction = transaction(
            type = type,
            targetAccountId = targetAccountId,
            investmentAssetId = investmentAssetId,
        ),
        accountNames = accountNames,
        investmentNames = investmentNames,
    )

    private fun transaction(
        type: TransactionType,
        accountId: String = "bank",
        targetAccountId: String? = null,
        investmentAssetId: String? = null,
    ) = Transaction(
        id = "transaction",
        type = type,
        amount = Money.cents(100),
        accountId = accountId,
        targetAccountId = targetAccountId,
        investmentAssetId = investmentAssetId,
        date = LocalDate.of(2026, 8, 11),
    )
}
