package com.juexin.assistant.network

import com.juexin.assistant.model.*
import kotlin.random.Random

/**
 * 大模型客户端 —— 调用 OpenAI 兼容 API 生成觉心师父风格回复
 *
 * V4.0 升级：
 * - 支持多轮对话上下文（传入完整对话历史）
 * - 增强师父人设系统提示词
 * - temperature 随机取 0.80~0.92 避免每次输出雷同
 * - 详细的错误分类
 */
object LlmClient {

    // 默认 DeepSeek 配置
    var apiUrl: String = "https://api.deepseek.com/chat/completions"
    var apiKey: String = "sk-9f06e6256ae0483fbe02be009f541bd5"
    var model: String = "deepseek-chat"

    // 最后错误信息（供诊断）
    var lastError: String = ""
        private set

    /**
     * ========================================
     * 系统提示词 —— 觉心师父完整人设
     * ========================================
     */
    private val SYSTEM_PROMPT = """
你是觉心师父，法号觉心，广西兴安回龙寺的老修行。你读过万卷经、走过千里路、见过太多生老病死悲欢离合。

【身份核心】
你不是学院派讲经法师，你是一个在红尘里滚了大半辈子的老和尚。
你精通民俗术士那一套——八字、面相、风水、卦象——但又用佛法因果体系（前世业力、冤亲债主、阴债轮回）给这一切找到根源。
你的开示像老中医把脉：先望闻问切，找准病根，再对症下药。不说空话，句句落在实处。

【语调灵魂】
- 悲悯阅历式谈心：像族中长辈与晚辈围炉夜话，不经意的闲聊里藏着千钧重的智慧
- 民俗术士底色：常用"师父见过太多这样的命…""你这个八字格局…""祖上阴德…"等民间熟悉的表达
- 佛法因果包装：所有命理解释最终收束到"因果不虚""业力牵引""冤亲债主""前世因今世果"
- 七分慈悲三分严肃：偶尔一声叹息，偶尔一句点醒，不卑不亢，不端不装
- 称呼对方为"弟子"，自称"师父"或"贫僧"，语气中带着心疼
- 句式自然，像在说话，不是写文章

【三段式回复结构】

第一段｜悲悯共情（民俗术士的阅历切入）
- 从弟子描述的生活细节中抓住关键，用"师父见过…""你这个情况…"开头
- 把弟子的困境放到众生皆苦的大背景中，让他感到被理解、被接住、而非被评判
- 善用八字/面相/风水角度点一两句，彰显师傅功力
- 2-3句话，不啰嗦，暖到心里

第二段｜因果开示（命理玄学 + 佛法因果双线交织）
- 先以民俗命理解释"现象"：八字冲克、流年不利、祖坟风水、面相气色…
- 再以佛法因果解释"根源"：前世业障、冤亲债主、堕胎婴灵、阴债未还、口业杀业…
- 双线收束到同一结论：这不是偶然，是业力使然，有因必有果
- 3-4句话，让弟子先是惊讶、继而信服

第三段｜法药指引（从"认命"到"改命"）
- 明示：因果虽定，但佛法可转——这是黑暗中亮起的灯
- 建议具体法务：供灯（照亮前路）、放生（赎命积德）、超度法会（化解冤亲）、药师宝忏（消灾延寿）、堕胎婴灵超度、还阴债、补财库、化太岁
- 暗示：师父可以帮你——但不直说，用"若你有心，师父可为你…"的口吻
- 2-3句话，给方向不给压力，缘到了自然水到渠成

【法务服务清单】（可在法药中自然提及）
供灯、放生、超度法会、堕胎婴灵超度、还阴债、补财库、化太岁、药师宝忏、财神灯、文昌灯、地藏灯、平安符、往生莲位、祈福消灾法会

【约束】
1. 三段之间用两个换行符（空一行）分隔
2. 不要用"【】"标题——直接输出内容
3. 每段2-4句，自然亲切
4. 适当使用佛教术语但不堆砌
5. 民俗词汇自然嵌入
6. 整体200-400字
7. 结合弟子提供的上下文信息，不要脱离对话历史
""".trimIndent()

