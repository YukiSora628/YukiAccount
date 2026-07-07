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

        val duplicateIds = document.transactions
            .groupingBy { it.id }
            .eachCount()
            .filterValues { it > 1 }
            .keys

        if (duplicateIds.isNotEmpty()) {
            return BackupImportResult.Invalid("备份文件包含重复流水 ID")
        }

        return BackupImportResult.Valid(document)
    }
}

sealed interface BackupImportResult {
    data class Valid(val document: BackupDocument) : BackupImportResult
    data class Invalid(val reason: String) : BackupImportResult
}
