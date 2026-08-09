package com.juexin.assistant.database

import android.content.Context
import com.google.gson.Gson
import com.juexin.assistant.model.ScriptLibraryV5
import com.juexin.assistant.model.VariantTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.random.Random

/**
 * V5 话术库仓库 —— 从 assets 内置库 + 数据库加载多变体话术
 *
 * 特点：
 * 1. 内置 assets/scripts/library.json（多场景多变体）
 * 2. 首次启动导入 Room 数据库
 * 3. 支持多变体随机 + 按使用频率优化
 */
object LibraryV5Repository {

    private val gson = Gson()
    @Volatile
    private var libraryV5: ScriptLibraryV5? = null

    /** 加载成功标志 */
    @Volatile
    var isLoaded: Boolean = false
        private set

    /** 加载失败原因（供诊断） */
    @Volatile
    var lastError: String = ""
        private set

    /**
     * 初始化：从 assets 加载话术库并导入数据库
     */
    suspend fun init(context: Context) {
        if (libraryV5 != null) return
        withContext(Dispatchers.IO) {
            try {
                // 从 assets 加载
                val json = readAsset(context, "scripts/library.json")
                if (json.isBlank()) {
                    lastError = "assets 话术库为空或读取失败"
                    return@withContext
                }
                libraryV5 = gson.fromJson(json, ScriptLibraryV5::class.java)
                if (libraryV5 == null) {
                    lastError = "话术库 JSON 解析为 null"
                    return@withContext
                }
                isLoaded = true
                lastError = ""

                // 导入数据库（供查询统计）——独立 try，不影响主流程
                try {
                    val db = AppDatabase.getInstance(context)
                    val entities = buildEntities()
                    if (db.scriptTemplateDao().count() < entities.size) {
                        db.scriptTemplateDao().insertAll(entities)
                    }
                } catch (e: Exception) {
                    // 数据库导入失败不影响话术匹配
                }
            } catch (e: Exception) {
                lastError = "话术库加载异常: ${e.message}"
                libraryV5 = null
            }
        }
    }

    /**
     * 根据消息匹配多变体话术
     * @return 命中的 VariantTemplate（随机选一变体）
     */
    fun match(message: String): VariantTemplate? {
        val lib = libraryV5 ?: return null
        val m = message.lowercase()

        // 遍历分类模板，关键词匹配
        for (tpl in lib.templates) {
            for (kw in tpl.keywords) {
                if (m.contains(kw)) {
                    return tpl.variants.randomOrNull()
                }
            }
        }
        // 未命中分类，返回通用
        return lib.generic.randomOrNull()
    }

    /**
     * 获取所有分类（供设置页展示）
     */
    fun getCategories(): List<String> {
        return libraryV5?.templates?.map { it.category } ?: emptyList()
    }

    /**
     * 构建数据库实体
     */
    private fun buildEntities(): List<ScriptTemplateEntity> {
        val lib = libraryV5 ?: return emptyList()
        val list = mutableListOf<ScriptTemplateEntity>()
        for (tpl in lib.templates) {
            for (v in tpl.variants) {
                list.add(ScriptTemplateEntity(
                    category = tpl.category,
                    keywords = tpl.keywords.joinToString(","),
                    compassion = v.compassion,
                    karma = v.karma,
                    action = v.action,
                    source = "local_v5"
                ))
            }
        }
        return list
    }

    /**
     * 读取 assets 文件
     */
    private fun readAsset(context: Context, path: String): String {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            ""
        }
    }
}
