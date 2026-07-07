package com.yukisora.yukiaccount.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yukisora.yukiaccount.data.model.AccountEntity
import com.yukisora.yukiaccount.data.model.CategoryEntity
import com.yukisora.yukiaccount.data.model.InvestmentAssetEntity
import com.yukisora.yukiaccount.data.model.RecurringRuleEntity
import com.yukisora.yukiaccount.data.model.SkippedOccurrenceEntity
import com.yukisora.yukiaccount.data.model.TransactionEntity
import com.yukisora.yukiaccount.data.model.ValuationSnapshotEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        RecurringRuleEntity::class,
        InvestmentAssetEntity::class,
        ValuationSnapshotEntity::class,
        SkippedOccurrenceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class YukiAccountDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun investmentDao(): InvestmentDao

    companion object {
        @Volatile
        private var instance: YukiAccountDatabase? = null

        fun getInstance(context: Context): YukiAccountDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    YukiAccountDatabase::class.java,
                    "yuki-account.db",
                ).build().also { instance = it }
            }
    }
}
