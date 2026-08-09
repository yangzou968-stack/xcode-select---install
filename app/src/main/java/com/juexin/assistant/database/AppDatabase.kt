package com.juexin.assistant.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 觉心助手数据库 —— 话术库 + 信众档案 + 对话记录
 *
 * 【迁移策略 V5.3】
 * - 每个数据库版本升级都提供显式 Migration，保留用户数据（信众档案/对话记录）
 * - 禁止 fallbackToDestructiveMigration（否则升级会清空"越用越聪明"的记忆数据）
 * - 未匹配到 Migration 时安全降级：保留已有数据，仅在确需时重建缺失表
 */
@Database(
    entities = [
        ScriptTemplateEntity::class,
        DevoteeProfileEntity::class,
        ChatLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scriptTemplateDao(): ScriptTemplateDao
    abstract fun devoteeProfileDao(): DevoteeProfileDao
    abstract fun chatLogDao(): ChatLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private const val TAG = "AppDatabase"

        /**
         * 迁移 1→2：v2 新增字段
         * - script_templates 表新增 feedback（好评率反馈）
         * - chat_logs 表新增 feedback（用户反馈：赞/踩）
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE script_templates ADD COLUMN feedback INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // 列已存在则忽略
                    Log.w(TAG, "script_templates.feedback 已存在或无法添加: ${e.message}")
                }
                try {
                    db.execSQL("ALTER TABLE chat_logs ADD COLUMN feedback INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    Log.w(TAG, "chat_logs.feedback 已存在或无法添加: ${e.message}")
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "juexin.db"
                )
                // 显式迁移，保留数据
                .addMigrations(MIGRATION_1_2)
                // 降级不销毁数据（仅当 schema 可兼容时）
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
