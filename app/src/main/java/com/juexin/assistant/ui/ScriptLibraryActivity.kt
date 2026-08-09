package com.juexin.assistant.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.gson.Gson
import com.juexin.assistant.R
import com.juexin.assistant.model.ScriptLibraryV5

/**
 * 话术共享库页面
 *
 * 可视化浏览 11 大场景的多变体话术
 */
class ScriptLibraryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_library)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        loadAndDisplayLibrary()
    }

    private fun loadAndDisplayLibrary() {
        val container = findViewById<LinearLayout>(R.id.container_categories)
        container.removeAllViews()

        try {
            // 从 assets 加载话术库
            val json = assets.open("scripts/library.json").bufferedReader().use { it.readText() }
            val library = Gson().fromJson(json, ScriptLibraryV5::class.java)

            // 统计信息
            val totalVariants = library.templates.sumOf { it.variants.size }
            addInfoCard(container, "共 ${library.templates.size} 大场景，$totalVariants 个变体话术")

            // 遍历每个分类
            for (template in library.templates) {
                addCategoryCard(container, template.category, template.keywords, template.variants.size)
            }
        } catch (e: Exception) {
            addInfoCard(container, "话术库加载失败: ${e.message}")
        }
    }

    /**
     * 添加信息卡片
     */
    private fun addInfoCard(container: LinearLayout, text: String) {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_script_info, container, false) as CardView
        val tv = card.findViewById<TextView>(R.id.tv_info)
        tv.text = text
        container.addView(card)
    }

    /**
     * 添加分类卡片
     */
    private fun addCategoryCard(container: LinearLayout, category: String, keywords: List<String>, variantCount: Int) {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_script_category, container, false) as CardView

        card.findViewById<TextView>(R.id.tv_category).text = category
        card.findViewById<TextView>(R.id.tv_keywords).text = "关键词：${keywords.take(5).joinToString("、")}${if (keywords.size > 5) "..." else ""}"
        card.findViewById<TextView>(R.id.tv_variant_count).text = "$variantCount 个变体"

        container.addView(card)
    }
}
