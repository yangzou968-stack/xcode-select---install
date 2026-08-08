package com.juexin.assistant.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 话术模板 DAO
 */
@Dao
interface ScriptTemplateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<ScriptTemplateEntity>)

    @Query("SELECT * FROM script_templates WHERE category = :category")
    suspend fun getByCategory(category: String): List<ScriptTemplateEntity>

    @Query("SELECT * FROM script_templates")
    fun observeAll(): Flow<List<ScriptTemplateEntity>>

    @Query("SELECT COUNT(*) FROM script_templates")
    suspend fun count(): Int

    @Query("UPDATE script_templates SET usageCount = usageCount + 1 WHERE id = :id")
    suspend fun incrementUsage(id: Long)

    @Query("UPDATE script_templates SET rating = :rating WHERE id = :id")
    suspend fun updateRating(id: Long, rating: Float)
}

/**
 * 信众档案 DAO
 */
@Dao
interface DevoteeProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: DevoteeProfileEntity)

    @Query("SELECT * FROM devotee_profiles WHERE devoteeId = :devoteeId")
    suspend fun getById(devoteeId: String): DevoteeProfileEntity?

    @Query("SELECT * FROM devotee_profiles")
    suspend fun getAll(): List<DevoteeProfileEntity>
}

/**
 * 对话记录 DAO
 */
@Dao
interface ChatLogDao {
    @Insert
    suspend fun insert(log: ChatLogEntity)

    @Query("SELECT * FROM chat_logs WHERE devoteeId = :devoteeId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(devoteeId: String, limit: Int): List<ChatLogEntity>

    @Query("SELECT * FROM chat_logs WHERE devoteeId = :devoteeId ORDER BY timestamp DESC LIMIT 50")
    suspend fun getHistory(devoteeId: String): List<ChatLogEntity>

    @Query("UPDATE chat_logs SET feedback = :feedback WHERE id = :id")
    suspend fun setFeedback(id: Long, feedback: Int)
}
