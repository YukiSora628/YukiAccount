package com.yukisora.yukiaccount.domain.service

import com.yukisora.yukiaccount.domain.model.InvestmentAsset
import com.yukisora.yukiaccount.domain.model.InvestmentType
import com.yukisora.yukiaccount.domain.model.Money
import java.time.LocalDate

object InvestmentAssetFactory {
    fun asset(
        id: String,
        name: String,
        type: InvestmentType,
        principal: Money,
        currentValue: Money,
        valuationDate: LocalDate?,
    ): InvestmentAsset {
        require(principal >= Money.ZERO) { "Investment principal must not be negative" }
        require(currentValue >= Money.ZERO) { "Investment current value must not be negative" }
        return InvestmentAsset(
            id = id,
            name = name,
            type = type,
            principal = principal,
            currentValue = currentValue,
            lastValuationDate = valuationDate,
        )
    }

    fun archive(asset: InvestmentAsset): InvestmentAsset =
        asset.copy(isArchived = true)

    fun updateValuation(
        asset: InvestmentAsset,
        currentValue: Money,
        valuationDate: LocalDate,
    ): InvestmentAsset {
        require(currentValue >= Money.ZERO) { "Investment current value must not be negative" }
        require(asset.lastValuationDate?.let { !valuationDate.isBefore(it) } != false) {
            "Valuation date must not be before the latest valuation date"
        }
        return asset.copy(
            currentValue = currentValue,
            lastValuationDate = valuationDate,
        )
    }
}
