package com.juexin.assistant.database

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 知识检索器 —— RAG 核心
 *
 * 功能：
 * 1. 根据信众消息检索最相关的知识片段
 * 2. 关键词匹配 + TF-IDF 相似度排序
 * 3. 返回 Top-N 知识片段供 LLM 注入
 */
object KnowledgeRetriever {

    /** 检索 Top-N 知识片段 */
    suspend fun retrieve(context: Context, userMessage: String, topN: Int = 3): List<KnowledgeEntity> {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                val dao = db.knowledgeDao()

                // 如果知识库为空，先播种
                if (dao.count() == 0) {
                    KnowledgeSeeder.seed(context)
                }

                // 提取关键词
                val keywords = extractKeywords(userMessage)
                val results = mutableListOf<KnowledgeEntity>()
                val seen = mutableSetOf<Long>()

                // 1. 关键词精确匹配检索
                for (kw in keywords) {
                    if (kw.length < 2) continue
                    val matches = dao.searchByKeyword(kw, topN)
                    for (m in matches) {
                        if (seen.add(m.id)) {
                            results.add(m)
                            dao.incrementUsage(m.id)
                        }
                        if (results.size >= topN) break
                    }
                    if (results.size >= topN) break
                }

                // 2. 如果关键词检索不足，补充全库 Top
                if (results.size < topN) {
                    val all = dao.getAll()
                    val scored = all.map { entity ->
                        entity to computeSimilarity(userMessage, entity)
                    }.sortedByDescending { it.second }
                    for ((entity, _) in scored) {
                        if (seen.add(entity.id)) {
                            results.add(entity)
                        }
                        if (results.size >= topN) break
                    }
                }

                results
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    /**
     * 构建检索到的知识上下文文本（注入 LLM）
     */
    fun buildKnowledgeContext(items: List<KnowledgeEntity>): String {
        if (items.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("【师父知识库检索结果——以下是最相关的知识片段，请参考融入开示】\n\n")
        for ((index, item) in items.withIndex()) {
            sb.append("${index + 1}. [${item.category}] ${item.title}\n")
            sb.append("   ${item.content}\n\n")
        }
        return sb.toString()
    }

    /**
     * 从用户消息提取关键词
     */
    private fun extractKeywords(message: String): List<String> {
        val m = message.lowercase()
        // 场景关键词映射
        val sceneKeywords = mapOf(
            // 财运
            "钱" to "财运", "财" to "财运", "穷" to "财运", "亏" to "财运", "负债" to "财运",
            "债" to "财运", "赌" to "财运", "生意" to "财运", "投资" to "财运", "收入" to "财运",
            // 堕胎
            "堕胎" to "堕胎", "流产" to "堕胎", "婴灵" to "堕胎", "打胎" to "堕胎",
            // 婚姻
            "婚姻" to "婚姻", "离婚" to "婚姻", "出轨" to "婚姻", "老公" to "婚姻",
            "老婆" to "婚姻", "夫妻" to "婚姻", "感情" to "婚姻", "小三" to "婚姻",
            // 子女
            "孩子" to "子女", "儿子" to "子女", "女儿" to "子女", "叛逆" to "子女", "学习" to "子女",
            // 健康
            "病" to "健康", "疼" to "健康", "痛" to "健康", "癌" to "健康", "失眠" to "健康",
            "抑郁" to "健康", "焦虑" to "健康", "手术" to "健康",
            // 噩梦
            "梦" to "噩梦", "鬼" to "噩梦", "鬼压床" to "噩梦", "惊醒" to "噩梦",
            // 亡亲
            "去世" to "亡亲", "过世" to "亡亲", "托梦" to "亡亲", "亡" to "亡亲", "死" to "亡亲",
            // 邪淫
            "邪淫" to "邪淫", "手淫" to "邪淫", "色情" to "邪淫", "欲" to "邪淫",
            // 职场
            "工作" to "职场", "失业" to "职场", "老板" to "职场", "事业" to "职场",
            // 压力
            "压力" to "压力", "烦" to "压力", "累" to "压力", "迷茫" to "压力"
        )

        val keywords = mutableListOf<String>()
        for ((kw, category) in sceneKeywords) {
            if (m.contains(kw)) {
                keywords.add(kw)
                keywords.add(category)
            }
        }
        // 加上消息本身的长词（>=2字的连续中文）
        val chineseWords = Regex("[\\u4e00-\\u9fa5]{2,}").findAll(m).map { it.value }.toList()
        keywords.addAll(chineseWords.filter { it.length in 2..6 })

        return keywords.distinct().take(10)
    }

    /**
     * 计算消息与知识条目的相似度（简化的 TF 匹配）
     */
    private fun computeSimilarity(message: String, entity: KnowledgeEntity): Float {
        val m = message.lowercase()
        var score = 0f
        // 关键词匹配得分
        for (kw in entity.keywords.split(",")) {
            if (kw.isNotBlank() && m.contains(kw.trim().lowercase())) {
                score += 1f
            }
        }
        // 标题匹配得分
        if (entity.title.any { m.contains(it.toString(), ignoreCase = true) }) {
            score += 0.5f
        }
        return score
    }

    /**
     * 学习积累：把优质回复存入知识库
     */
    suspend fun learnFromConversation(
        context: Context,
        category: String,
        keywords: String,
        title: String,
        content: String
    ): Long {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                db.knowledgeDao().insert(KnowledgeEntity(
                    category = category,
                    keywords = keywords,
                    title = title,
                    content = content,
                    source = "learned"
                ))
            } catch (_: Exception) { -1L }
        }
    }
}
