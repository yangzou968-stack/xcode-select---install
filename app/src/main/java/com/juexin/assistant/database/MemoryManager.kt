package com.juexin.assistant.database

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.juexin.assistant.ReplyResult
import com.juexin.assistant.ReplySource

/**
 * 记忆学习管理器 —— 记录信众画像 + 对话历史，供 LLM 记忆注入
 *
 * 核心逻辑：
 * 1. 每次对话后，把信众消息 + 师父回复存入数据库
 * 2. 更新信众画像（痛点、话题、对话次数）
 * 3. 生成回复前，读取该信众的历史记忆，注入 LLM 上下文
 */
object MemoryManager {

    /**
     * 记录一次对话（信众消息 + 师父回复）
     */
    suspend fun recordConversation(
        context: Context,
        devoteeId: String,
        userMessage: String,
        result: ReplyResult
    ) {
        if (devoteeId.isBlank()) return
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                val chatDao = db.chatLogDao()
                val profileDao = db.devoteeProfileDao()

                // 记录对话
                chatDao.insert(ChatLogEntity(
                    devoteeId = devoteeId,
                    userMessage = userMessage,
                    replyCompassion = result.compassion,
                    replyKarma = result.karma,
                    replyAction = result.action,
                    source = when (result.source) {
                        ReplySource.LLM -> "LLM"
                        ReplySource.REMOTE_SCRIPT -> "REMOTE"
                        ReplySource.LOCAL_FALLBACK -> "LOCAL"
                    }
                ))

                // 更新信众画像
                val existing = profileDao.getById(devoteeId)
                val newCount = (existing?.conversationCount ?: 0) + 1
                profileDao.upsert(
                    DevoteeProfileEntity(
                        devoteeId = devoteeId,
                        name = existing?.name ?: "",
                        gender = existing?.gender ?: "",
                        birthInfo = existing?.birthInfo ?: "",
                        coreProblems = mergeProblems(existing?.coreProblems ?: "", userMessage),
                        conversationCount = newCount,
                        lastTopic = detectTopic(userMessage),
                        summary = existing?.summary ?: "",
                        lastChatTime = System.currentTimeMillis(),
                        createdAt = existing?.createdAt ?: System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) { }
        }
    }

    /**
     * 构建信众记忆上下文，注入 LLM
     */
    suspend fun buildMemoryContext(context: Context, devoteeId: String): String {
        if (devoteeId.isBlank()) return ""
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                val profile = db.devoteeProfileDao().getById(devoteeId)
                val recentLogs = db.chatLogDao().getRecent(devoteeId, 8)

                val sb = StringBuilder()
                // 信众画像
                if (profile != null && profile.conversationCount > 0) {
                    sb.append("【关于这位弟子的记忆】\n")
                    if (profile.name.isNotBlank()) sb.append("- 称呼：${profile.name}\n")
                    if (profile.gender.isNotBlank()) sb.append("- 性别：${profile.gender}\n")
                    sb.append("- 核心痛点：${profile.coreProblems.ifBlank { "未知" }}\n")
                    sb.append("- 最近话题：${profile.lastTopic.ifBlank { "未知" }}\n")
                    sb.append("- 累计对话：${profile.conversationCount} 次\n")
                    sb.append("\n")
                }
                // 最近对话
                if (recentLogs.isNotEmpty()) {
                    sb.append("【这位弟子之前的对话记录】\n")
                    for (log in recentLogs.asReversed()) {
                        sb.append("弟子说：${log.userMessage}\n")
                    }
                    sb.append("\n")
                }
                sb.toString()
            } catch (_: Exception) { "" }
        }
    }

    /**
     * 合并核心痛点（去重，最多保留10个）
     */
    private fun mergeProblems(existing: String, newMessage: String): String {
        val set = existing.split(",").filter { it.isNotBlank() }.toMutableSet()
        // 提取话题关键词
        val topic = detectTopic(newMessage)
        if (topic.isNotBlank() && topic != "通用") {
            set.add(topic)
        }
        return set.take(10).joinToString(",")
    }

    /**
     * 检测消息主题
     */
    private fun detectTopic(message: String): String {
        val m = message.lowercase()
        return when {
            listOf("钱","财","穷","亏","负债","债","赌","生意","投资").any { m.contains(it) } -> "财运"
            listOf("堕胎","流产","打胎","婴灵").any { m.contains(it) } -> "堕胎"
            listOf("婚姻","离婚","出轨","老公","老婆","夫妻","感情","分手","小三").any { m.contains(it) } -> "婚姻"
            listOf("孩子","儿子","女儿","不听话","叛逆","学习").any { m.contains(it) } -> "子女"
            listOf("病","疼","痛","癌","医院","失眠","抑郁","焦虑").any { m.contains(it) } -> "健康"
            listOf("梦","噩梦","鬼","鬼压床","睡不好").any { m.contains(it) } -> "噩梦"
            listOf("去世","过世","托梦","亡","亲人").any { m.contains(it) } -> "亡亲"
            listOf("邪淫","手淫","色情","淫欲").any { m.contains(it) } -> "邪淫"
            listOf("工作","失业","老板","同事","事业").any { m.contains(it) } -> "职场"
            listOf("压力","焦虑","烦","累","迷茫").any { m.contains(it) } -> "压力"
            listOf("运势","倒霉","运气","不顺").any { m.contains(it) } -> "运势"
            else -> "通用"
        }
    }
}
