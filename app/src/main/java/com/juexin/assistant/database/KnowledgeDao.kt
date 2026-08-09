package com.juexin.assistant.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 知识库 DAO —— RAG 检索 + 学习积累
 */
@Dao
interface KnowledgeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<KnowledgeEntity>)

    @Insert
    suspend fun insert(item: KnowledgeEntity): Long

    /**
     * 按关键词模糊检索（核心检索方法）
     * 查找 keywords 字段包含任一关键词的记录
     */
    @Query("SELECT * FROM knowledge_base WHERE keywords LIKE '%' || :keyword || '%' OR title LIKE '%' || :keyword || '%' OR content LIKE '%' || :keyword || '%' ORDER BY usageCount DESC LIMIT :limit")
    suspend fun searchByKeyword(keyword: String, limit: Int = 5): List<KnowledgeEntity>

    /**
     * 按分类检索
     */
    @Query("SELECT * FROM knowledge_base WHERE category = :category")
    suspend fun getByCategory(category: String): List<KnowledgeEntity>

    /**
     * 获取全部知识（供全文检索）
     */
    @Query("SELECT * FROM knowledge_base")
    suspend fun getAll(): List<KnowledgeEntity>

    @Query("SELECT COUNT(*) FROM knowledge_base")
    suspend fun count(): Int

    @Query("UPDATE knowledge_base SET usageCount = usageCount + 1 WHERE id = :id")
    suspend fun incrementUsage(id: Long)

    @Query("UPDATE knowledge_base SET rating = :rating WHERE id = :id")
    suspend fun updateRating(id: Long, rating: Float)

    /**
     * 获取学习积累的知识（source=learned）
     */
    @Query("SELECT * FROM knowledge_base WHERE source = 'learned' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getLearned(limit: Int = 50): List<KnowledgeEntity>
}
