package com.juexin.assistant

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 无障碍服务：实时监听微信聊天，自动提取对话上下文
 *
 * 核心能力：
 * 1. 监听微信窗口内容变化，实时读取当前屏幕消息
 * 2. **自动滑动屏幕，采集佛弟子说过的全部消息**（V4.2 新增）
 * 3. 按会话累积存储，悬浮球打开时读取完整历史
 * 4. 悬浮球读取这些数据，实现上下文感知回复
 */
class WeChatReaderService : AccessibilityService() {

    companion object {
        private const val TAG = "WeChatReader"
        private const val WECHAT_PACKAGE = "com.tencent.mm"
        const val PREFS_NAME = "wechat_reader"
        const val KEY_LAST_INCOMING = "last_incoming"
        const val KEY_LAST_OUTGOING = "last_outgoing"
        const val KEY_CONVERSATION = "conversation_json"
        const val KEY_LAST_UPDATE = "last_update"
        // V4.2 新增：累积的全部佛弟子消息
        const val KEY_ALL_INCOMING = "all_incoming_json"
        // 触发采集的意图动作
        const val ACTION_COLLECT = "com.juexin.assistant.action.COLLECT_ALL"

        // 最近保留的实时对话轮数
        private const val MAX_TURNS = 10
        // 滚动采集最大条数（防止无限滚动）
        private const val MAX_COLLECT = 60
        // 防抖：最小事件间隔(ms)
        private const val DEBOUNCE_MS = 800L
        // 滚动后等待内容加载
        private const val SCROLL_DELAY_MS = 400L

        @Volatile
        var instance: WeChatReaderService? = null
            private set
    }

    // 用于防抖
    private var lastProcessTime = 0L
    // 滚动采集线程
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "无障碍服务已连接")
    }

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
            val chatMessages = filterChatMessages(allMessages)

            if (chatMessages.isEmpty()) return

            // 分离对方消息（左侧）和师父消息（右侧）
            val incoming = chatMessages.filter { it.isIncoming }
            val outgoing = chatMessages.filter { !it.isIncoming }

            val lastIncoming = incoming.lastOrNull()?.text ?: ""
            val lastOutgoing = outgoing.lastOrNull()?.text ?: ""

            // 构建最近 N 轮对话历史
            val conversation = buildConversationHistory(incoming, outgoing)
            val convJson = conversation.joinToString("\n") { t -> t }

            // V4.2：把本次采集到的新"弟子"消息累积到全量存储
            appendToAllIncoming(incoming.map { it.text })

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
     * ==================== V4.2 核心：自动滚动采集全部对话 ====================
     *
     * 悬浮球打开时调用，自动向上滑动微信聊天列表，累积采集佛弟子说过的全部消息
     */
    fun collectAllMessages(onComplete: (List<String>) -> Unit) {
        handler.post {
            val collected = mutableListOf<String>()  // 本次新增
            val seen = HashSet<String>()             // 去重（避免滚动重复读到同一屏）
            scrollAndCollect(collected, seen, 0, onComplete)
        }
    }

    /**
     * 递归滚动 + 采集
     */
    private fun scrollAndCollect(
        collected: MutableList<String>,
        seen: HashSet<String>,
        scrollCount: Int,
        onComplete: (List<String>) -> Unit
    ) {
        if (scrollCount >= MAX_COLLECT) {
            // 达到上限，结束
            val allIncoming = loadAllIncoming()
            onComplete(allIncoming)
            return
        }

        // 读取当前屏幕的佛弟子消息
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            onComplete(loadAllIncoming())
            return
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val allMessages = mutableListOf<RawMessage>()
        try {
            collectMessages(rootNode, screenWidth, allMessages)
        } finally {
            rootNode.recycle()
        }

        val incoming = filterChatMessages(allMessages)
            .filter { it.isIncoming }

        // 记录新的消息
        var addedNew = false
        for (msg in incoming) {
            val text = msg.text
            if (text.length in 2..800 && seen.add(text)) {
                collected.add(text)
                addedNew = true
            }
        }

        // 追加到全量存储
        appendToAllIncoming(collected)

        // 判断是否需要继续滚动：找到可滚动的列表节点并向上滚动
        val scrollable = findScrollableNode(rootInActiveWindow)
        if (scrollable != null && addedNew) {
            val success = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            if (success) {
                // 等待内容加载后继续
                handler.postDelayed({
                    scrollAndCollect(collected, seen, scrollCount + 1, onComplete)
                }, SCROLL_DELAY_MS)
                return
            }
        }

        // 无法继续滚动或没有新内容，结束
        val allIncoming = loadAllIncoming()
        onComplete(allIncoming)
    }

    /**
     * 查找可滚动的列表节点（聊天列表）
     */
    private fun findScrollableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isScrollable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            if (child.isScrollable) return child
            val result = findScrollableNode(child)
            if (result != null) {
                child.recycle()
                return result
            }
            child.recycle()
        }
        return null
    }

    /**
     * 过滤聊天消息中的 UI 元素
     */
    private fun filterChatMessages(allMessages: List<RawMessage>): List<RawMessage> {
        val uiBlacklist = setOf("微信", "返回", "通讯录", "发现", "我",
            "发送", "按住说话", "复制", "转发", "收藏", "删除", "多选",
            "引用", "提醒", "搜一搜", "表情")
        return allMessages.filter { msg ->
            msg.text.length in 2..800 &&
                    uiBlacklist.none { msg.text.contains(it.trim(), ignoreCase = false) }
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

    // ==================== 累积存储 ====================

    /**
     * 把新的"弟子"消息追加到全量存储（JSONArray 形式，去重）
     */
    private fun appendToAllIncoming(newMessages: List<String>) {
        if (newMessages.isEmpty()) return
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val existing = prefs.getString(KEY_ALL_INCOMING, null)
        val arr = if (existing != null) {
            try { JSONArray(existing) } catch (e: Exception) { JSONArray() }
        } else JSONArray()

        val seen = HashSet<String>()
        for (i in 0 until arr.length()) {
            try { seen.add(arr.getString(i)) } catch (_: Exception) {}
        }
        var added = false
        for (msg in newMessages) {
            if (msg.isNotBlank() && seen.add(msg)) {
                arr.put(msg)
                added = true
            }
        }
        if (added) {
            prefs.edit().putString(KEY_ALL_INCOMING, arr.toString()).apply()
        }
    }

    /**
     * 读取累积的全部佛弟子消息
     */
    fun loadAllIncoming(): List<String> {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val existing = prefs.getString(KEY_ALL_INCOMING, null) ?: return emptyList()
        return try {
            val arr = JSONArray(existing)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 清除累积消息（新会话时调用）
     */
    fun clearAllIncoming() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().remove(KEY_ALL_INCOMING).apply()
    }

    override fun onInterrupt() {}
}

/** 消息数据结构 */
data class RawMessage(
    val text: String,
    val isIncoming: Boolean,
    val viewId: String,
    val y: Int
)
