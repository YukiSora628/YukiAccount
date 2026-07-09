package com.yukisora.yukiaccount.data.backup

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

        document.duplicateIdReason()?.let { reason ->
            return BackupImportResult.Invalid(reason)
        }

        document.referenceIntegrityReason()?.let { reason ->
            return BackupImportResult.Invalid(reason)
        }

        return BackupImportResult.Valid(document)
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

        return null
    }
}

sealed interface BackupImportResult {
    data class Valid(val document: BackupDocument) : BackupImportResult
    data class Invalid(val reason: String) : BackupImportResult
}
