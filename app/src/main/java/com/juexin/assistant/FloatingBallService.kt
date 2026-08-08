package com.juexin.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import com.juexin.assistant.network.LlmClient
import kotlinx.coroutines.*

/**
 * 觉心助手悬浮球服务 — 在微信聊天界面实时辅助师父回复信众
 *
 * V3.0 更新：
 * - 集成无障碍服务实时读取微信聊天
 * - 面板定位屏幕上半部，半透明浮层不遮挡聊天
 * - 显示对方消息上下文 + 已回复内容
 * - 支持实时交互：查看消息 → 生成开示 → 复制回复
 */
class FloatingBallService : Service() {

    companion object {
        private const val CHANNEL_ID = "floating_ball_channel"
        private const val NOTIFICATION_ID = 1001

        // 意图动作常量
        const val ACTION_STOP = "com.juexin.assistant.action.STOP"
        const val ACTION_SHOW_REPLIES = "com.juexin.assistant.action.SHOW_REPLIES"
        const val EXTRA_CLIPBOARD_TEXT = "clipboard_text"

        // 外部实例引用（供 WeChatReaderService 通知新消息）
        // 注意：使用 var + private set 会自动生成 getInstance()，不要再显式定义同名方法
        @Volatile
        var instance: FloatingBallService? = null
            private set

        /**
         * 无障碍服务检测到新消息时调用，通知悬浮球更新
         */
        fun onNewMessage(incoming: String, outgoing: String) {
            instance?.let { svc ->
                Handler(Looper.getMainLooper()).post {
                    svc.lastIncomingMsg = incoming
                    svc.lastOutgoingMsg = outgoing
                    // 如果输入面板正在显示，实时更新上下文
                    svc.updateContextDisplay()
                }
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var inputPanel: View? = null
    private var resultPanel: View? = null

    // UI 组件 - 输入面板
    private var etInput: EditText? = null
    private var tvContextIncoming: TextView? = null
    private var tvContextLabel: TextView? = null
    private var tvContextStatus: TextView? = null

    // UI 组件 - 结果面板
    private var tvCompassion: TextView? = null
    private var tvKarma: TextView? = null
    private var tvAction: TextView? = null
    private var tvSource: TextView? = null
    private var tvStatus: TextView? = null
    private var copyActionBtn: Button? = null
    private var copyCompassionBtn: Button? = null
    private var copyKarmaBtn: Button? = null

    // 保存的回复内容
    private var savedCompassion: String = ""
    private var savedKarma: String = ""
    private var savedAction: String = ""

    // 聊天上下文（由无障碍服务实时更新）
    @Volatile
    var lastIncomingMsg: String = ""
    @Volatile
    var lastOutgoingMsg: String = ""

    // 拖拽状态
    private var downRawX = 0f
    private var downRawY = 0f

    // 协程
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        showFloatingBall()
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        removeAllViews()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_STOP -> {
                    stopSelf()
                }
                ACTION_SHOW_REPLIES -> {
                    val text = it.getStringExtra(EXTRA_CLIPBOARD_TEXT) ?: ""
                    if (text.isNotBlank()) {
                        lastIncomingMsg = text
                        showInputPanel()
                    }
                }
            }
        }
        return START_STICKY
    }

    // ========== 悬浮球 ==========

