package com.yukisora.yukiaccount.domain.service

import com.yukisora.yukiaccount.domain.model.Money
import com.yukisora.yukiaccount.domain.model.Transaction
import com.yukisora.yukiaccount.domain.model.TransactionType
import java.time.LocalDate

object TransactionFactory {
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
}
