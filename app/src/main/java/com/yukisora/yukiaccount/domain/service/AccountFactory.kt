package com.yukisora.yukiaccount.domain.service

import com.yukisora.yukiaccount.domain.model.Account
import com.yukisora.yukiaccount.domain.model.AccountType
import com.yukisora.yukiaccount.domain.model.Money

object AccountFactory {
    fun assetAccount(
        id: String,
        name: String,
        type: AccountType,
        balance: Money,
    ): Account =
        Account(
            id = id,
            name = name,
            type = type,
            balance = balance,
        )

    fun creditCard(
        id: String,
        name: String,
        unpaidBalance: Money,
        creditLimit: Money?,
        billingDay: Int?,
        repaymentDay: Int?,
    ): Account =
        Account(
            id = id,
            name = name,
            type = AccountType.CREDIT_CARD,
            balance = unpaidBalance,
            creditLimit = creditLimit,
            billingDay = billingDay,
            repaymentDay = repaymentDay,
        )

    fun archive(account: Account): Account =
        account.copy(isArchived = true)
}
