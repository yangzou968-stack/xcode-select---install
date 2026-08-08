package com.juexin.assistant.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 觉心助手数据库 —— 话术库 + 信众档案 + 对话记录
 */
@Database(
    entities = [
        ScriptTemplateEntity::class,
        DevoteeProfileEntity::class,
        ChatLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scriptTemplateDao(): ScriptTemplateDao
    abstract fun devoteeProfileDao(): DevoteeProfileDao
    abstract fun chatLogDao(): ChatLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "juexin.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