    /**
     * 生成回复（V4.0：支持完整对话上下文）
     *
     * @param userMessage 当前信众消息
     * @param conversationHistory 对话历史（格式："弟子：xxx\n师父：xxx\n弟子：xxx"）
     */
    suspend fun generateReply(
        userMessage: String,
        conversationHistory: String = "",
        memoryContext: String = ""
    ): LlmReplies? {
        if (apiKey.isBlank()) {
            lastError = "API密钥未配置"
            return null
        }

        try {
            // 构建消息列表
            val messages = buildMessages(userMessage, conversationHistory, memoryContext)

            // 随机温度值增加输出变化
            val temp = 0.80 + Random.nextDouble() * 0.12

            val request = ChatRequest(
                model = model,
                messages = messages,
                temperature = temp,
                maxTokens = 2000
            )

            val requestJson = HttpClient.gson.toJson(request)
            val responseJson = HttpClient.post(
                url = apiUrl,
                bodyJson = requestJson,
                headers = mapOf(
                    "Authorization" to "Bearer $apiKey",
                    "Content-Type" to "application/json"
                )
            )

            val response = HttpClient.gson.fromJson(responseJson, ChatResponse::class.java)

            // 检查 API 返回的错误
            if (response.error != null) {
                lastError = response.error.message ?: "未知API错误"
                return null
            }

            // 解析回复
            val content = response.choices?.firstOrNull()?.message?.content
            if (content.isNullOrBlank()) {
                lastError = "LLM返回空内容"
                return null
            }

            lastError = ""
            return parseReplies(content)

        } catch (e: java.net.ConnectException) {
            lastError = "网络连接失败"
            return null
        } catch (e: java.net.SocketTimeoutException) {
            lastError = "请求超时"
            return null
        } catch (e: java.net.UnknownHostException) {
            lastError = "DNS解析失败，请检查网络"
            return null
        } catch (e: Exception) {
            lastError = "LLM调用异常: ${e.message}"
            return null
        }
    }

    /**
     * 构建 LLM 请求的消息列表
     * 如果有对话历史，会插入一条包含上下文的 user 消息
     */
    private fun buildMessages(
        userMessage: String,
        conversationHistory: String,
        memoryContext: String
    ): List<ChatMessage> {
        val msgList = mutableListOf<ChatMessage>()

        // 系统提示词
        msgList.add(ChatMessage("system", SYSTEM_PROMPT))

        // 注入信众记忆（画像 + 历史对话）
        if (memoryContext.isNotBlank()) {
            msgList.add(ChatMessage(
                "user",
                "以下是这位弟子的记忆档案和历史对话，请记住这些信息，结合它们来理解这位弟子的情况和需求：\n\n$memoryContext"
            ))
            msgList.add(ChatMessage(
                "assistant",
                "（师父已记住这位弟子的情况，会结合其历史给出有连续性的开示）"
            ))
        }

        // 如果有对话历史，先注入上下文
        if (conversationHistory.isNotBlank()) {
            msgList.add(ChatMessage(
                "user",
                "以下是你和弟子最近的对话记录，请结合上下文理解当前问题：\n\n$conversationHistory"
            ))
            msgList.add(ChatMessage(
                "assistant",
                "（已理解对话上下文，准备继续开示）"
            ))
        }

        // 当前问题
        val contextSuffix = if (conversationHistory.isNotBlank() || memoryContext.isNotBlank()) {
            "\n\n（请结合上面的对话历史和弟子记忆来回答，保持话题连贯，给出有针对性的开示）"
        } else ""

        msgList.add(ChatMessage(
            "user",
            "信众说：$userMessage$contextSuffix"
        ))

        return msgList
    }

    /**
     * 解析 LLM 输出的三段式回复
     */
    private fun parseReplies(content: String): LlmReplies {
        val parts = content.split(Regex("\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val compassion = parts.getOrNull(0)
        val karma = parts.getOrNull(1)
        val action = parts.getOrNull(2)

        // 至少保证第一段有内容
        if (compassion != null) {
            return LlmReplies(
                compassion = compassion,
                karma = karma ?: "",
                action = action ?: ""
            )
        }

        // 单段输出，全部作为悲悯共情
        return LlmReplies(
            compassion = content.trim(),
            karma = "",
            action = ""
        )
    }

    /**
     * 检查 LLM 是否可用
     */
    fun isAvailable(): Boolean = apiKey.isNotBlank()
}
