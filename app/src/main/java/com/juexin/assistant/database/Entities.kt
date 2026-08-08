package com.juexin.assistant.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 话术模板表 —— 多变体话术库
 */
@Entity(tableName = "script_templates")
data class ScriptTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,          // 场景分类：财运/婚姻/健康/堕胎/子女/噩梦/亡亲/邪淫/职场/通用
    val keywords: String,          // 关键词，逗号分隔
    val compassion: String,        // 悲悯共情
    val karma: String,             // 因果开示
    val action: String,            // 法药指引
    val usageCount: Int = 0,       // 使用次数（用于学习优化）
    val rating: Float = 0f,        // 好评率（学习）
    val source: String = "local"   // 来源：local内置 / remote远程
)

/**
 * 信众档案表 —— 记忆每位弟子的画像
 */
@Entity(tableName = "devotee_profiles")
data class DevoteeProfileEntity(
    @PrimaryKey val devoteeId: String,      // 信众标识（如微信昵称/ID）
    val name: String = "",                  // 称呼
    val gender: String = "",                // 性别
    val birthInfo: String = "",             // 生辰八字（可选）
    val coreProblems: String = "",          // 核心痛点，逗号分隔
    val conversationCount: Int = 0,         // 对话次数
    val lastTopic: String = "",             // 最近话题
    val summary: String = "",               // AI 生成的画像摘要
    val lastChatTime: Long = 0L,            // 最近对话时间
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 对话记录表 —— 每次对话的原文
 */
@Entity(tableName = "chat_logs")
data class ChatLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val devoteeId: String,
    val userMessage: String,       // 信众消息
    val replyCompassion: String,   // 师父三段回复
    val replyKarma: String,
    val replyAction: String,
    val source: String,            // LLM / REMOTE_SCRIPT / LOCAL_FALLBACK
    val feedback: Int = 0,         // 用户反馈：1赞 / 0无 / -1踩
    val timestamp: Long = System.currentTimeMillis()
)