    private fun showFloatingBall() {
        val ball = LayoutInflater.from(this).inflate(R.layout.floating_ball, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 300  // 上半屏位置
        }

        windowManager.addView(ball, params)
        floatingView = ball

        // 半透明状态
        ball.findViewById<ImageView>(R.id.iv_icon)?.alpha = 0.7f

        // 点击 → 打开输入面板
        ball.findViewById<ImageView>(R.id.iv_icon)?.setOnClickListener {
            showInputPanel()
        }

        // 拖拽
        ball.findViewById<ImageView>(R.id.iv_icon)?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (windowManager.defaultDisplay.width - event.rawX).toInt()
                    params.y = event.rawY.toInt() - 100
                    windowManager.updateViewLayout(ball, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    windowManager.updateViewLayout(ball, params)
                    val dx = Math.abs(event.rawX - downRawX)
                    val dy = Math.abs(event.rawY - downRawY)
                    if (dx < 10 && dy < 10) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ========== 输入面板（上半屏半透明） ==========

    private fun showInputPanel() {
        scope.launch {
            // 安全移除旧面板
            removePanelSafely(inputPanel)
            inputPanel = null
            removePanelSafely(resultPanel)
            resultPanel = null

            // 预先加载聊天上下文
            loadChatContext()

            val panel = LayoutInflater.from(this@FloatingBallService)
                .inflate(R.layout.panel_input, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                y = dpToPx(60)  // 避开状态栏
                alpha = 0.95f
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

            // 绑定UI元素
            etInput = panel.findViewById(R.id.et_input)
            tvContextIncoming = panel.findViewById(R.id.tv_context_incoming)
            tvContextLabel = panel.findViewById(R.id.tv_context_label)
            tvContextStatus = panel.findViewById(R.id.tv_context_status)

            // 填入当前聊天上下文
            updateContextDisplay()

            // V4.2：触发自动采集佛弟子说过的全部消息（异步滚动读取，tvContextStatus 已绑定）
            triggerCollectAll()

            // 关闭按钮
            panel.findViewById<Button>(R.id.btn_close_input)?.setOnClickListener {
                closeInputPanel()
            }

            // 清空按钮
            panel.findViewById<Button>(R.id.btn_clear_input)?.setOnClickListener {
                etInput?.text?.clear()
            }

            // 生成按钮
            panel.findViewById<Button>(R.id.btn_generate)?.setOnClickListener {
                val input = etInput?.text?.toString()?.trim() ?: ""
                val contextText = lastIncomingMsg

                val finalInput = if (input.isNotBlank()) input
                else if (contextText.isNotBlank()) contextText
                else {
                    Toast.makeText(this@FloatingBallService, "请先输入问题或等待信众消息", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                generateReply(finalInput)
            }

            windowManager.addView(panel, params)
            inputPanel = panel
        }
    }

    /**
     * 更新聊天上下文显示
     */
    private fun updateContextDisplay() {
        val ctxIncoming = tvContextIncoming ?: return
        val ctxLabel = tvContextLabel ?: return
        val ctxStatus = tvContextStatus ?: return

        if (lastIncomingMsg.isNotBlank()) {
            ctxIncoming.text = lastIncomingMsg
            ctxLabel.text = "信众最新留言："
            ctxStatus.text = if (lastOutgoingMsg.isNotBlank())
                "已回复：${lastOutgoingMsg.take(30)}..." else ""
        } else {
            // 尝试从 SharedPreferences 重新加载
            loadChatContext()
            if (lastIncomingMsg.isNotBlank()) {
                ctxIncoming.text = lastIncomingMsg
            } else {
                ctxLabel.text = "聊天上下文："
                ctxIncoming.text = "尚未收到消息\n请确保已开启无障碍服务"
                ctxStatus.text = ""
            }
        }
    }

    /**
     * 从 SharedPreferences 加载聊天上下文
     */
    private fun loadChatContext() {
        val prefs = getSharedPreferences(WeChatReaderService.PREFS_NAME, MODE_PRIVATE)
        val incoming = prefs.getString(WeChatReaderService.KEY_LAST_INCOMING, "")
        val outgoing = prefs.getString(WeChatReaderService.KEY_LAST_OUTGOING, "")
        val lastUpdate = prefs.getLong(WeChatReaderService.KEY_LAST_UPDATE, 0L)

        if (!incoming.isNullOrBlank()) {
            lastIncomingMsg = incoming
        }
        if (!outgoing.isNullOrBlank()) {
            lastOutgoingMsg = outgoing
        }
    }

    /**
     * V4.2：触发自动滚动采集佛弟子说过的全部消息
     * 采集完成后刷新上下文显示，并缓存最新一条到 lastIncomingMsg
     */
    private fun triggerCollectAll() {
        // 无障碍服务未连接时提示
        if (WeChatReaderService.instance == null) {
            tvContextStatus?.text = "未开启无障碍，无法自动采集（设置→更多设置→无障碍→觉心助手）"
            return
        }
        WeChatReaderService.instance?.let { svc ->
            tvContextStatus?.text = "正在采集佛弟子历史消息..."
            svc.collectAllMessages { allIncoming ->
                // 回到主线程更新 UI
                Handler(Looper.getMainLooper()).post {
                    if (allIncoming.isNotEmpty()) {
                        // 更新最近一条为最后一条佛弟子消息
                        lastIncomingMsg = allIncoming.last()
                        tvContextStatus?.text = "已采集佛弟子消息 ${allIncoming.size} 条"
                    } else {
                        tvContextStatus?.text = "未采集到消息（请确认无障碍已开启）"
                    }
                    updateContextDisplay()
                }
            }
        }
    }

    // ========== 生成回复 V4.0：统一使用 ReplyGenerator 三层架构 ==========

    private fun generateReply(userMessage: String) {
        scope.launch {
            tvStatus?.text = "正在恭请师父开示..."

            try {
                // 加载完整对话历史
                val convHistory = loadConversationHistory()

                // 统一通过 ReplyGenerator 生成（V5：多变体话术库 → LLM+记忆+上下文 → 本地兜底）
                val result = withContext(Dispatchers.IO) {
                    ReplyGenerator.generateReply(
                        context = this@FloatingBallService,
                        userMessage = userMessage,
                        conversationHistory = convHistory,
                        devoteeId = "default"
                    )
                }

                savedCompassion = result.compassion
                savedKarma = result.karma
                savedAction = result.action
                showResultPanel(result)

            } catch (e: Exception) {
                // 最终降级
                val local = ReplyGenerator.generate(userMessage)
                savedCompassion = local.compassion
                savedKarma = local.karma
                savedAction = local.action
                showResultPanel(local)
            }
        }
    }

    /**
     * 从 SharedPreferences 加载完整对话历史（V4.2：优先读取累积的全部佛弟子消息）
     */
    private fun loadConversationHistory(): String {
        return try {
            WeChatReaderService.instance?.let { svc ->
                val allIncoming = svc.loadAllIncoming()
                if (allIncoming.isNotEmpty()) {
                    // 汇总佛弟子说过的全部内容，供 LLM 综合分析
                    return allIncoming.joinToString("\n") { "弟子：$it" }
                }
            }
            // 回退：读取最近 N 轮对话
            val prefs = getSharedPreferences(WeChatReaderService.PREFS_NAME, MODE_PRIVATE)
            prefs.getString(WeChatReaderService.KEY_CONVERSATION, "") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // ========== 结果面板（上半屏半透明） ==========

    private fun showResultPanel(result: ReplyResult) {
        removePanelSafely(inputPanel)
        inputPanel = null
        removePanelSafely(resultPanel)
        resultPanel = null

        val panel = LayoutInflater.from(this).inflate(R.layout.panel_reply, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = dpToPx(60)
            alpha = 0.95f
        }

        // 绑定UI
        tvCompassion = panel.findViewById(R.id.tv_compassion)
        tvKarma = panel.findViewById(R.id.tv_karma)
        tvAction = panel.findViewById(R.id.tv_action)
        tvSource = panel.findViewById(R.id.tv_source)
        tvStatus = panel.findViewById(R.id.tv_status)
        copyCompassionBtn = panel.findViewById(R.id.btn_copy_compassion)
        copyKarmaBtn = panel.findViewById(R.id.btn_copy_karma)
        copyActionBtn = panel.findViewById(R.id.btn_copy_action)

        // 填充内容
        tvCompassion?.text = result.compassion
        tvKarma?.text = result.karma
        tvAction?.text = result.action
        tvSource?.text = when (result.source) {
            ReplySource.LLM -> "AI 生成"
            ReplySource.REMOTE_SCRIPT -> "话术库"
            ReplySource.LOCAL_FALLBACK -> "本地话术"
        }
        // 若 LLM 不可用，在状态栏提示具体原因（便于诊断）
        tvStatus?.text = if (result.source == ReplySource.LLM) {
            ""
        } else if (!result.llmError.isNullOrBlank()) {
            "⚠️ AI未生效: ${result.llmError}"
        } else {
            ""
        }

        // 关闭按钮
        panel.findViewById<Button>(R.id.btn_close_result)?.setOnClickListener {
            closeResultPanel()
        }

        // 复制按钮
        copyCompassionBtn?.setOnClickListener { copyToClipboard(result.compassion, "悲悯共情") }
        copyKarmaBtn?.setOnClickListener { copyToClipboard(result.karma, "因果开示") }
        copyActionBtn?.setOnClickListener { copyToClipboard(result.action, "法药指引") }

        // 复制全文
        panel.findViewById<Button>(R.id.btn_copy_all)?.setOnClickListener {
            val full = "${result.compassion}\n\n${result.karma}\n\n${result.action}"
            copyToClipboard(full, "全文开示")
        }

        // 重新生成
        panel.findViewById<Button>(R.id.btn_regenerate)?.setOnClickListener {
            // 回到输入面板
            showInputPanel()
            etInput?.setText(lastIncomingMsg)
        }

        windowManager.addView(panel, params)
        resultPanel = panel
    }

    // ========== 辅助方法 ==========

    private fun copyToClipboard(text: String, label: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "已复制 $label", Toast.LENGTH_SHORT).show()
    }

    private fun closeInputPanel() {
        removePanelSafely(inputPanel)
        inputPanel = null
        floatingView?.findViewById<ImageView>(R.id.iv_icon)?.alpha = 0.7f
    }

    private fun closeResultPanel() {
        removePanelSafely(resultPanel)
        resultPanel = null
    }

    private fun showErrorNotification(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun removePanelSafely(view: View?) {
        view?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
    }

    private fun removeAllViews() {
        removePanelSafely(floatingView)
        floatingView = null
        removePanelSafely(inputPanel)
        inputPanel = null
        removePanelSafely(resultPanel)
        resultPanel = null
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    // ========== 前台通知 ==========

    private fun startForegroundNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID, "觉心助手", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "悬浮球服务运行中"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("觉心助手")
            .setContentText("悬浮球已就绪，等待信众消息...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }
}
