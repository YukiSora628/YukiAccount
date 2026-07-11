package com.yukisora.yukiaccount.ui

import com.yukisora.yukiaccount.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyInputTest {
    @Test
    fun transactionAmountMustBePositive() {
        assertEquals(Money.cents(123), "1.23".toMoneyOrNull())
        assertNull("0".toMoneyOrNull())
        assertNull("-1".toMoneyOrNull())
    }

    @Test
    fun nonNegativeValueAllowsZero() {
        assertEquals(Money.ZERO, "0".toNonNegativeMoneyOrNull())
        assertEquals(Money.cents(123), "1.23".toNonNegativeMoneyOrNull())
        assertNull("-0.01".toNonNegativeMoneyOrNull())
    }

    @Test
    fun invalidAndOverflowingValuesAreRejected() {
        assertNull("not-a-number".toMoneyOrNull())
        assertNull("999999999999999999999999999999999999".toMoneyOrNull())
        assertNull("999999999999999999999999999999999999".toNonNegativeMoneyOrNull())
    }
}
