package com.yukisora.yukiaccount.domain.service

import com.yukisora.yukiaccount.domain.model.InvestmentType
import com.yukisora.yukiaccount.domain.model.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class InvestmentAssetFactoryTest {
    @Test
    fun investmentAssetKeepsPrincipalAndCurrentValue() {
        val asset = InvestmentAssetFactory.asset(
            id = "wealth",
            name = "银行理财",
            type = InvestmentType.WEALTH_MANAGEMENT,
            principal = Money.cents(50_000),
            currentValue = Money.cents(51_200),
            valuationDate = LocalDate.of(2026, 7, 8),
        )

        assertEquals("wealth", asset.id)
        assertEquals("银行理财", asset.name)
        assertEquals(InvestmentType.WEALTH_MANAGEMENT, asset.type)
        assertEquals(Money.cents(50_000), asset.principal)
        assertEquals(Money.cents(51_200), asset.currentValue)
        assertEquals(LocalDate.of(2026, 7, 8), asset.lastValuationDate)
        assertFalse(asset.isArchived)
    }
}
