package com.juexin.assistant.network

/**
 * 大模型预设配置
 *
 * 用户可在设置页选择模型，App 自动切换 API 地址和模型名
 */
object ModelPresets {

    data class ModelPreset(
        val id: String,           // 唯一标识
        val displayName: String,  // 显示名称
        val apiBaseUrl: String,   // API 地址
        val modelName: String,    // 模型参数名
        val description: String,  // 描述
        val keyHint: String       // API Key 获取提示
    )

    /** 预设模型列表 */
    val PRESETS = listOf(
        ModelPreset(
            id = "deepseek-chat",
            displayName = "DeepSeek（默认推荐）",
            apiBaseUrl = "https://api.deepseek.com/chat/completions",
            modelName = "deepseek-chat",
            description = "性价比最高，中文开示效果好",
            keyHint = "platform.deepseek.com 获取 API Key"
        ),
        ModelPreset(
            id = "deepseek-reasoner",
            displayName = "DeepSeek R1（推理增强）",
            apiBaseUrl = "https://api.deepseek.com/chat/completions",
            modelName = "deepseek-reasoner",
            description = "推理能力强，开示更有深度",
            keyHint = "platform.deepseek.com 获取 API Key"
        ),
        ModelPreset(
            id = "gpt-4o",
            displayName = "OpenAI GPT-4o",
            apiBaseUrl = "https://api.openai.com/v1/chat/completions",
            modelName = "gpt-4o",
            description = "OpenAI 旗舰模型",
            keyHint = "platform.openai.com 获取 API Key"
        ),
        ModelPreset(
            id = "gpt-4o-mini",
            displayName = "OpenAI GPT-4o mini",
            apiBaseUrl = "https://api.openai.com/v1/chat/completions",
            modelName = "gpt-4o-mini",
            description = "快速且经济",
            keyHint = "platform.openai.com 获取 API Key"
        ),
        ModelPreset(
            id = "qwen-plus",
            displayName = "通义千问 Plus",
            apiBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            modelName = "qwen-plus",
            description = "阿里通义千问",
            keyHint = "dashscope.console.aliyun.com 获取 API Key"
        ),
        ModelPreset(
            id = "glm-4",
            displayName = "智谱 GLM-4",
            apiBaseUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            modelName = "glm-4",
            description = "智谱清言",
            keyHint = "open.bigmodel.cn 获取 API Key"
        ),
        ModelPreset(
            id = "kimi-k2",
            displayName = "Moonshot Kimi",
            apiBaseUrl = "https://api.moonshot.cn/v1/chat/completions",
            modelName = "moonshot-v1-8k",
            description = "月之暗面 Kimi",
            keyHint = "platform.moonshot.cn 获取 API Key"
        )
    )

    /** 根据 ID 获取预设 */
    fun getById(id: String): ModelPreset? = PRESETS.find { it.id == id }

    /** 获取显示名称列表（供 Spinner 用） */
    fun getDisplayNames(): List<String> = PRESETS.map { it.displayName }
}
