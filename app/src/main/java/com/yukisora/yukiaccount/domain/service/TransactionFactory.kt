package com.yukisora.yukiaccount.domain.service

import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.Transaction
import com.yukisora.yukiaccount.domain.model.TransactionType
import java.time.LocalDate

object TransactionFactory {
    fun expense(
        id: String,
        amount: Money,
        accountId: String,
        categoryId: String?,
        date: LocalDate,
        note: String,
    ): Transaction =
        Transaction(
            id = id,
            type = TransactionType.EXPENSE,
            amount = amount,
            accountId = accountId,
            categoryId = categoryId,
            date = date,
            note = note,
        )

    fun income(
        id: String,
        amount: Money,
        accountId: String,
        categoryId: String?,
        date: LocalDate,
        note: String,
    ): Transaction =
        Transaction(
            id = id,
            type = TransactionType.INCOME,
            amount = amount,
            accountId = accountId,
            categoryId = categoryId,
            date = date,
            note = note,
        )

    fun refund(
        id: String,
        amount: Money,
        accountId: String,
        categoryId: String?,
        date: LocalDate,
        note: String,
    ): Transaction =
        Transaction(
            id = id,
            type = TransactionType.REFUND,
            amount = amount,
            accountId = accountId,
            categoryId = categoryId,
            date = date,
            note = note,
        )

    fun transfer(
        id: String,
        amount: Money,
        sourceAccountId: String,
        targetAccountId: String,
        categoryId: String?,
        date: LocalDate,
        note: String,
    ): Transaction =
        Transaction(
            id = id,
            type = TransactionType.TRANSFER,
            amount = amount,
            accountId = sourceAccountId,
            targetAccountId = targetAccountId,
            categoryId = categoryId,
            date = date,
            note = note,
        )

    fun creditCardRepayment(
        id: String,
        amount: Money,
        sourceAccountId: String,
        creditCardAccountId: String,
        date: LocalDate,
        note: String,
    ): Transaction =
        Transaction(
            id = id,
            type = TransactionType.CREDIT_CARD_REPAYMENT,
            amount = amount,
            accountId = sourceAccountId,
            targetAccountId = creditCardAccountId,
            date = date,
            note = note,
        )

    fun investmentBuy(
        id: String,
        amount: Money,
        accountId: String,
        investmentAssetId: String,
        categoryId: String?,
        date: LocalDate,
        note: String,
    ): Transaction =
        Transaction(
            id = id,
            type = TransactionType.INVESTMENT_BUY,
            amount = amount,
            accountId = accountId,
            categoryId = categoryId,
            investmentAssetId = investmentAssetId,
            date = date,
            note = note,
        )
}
