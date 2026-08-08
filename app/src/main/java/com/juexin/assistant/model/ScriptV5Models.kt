package com.juexin.assistant.model

import com.google.gson.annotations.SerializedName

/**
 * V5 多变体话术库根模型
 */
data class ScriptLibraryV5(
    @SerializedName("version") val version: Int,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("description") val description: String,
    @SerializedName("generic") val generic: List<VariantTemplate>,
    @SerializedName("templates") val templates: List<CategoryTemplate>
)

/**
 * 场景分类模板（含多变体）
 */
data class CategoryTemplate(
    @SerializedName("category") val category: String,
    @SerializedName("keywords") val keywords: List<String>,
    @SerializedName("variants") val variants: List<VariantTemplate>
)

/**
 * 单段回复模板
 */
data class VariantTemplate(
    @SerializedName("compassion") val compassion: String,
    @SerializedName("karma") val karma: String,
    @SerializedName("action") val action: String
)
