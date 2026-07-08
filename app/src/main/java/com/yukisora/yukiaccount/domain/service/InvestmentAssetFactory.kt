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
    ): InvestmentAsset =
        InvestmentAsset(
            id = id,
            name = name,
            type = type,
            principal = principal,
            currentValue = currentValue,
            lastValuationDate = valuationDate,
        )

    fun archive(asset: InvestmentAsset): InvestmentAsset =
        asset.copy(isArchived = true)
}
