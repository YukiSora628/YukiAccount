package com.yukisora.yukiaccount.domain.service

import com.yukisora.yukiaccount.domain.model.AccountType
import com.yukisora.yukiaccount.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AccountFactoryTest {
    @Test
    fun assetAccountStartsWithProvidedBalance() {
        val account = AccountFactory.assetAccount(
            id = "alipay",
            name = "支付宝",
            type = AccountType.ALIPAY,
            balance = Money.cents(12_300),
        )

        assertEquals("alipay", account.id)
        assertEquals("支付宝", account.name)
        assertEquals(AccountType.ALIPAY, account.type)
        assertEquals(Money.cents(12_300), account.balance)
        assertEquals(null, account.creditLimit)
        assertEquals(null, account.billingDay)
        assertEquals(null, account.repaymentDay)
        assertFalse(account.isArchived)
    }

    @Test
    fun creditCardAccountKeepsBillingAndRepaymentDays() {
        val account = AccountFactory.creditCard(
            id = "cmb-card",
            name = "招行信用卡",
            unpaidBalance = Money.cents(2_000),
            creditLimit = Money.cents(50_000),
            billingDay = 8,
            repaymentDay = 28,
        )

        assertEquals(AccountType.CREDIT_CARD, account.type)
        assertEquals(Money.cents(2_000), account.balance)
        assertEquals(Money.cents(50_000), account.creditLimit)
        assertEquals(8, account.billingDay)
        assertEquals(28, account.repaymentDay)
    }

    @Test
    fun archiveKeepsAccountValuesAndMarksArchived() {
        val account = AccountFactory.creditCard(
            id = "cmb-card",
            name = "card",
            unpaidBalance = Money.cents(2_000),
            creditLimit = Money.cents(50_000),
            billingDay = 8,
            repaymentDay = 28,
        )

        val archived = AccountFactory.archive(account)

        assertEquals(account.id, archived.id)
        assertEquals(account.name, archived.name)
        assertEquals(account.type, archived.type)
        assertEquals(account.balance, archived.balance)
        assertEquals(account.creditLimit, archived.creditLimit)
        assertEquals(account.billingDay, archived.billingDay)
        assertEquals(account.repaymentDay, archived.repaymentDay)
        assertEquals(true, archived.isArchived)
    }
}
