package com.juexin.assistant.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 知识库实体 —— RAG 检索增强的核心
 *
 * 存储佛学知识、实战话术、案例、经文推荐等，供 AI 按需检索注入
 * 支持学习积累：优质回复自动入库，越用越丰富
 */
@Entity(tableName = "knowledge_base")
data class KnowledgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,           // 分类：佛学义理/因果翻译/经文推荐/法务指引/实战案例/话术技巧/风险提示
    val keywords: String,           // 检索关键词，逗号分隔
    val title: String,              // 标题
    val content: String,            // 知识内容（注入 LLM 的文本）
    val source: String = "seed",    // 来源：seed种子/knowledge佛学文档/learned学习积累
    val usageCount: Int = 0,        // 被检索使用次数
    val rating: Float = 0f,         // 好评率
    val createdAt: Long = System.currentTimeMillis()
)
