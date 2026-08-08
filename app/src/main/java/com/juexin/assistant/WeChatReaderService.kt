package com.juexin.assistant

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务：实时监听微信聊天，自动提取对话上下文
 *
 * 核心能力：
 * 1. 监听微信窗口内容变化
 * 2. 通过节点屏幕坐标区分"对方消息"（左侧）vs "师父消息"（右侧）
 * 3. 自动缓存最近N轮对话到 SharedPreferences
 * 4. 悬浮球读取这些数据，实现上下文感知回复
 */
class WeChatReaderService : AccessibilityService() {

    companion object {
        private const val WECHAT_PACKAGE = "com.tencent.mm"
        const val PREFS_NAME = "wechat_reader"
        const val KEY_LAST_INCOMING = "last_incoming"
        const val KEY_LAST_OUTGOING = "last_outgoing"
        const val KEY_CONVERSATION = "conversation_json"
        const val KEY_LAST_UPDATE = "last_update"

        // 最近保留的对话轮数
        private const val MAX_TURNS = 10

        // 防抖：最小事件间隔(ms)
        private const val DEBOUNCE_MS = 800L
    }

    // 用于防抖
    private var lastProcessTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() != WECHAT_PACKAGE) return

        // 只关注内容变化
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val now = System.currentTimeMillis()
                if (now - lastProcessTime < DEBOUNCE_MS) return
                lastProcessTime = now
                readChatMessages()
            }
        }
    }

    /**
     * 从屏幕读取微信聊天消息，区分对方和师父
     */
    private fun readChatMessages() {
        val rootNode = rootInActiveWindow ?: return

        try {
            val screenWidth = resources.displayMetrics.widthPixels
            val allMessages = mutableListOf<RawMessage>()
            collectMessages(rootNode, screenWidth, allMessages)

            if (allMessages.isEmpty()) return

            // 过滤掉UI元素
            val uiBlacklist = setOf("微信", "返回", "通讯录", "发现", "我",
                "发送", "按住说话", "复制", "转发", "收藏", "删除", "多选",
                "引用", "提醒", "搜一搜", "表情")

            val chatMessages = allMessages.filter { msg ->
                msg.text.length in 2..800 &&
                        uiBlacklist.none { msg.text.contains(it.trim(), ignoreCase = false) }
            }

            if (chatMessages.isEmpty()) return

            // 分离对方消息（左侧）和师父消息（右侧）
            val incoming = chatMessages.filter { it.isIncoming }
            val outgoing = chatMessages.filter { !it.isIncoming }

            val lastIncoming = incoming.lastOrNull()?.text ?: ""
            val lastOutgoing = outgoing.lastOrNull()?.text ?: ""

            // 构建最近 N 轮对话历史
            val conversation = buildConversationHistory(incoming, outgoing)
            val convJson = conversation.joinToString("\n") { t -> t }

            // 存入 SharedPreferences
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_LAST_INCOMING, lastIncoming)
                .putString(KEY_LAST_OUTGOING, lastOutgoing)
                .putString(KEY_CONVERSATION, convJson)
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .apply()

            // 通知悬浮球有新消息（通过广播）
            FloatingBallService.onNewMessage(lastIncoming, lastOutgoing)

        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 递归遍历节点树，按屏幕位置区分收发
     */
    private fun collectMessages(
        node: AccessibilityNodeInfo,
        screenWidth: Int,
        results: MutableList<RawMessage>
    ) {
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && text.length > 1) {
            val rect = Rect()
            node.getBoundsInScreen(rect)

            // 微信左侧气泡通常 x < screenWidth/2（对方消息）
            val isIncoming = rect.left < screenWidth * 0.45f
            val viewId = node.viewIdResourceName ?: ""

            results.add(RawMessage(
                text = text,
                isIncoming = isIncoming,
                viewId = viewId,
                y = rect.top
            ))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectMessages(child, screenWidth, results)
            } finally {
                child.recycle()
            }
        }
    }

    /**
     * 将分离后的消息构建成对话历史
     * 按Y坐标排序，模拟实际对话顺序
     */
    private fun buildConversationHistory(
        incoming: List<RawMessage>,
        outgoing: List<RawMessage>
    ): List<String> {
        val all = (incoming.map { "弟子" to it } + outgoing.map { "师父" to it })
            .sortedBy { it.second.y }
            .takeLast(MAX_TURNS)

        return all.map { (role, msg) -> "$role：${msg.text}" }
    }

    /**
     * 判断文本是否为微信UI元素
     */
    private fun isWeChatUI(text: String): Boolean {
        val patterns = listOf(
            Regex("^\\d{1,2}:\\d{2}$"),      // 时间
            Regex("^\\d+条新消息$"),          // 微信提示
            Regex("^对方正在输入"),
            Regex("^\\[.*]$"),                // 微信系统消息 [动画表情] [图片] 等
            Regex("^你撤回了一条消息"),
            Regex("^对方撤回了一条消息"),
        )
        return patterns.any { it.matches(text) }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
    }
}

/** 消息数据结构 */
data class RawMessage(
    val text: String,
    val isIncoming: Boolean,
    val viewId: String,
    val y: Int
)
