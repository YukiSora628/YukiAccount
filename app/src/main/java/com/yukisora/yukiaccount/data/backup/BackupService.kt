package com.yukisora.yukiaccount.data.backup

import com.yukisora.yukiaccount.domain.model.AccountType
import com.yukisora.yukiaccount.domain.model.InvestmentType
import com.yukisora.yukiaccount.domain.model.RecurringFrequency
import com.yukisora.yukiaccount.domain.model.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupService(
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
        encodeDefaults = true
    },
) {
    fun export(document: BackupDocument): String =
        json.encodeToString(document.copy(schemaVersion = CURRENT_BACKUP_SCHEMA_VERSION))

    fun parseForImport(rawJson: String): BackupImportResult {
        val document = try {
            json.decodeFromString<BackupDocument>(rawJson)
        } catch (error: SerializationException) {
            return BackupImportResult.Invalid("备份文件格式无效")
        } catch (error: IllegalArgumentException) {
            return BackupImportResult.Invalid("备份文件内容无效")
        }

        if (document.schemaVersion != CURRENT_BACKUP_SCHEMA_VERSION) {
            return BackupImportResult.Invalid("不支持的备份版本：${document.schemaVersion}")
        }

        document.enumValueReason()?.let { reason ->
            return BackupImportResult.Invalid(reason)
        }

        document.basicFieldReason()?.let { reason ->
            return BackupImportResult.Invalid(reason)
        }

        document.duplicateIdReason()?.let { reason ->
            return BackupImportResult.Invalid(reason)
        }

        document.referenceIntegrityReason()?.let { reason ->
            return BackupImportResult.Invalid(reason)
        }

        document.semanticIntegrityReason()?.let { reason ->
            return BackupImportResult.Invalid(reason)
        }

        return BackupImportResult.Valid(document)
    }

    private fun BackupDocument.enumValueReason(): String? {
        if (accounts.any { it.type !in AccountType.entries.names() }) {
            return "备份文件包含无效账户类型"
        }
        if (categories.any { it.type !in CATEGORY_TYPES }) {
            return "备份文件包含无效分类类型"
        }
        if (transactions.any { it.type !in TransactionType.entries.names() }) {
            return "备份文件包含无效流水类型"
        }
        if (recurringRules.any { it.transactionType !in TransactionType.entries.names() }) {
            return "备份文件包含无效周期规则流水类型"
        }
        if (recurringRules.any { it.frequency !in RecurringFrequency.entries.names() }) {
            return "备份文件包含无效周期频率"
        }
        if (investmentAssets.any { it.type !in InvestmentType.entries.names() }) {
            return "备份文件包含无效投资类型"
        }
        return null
    }

    private fun <T : Enum<T>> List<T>.names(): Set<String> = map { it.name }.toSet()

    private fun BackupDocument.basicFieldReason(): String? {
        listOf(
            "账户" to accounts.map { it.id },
            "分类" to categories.map { it.id },
            "流水" to transactions.map { it.id },
            "周期规则" to recurringRules.map { it.id },
            "投资资产" to investmentAssets.map { it.id },
            "市值快照" to valuationSnapshots.map { it.id },
            "跳过周期记录" to skippedOccurrences.map { it.id },
        ).firstOrNull { (_, ids) -> ids.any(String::isBlank) }
            ?.let { (label, _) -> return "备份文件包含空${label} ID" }

        listOf(
            "账户" to accounts.map { it.name },
            "分类" to categories.map { it.name },
            "周期规则" to recurringRules.map { it.name },
            "投资资产" to investmentAssets.map { it.name },
        ).firstOrNull { (_, names) -> names.any(String::isBlank) }
            ?.let { (label, _) -> return "备份文件包含空${label}名称" }

        if (transactions.any { it.amountCents <= 0 }) {
            return "备份文件包含非正数流水金额"
        }
        if (recurringRules.any { it.amountCents <= 0 }) {
            return "备份文件包含非正数周期规则金额"
        }
        if (investmentAssets.any { it.principalCents < 0 }) {
            return "备份文件包含负数投资本金"
        }
        if (investmentAssets.any { it.currentValueCents < 0 }) {
            return "备份文件包含负数投资市值"
        }
        if (valuationSnapshots.any { it.valueCents < 0 }) {
            return "备份文件包含负数市值快照金额"
        }
        if (accounts.any { it.creditLimitCents != null && it.creditLimitCents < 0 }) {
            return "备份文件包含负数信用额度"
        }
        if (accounts.any { it.billingDay != null && it.billingDay !in 1..31 }) {
            return "备份文件包含无效信用卡账单日"
        }
        if (accounts.any { it.repaymentDay != null && it.repaymentDay !in 1..31 }) {
            return "备份文件包含无效信用卡还款日"
        }

        if (allDateValues().any { it.toLocalDateOrNull() == null }) {
            return "备份文件包含无效日期"
        }
        if (recurringRules.any { rule ->
                val startDate = LocalDate.parse(rule.startDate)
                rule.endDate?.let(LocalDate::parse)?.isBefore(startDate) == true
            }
        ) {
            return "备份文件包含结束日早于开始日的周期规则"
        }
        if (recurringRules.any { rule ->
                LocalDate.parse(rule.nextOccurrenceDate).isBefore(LocalDate.parse(rule.startDate))
            }
        ) {
            return "备份文件包含下次执行日早于开始日的周期规则"
        }
        if (transactions.any { transaction ->
                transaction.autoGenerated &&
                    (transaction.recurringRuleId.isNullOrBlank() || transaction.occurrenceDate.isNullOrBlank())
            }
        ) {
            return "备份文件包含来源信息不完整的自动流水"
        }

        return null
    }

    private fun BackupDocument.allDateValues(): List<String> =
        buildList {
            transactions.forEach { transaction ->
                add(transaction.date)
                transaction.occurrenceDate?.let(::add)
            }
            recurringRules.forEach { rule ->
                add(rule.startDate)
                rule.endDate?.let(::add)
                add(rule.nextOccurrenceDate)
            }
            investmentAssets.forEach { it.lastValuationDate?.let(::add) }
            valuationSnapshots.forEach { add(it.date) }
            skippedOccurrences.forEach { add(it.occurrenceDate) }
        }

    private fun String.toLocalDateOrNull(): LocalDate? =
        try {
            LocalDate.parse(this)
        } catch (error: DateTimeParseException) {
            null
        }

    private fun BackupDocument.duplicateIdReason(): String? =
        listOf(
            "账户" to accounts.map { it.id },
            "分类" to categories.map { it.id },
            "流水" to transactions.map { it.id },
            "周期规则" to recurringRules.map { it.id },
            "投资资产" to investmentAssets.map { it.id },
            "市值快照" to valuationSnapshots.map { it.id },
            "跳过周期记录" to skippedOccurrences.map { it.id },
        ).firstOrNull { (_, ids) -> ids.hasDuplicate() }
            ?.let { (label, _) -> "备份文件包含重复${label} ID" }

    private fun List<String>.hasDuplicate(): Boolean =
        size != toSet().size

    private fun BackupDocument.referenceIntegrityReason(): String? {
        val accountIds = accounts.map { it.id }.toSet()
        val categoryIds = categories.map { it.id }.toSet()
        val investmentAssetIds = investmentAssets.map { it.id }.toSet()
        val recurringRuleIds = recurringRules.map { it.id }.toSet()

        if (transactions.any { it.accountId !in accountIds }) {
            return "备份文件包含不存在的流水账户 ID"
        }
        if (transactions.any { it.targetAccountId != null && it.targetAccountId !in accountIds }) {
            return "备份文件包含不存在的流水目标账户 ID"
        }
        if (transactions.any { it.categoryId != null && it.categoryId !in categoryIds }) {
            return "备份文件包含不存在的流水分类 ID"
        }
        if (transactions.any { it.investmentAssetId != null && it.investmentAssetId !in investmentAssetIds }) {
            return "备份文件包含不存在的流水投资资产 ID"
        }
        if (transactions.any { it.recurringRuleId != null && it.recurringRuleId !in recurringRuleIds }) {
            return "备份文件包含不存在的流水周期规则 ID"
        }
        if (recurringRules.any { it.accountId !in accountIds }) {
            return "备份文件包含不存在的周期规则账户 ID"
        }
        if (recurringRules.any { it.targetAccountId != null && it.targetAccountId !in accountIds }) {
            return "备份文件包含不存在的周期规则目标账户 ID"
        }
        if (recurringRules.any { it.categoryId != null && it.categoryId !in categoryIds }) {
            return "备份文件包含不存在的周期规则分类 ID"
        }
        if (recurringRules.any {
                it.investmentAssetId != null && it.investmentAssetId !in investmentAssetIds
            }
        ) {
            return "备份文件包含不存在的周期规则投资资产 ID"
        }
        if (valuationSnapshots.any { it.investmentAssetId !in investmentAssetIds }) {
            return "备份文件包含不存在的市值快照投资资产 ID"
        }
        if (skippedOccurrences.any { it.recurringRuleId !in recurringRuleIds }) {
            return "备份文件包含不存在的跳过周期规则 ID"
        }

        return null
    }

    private fun BackupDocument.semanticIntegrityReason(): String? {
        val accountTypes = accounts.associate { it.id to AccountType.valueOf(it.type) }

        transactions.forEach { transaction ->
            val sourceType = accountTypes.getValue(transaction.accountId)
            when (TransactionType.valueOf(transaction.type)) {
                TransactionType.EXPENSE,
                TransactionType.REFUND -> Unit
                TransactionType.INCOME -> if (sourceType == AccountType.CREDIT_CARD) {
                    return "备份文件包含记入信用卡的收入流水"
                }
                TransactionType.TRANSFER -> {
                    val targetAccountId = transaction.targetAccountId
                        ?: return "备份文件包含缺少目标账户的转账流水"
                    if (targetAccountId == transaction.accountId) {
                        return "备份文件包含源账户与目标账户相同的转账流水"
                    }
                    if (sourceType == AccountType.CREDIT_CARD ||
                        accountTypes.getValue(targetAccountId) == AccountType.CREDIT_CARD
                    ) {
                        return "备份文件包含使用信用卡的普通转账流水"
                    }
                }
                TransactionType.CREDIT_CARD_REPAYMENT -> {
                    val targetAccountId = transaction.targetAccountId
                        ?: return "备份文件包含缺少目标账户的信用卡还款流水"
                    if (sourceType == AccountType.CREDIT_CARD) {
                        return "备份文件包含来源是信用卡的还款流水"
                    }
                    if (accountTypes.getValue(targetAccountId) != AccountType.CREDIT_CARD) {
                        return "备份文件包含目标不是信用卡的还款流水"
                    }
                }
                TransactionType.INVESTMENT_BUY -> {
                    if (transaction.investmentAssetId == null) {
                        return "备份文件包含缺少投资资产的投资买入流水"
                    }
                    if (sourceType == AccountType.CREDIT_CARD) {
                        return "备份文件包含使用信用卡的投资买入流水"
                    }
                }
            }

            if (!transaction.autoGenerated &&
                (transaction.recurringRuleId != null || transaction.occurrenceDate != null)
            ) {
                return "备份文件包含来源标记与自动状态不一致的流水"
            }
            if (transaction.autoGenerated && transaction.occurrenceDate != transaction.date) {
                return "备份文件包含发生日期不一致的自动流水"
            }
        }

        recurringRules.forEach { rule ->
            if (accountTypes.getValue(rule.accountId) == AccountType.CREDIT_CARD) {
                return "备份文件包含使用信用卡的周期规则"
            }
            when (TransactionType.valueOf(rule.transactionType)) {
                TransactionType.EXPENSE -> Unit
                TransactionType.INVESTMENT_BUY -> if (rule.investmentAssetId == null) {
                    return "备份文件包含缺少投资资产的定投规则"
                }
                else -> return "备份文件包含不支持的周期规则流水类型"
            }
        }

        return null
    }

    private companion object {
        val CATEGORY_TYPES = setOf("expense", "income", "investment")
    }
}

sealed interface BackupImportResult {
    data class Valid(val document: BackupDocument) : BackupImportResult
    data class Invalid(val reason: String) : BackupImportResult
}
