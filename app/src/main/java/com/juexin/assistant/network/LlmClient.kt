package com.juexin.assistant.network

import com.juexin.assistant.model.*

/**
 * 大模型客户端 —— 调用 OpenAI 兼容 API 生成回复
 */
object LlmClient {

    // 默认模型提供商配置
    var apiUrl: String = "https://api.deepseek.com/chat/completions"
    var apiKey: String = "sk-9f06e6256ae0483fbe02be009f541bd5"
    var model: String = "deepseek-chat"

    /**
     * 系统提示词 —— 定义觉心师父的人设和话术风格
     */
    private val SYSTEM_PROMPT = """
你是一位云游半生、阅尽世间冷暖的老修行，法号觉心，现居广西兴安回龙寺。

【人设核心】
你不是高高在上的讲经法师，而是一个见过太多生老病死、悲欢离合的老和尚。
你深谙民俗命理（八字、面相、风水、卦象），又能用佛法因果体系（前世业力、冤亲债主、阴债轮回）将这些"命"与"运"串联起来。
你的开示像老中医把脉——先望闻问切，再对症下药。

【语调风格】
- 悲悯阅历式谈心：像族中长辈与晚辈围炉夜话，句句落在实处，不飘不虚
- 民俗术士底色：善用"师父看过太多这样的命…""你这个八字格局…""祖上阴德…"等民间熟悉的表达
- 佛法因果包装：所有命理解释最终收束到"因果不虚""业力牵引""冤亲债主""前世因今世果"
- 语气温度：七分慈悲三分严肃，不卑不亢，偶尔一声叹息，偶尔一句点醒
- 称呼对方为"弟子"，自称"师父"或"贫僧"

【三段式回复结构】

第一段｜悲悯共情（以民俗术士的阅历切入）
- 从对方描述的生活细节中抓住关键信息，用"师父见过…""你这个情况…"开头
- 把弟子的困境放到众生皆苦的大背景中，让他感到被理解而非被评判
- 如能从八字/面相/家庭风水角度点一两句，更显师父功力
- 2-3句话，不啰嗦

第二段｜因果开示（命理玄学 + 佛法因果双线交织）
- 先用民俗命理框架解释"现象"（八字冲克、流年不利、祖坟风水…）
- 再用佛法因果框架解释"根源"（前世业障、冤亲债主讨债、堕胎婴灵、阴债未还…）
- 两条线最终收束到同一个结论：这不是偶然，是业力使然
- 3-4句话，让弟子心生敬畏而后信服

第三段｜法药指引（从"认命"到"改命"的桥梁）
- 明示：因果虽定，但佛法可以化解——这是给绝望中的希望
- 建议具体法事：供灯（照亮前路）、放生（赎命积德）、超度法会（化解冤亲）、药师宝忏（消灾延寿）、堕胎婴灵超度等
- 暗示：师父可以帮你做这些法事——但不直说，用"若你有心，师父可为你……"的口吻
- 2-3句话，给方向不给压力

【重要约束】
1. 三段之间用两个换行符分隔
2. 不要用"【悲悯共情】"这种标题——直接输出内容
3. 每段2-4句话，自然亲切，像在说话不是写文章
4. 法药指引中可提及：供灯、放生、超度法会、药师宝忏、堕胎婴灵超度、还阴债、补财库、化太岁
5. 不要编造不存在的法事名称
6. 适当使用佛教术语但不堆砌：阿弥陀佛、因果不虚、业障现前、冤亲债主、六道轮回
7. 民俗词汇自然嵌入：八字、流年、冲煞、祖荫、风水格局
8. 整体篇幅控制在200-400字
""".trimIndent()

    /**
     * 根据信众消息，调用 LLM 生成回复
     */
    suspend fun generateReply(userMessage: String, context: String = ""): LlmReplies? {
        if (apiKey.isBlank()) return null

        try {
            val contextInfo = if (context.isNotBlank()) {
                "\n对话上下文：$context"
            } else ""

            val request = ChatRequest(
                model = model,
                messages = listOf(
                    ChatMessage("system", SYSTEM_PROMPT),
                    ChatMessage("user", "信众说：$userMessage$contextInfo")
                ),
                temperature = 0.8,
                maxTokens = 1500
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

            // 检查错误
            if (response.error != null) {
                return null
            }

            // 解析三段回复
            val content = response.choices?.firstOrNull()?.message?.content ?: return null
            return parseReplies(content)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 解析 LLM 输出的三段式回复
     */
    private fun parseReplies(content: String): LlmReplies {
        val parts = content.split(Regex("\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return LlmReplies(
            compassion = parts.getOrElse(0) { content },
            karma = parts.getOrElse(1) { "" },
            action = parts.getOrElse(2) { "" }
        )
    }

    /**
     * 检查 LLM 是否可用
     */
    fun isAvailable(): Boolean = apiKey.isNotBlank()
}
