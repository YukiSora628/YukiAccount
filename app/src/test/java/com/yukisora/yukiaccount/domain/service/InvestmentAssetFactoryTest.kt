package com.yukisora.yukiaccount.domain.service

import com.yukisora.yukiaccount.domain.model.InvestmentType
import com.yukisora.yukiaccount.domain.model.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun archiveKeepsInvestmentValuesAndMarksArchived() {
        val asset = InvestmentAssetFactory.asset(
            id = "wealth",
            name = "wealth",
            type = InvestmentType.WEALTH_MANAGEMENT,
            principal = Money.cents(50_000),
            currentValue = Money.cents(51_200),
            valuationDate = LocalDate.of(2026, 7, 8),
        )

        val archived = InvestmentAssetFactory.archive(asset)

        assertEquals(asset.id, archived.id)
        assertEquals(asset.name, archived.name)
        assertEquals(asset.type, archived.type)
        assertEquals(asset.principal, archived.principal)
        assertEquals(asset.currentValue, archived.currentValue)
        assertEquals(asset.lastValuationDate, archived.lastValuationDate)
        assertEquals(true, archived.isArchived)
    }

    @Test
    fun updateValuationKeepsPrincipalAndUpdatesCurrentValueAndDate() {
        val asset = InvestmentAssetFactory.asset(
            id = "fund",
            name = "基金",
            type = InvestmentType.FUND,
            principal = Money.cents(30_000),
            currentValue = Money.cents(31_000),
            valuationDate = LocalDate.of(2026, 7, 8),
        )

        val updated = InvestmentAssetFactory.updateValuation(
            asset = asset,
            currentValue = Money.cents(32_500),
            valuationDate = LocalDate.of(2026, 7, 10),
        )

        assertEquals(asset.id, updated.id)
        assertEquals(asset.name, updated.name)
        assertEquals(asset.type, updated.type)
        assertEquals(Money.cents(30_000), updated.principal)
        assertEquals(Money.cents(32_500), updated.currentValue)
        assertEquals(LocalDate.of(2026, 7, 10), updated.lastValuationDate)
        assertEquals(asset.isArchived, updated.isArchived)
    }

    @Test
    fun updateValuationAllowsZeroButRejectsNegativeValue() {
        val asset = InvestmentAssetFactory.asset(
            id = "fund",
            name = "基金",
            type = InvestmentType.FUND,
            principal = Money.cents(30_000),
            currentValue = Money.cents(31_000),
            valuationDate = LocalDate.of(2026, 7, 8),
        )

        val updated = InvestmentAssetFactory.updateValuation(
            asset = asset,
            currentValue = Money.ZERO,
            valuationDate = LocalDate.of(2026, 7, 10),
        )

        assertEquals(Money.ZERO, updated.currentValue)
        assertFailsWith<IllegalArgumentException> {
            InvestmentAssetFactory.updateValuation(
                asset = asset,
                currentValue = Money.cents(-1),
                valuationDate = LocalDate.of(2026, 7, 10),
            )
        }
    }

    @Test
    fun updateValuationRejectsDateBeforeLatestValuation() {
        val asset = InvestmentAssetFactory.asset(
            id = "fund",
            name = "基金",
            type = InvestmentType.FUND,
            principal = Money.cents(30_000),
            currentValue = Money.cents(31_000),
            valuationDate = LocalDate.of(2026, 7, 10),
        )

        assertFailsWith<IllegalArgumentException> {
            InvestmentAssetFactory.updateValuation(
                asset = asset,
                currentValue = Money.cents(30_500),
                valuationDate = LocalDate.of(2026, 7, 9),
            )
        }
    }
}
