package com.juexin.assistant.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * 通用 HTTP 客户端
 *
 * 优化（V5.2）：
 * - 连接池复用（HTTP keep-alive）
 * - gzip 压缩响应
 * - 失败自动重试（最多2次，幂等GET）
 * - 消除自定义 IOException 与标准库命名冲突
 */
object HttpClient {

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    val gson = Gson()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** 请求异常（替代原先自定义 IOException，避免与 java.io.IOException 冲突） */
    class HttpException(message: String, val code: Int = -1) : Exception(message)

    /**
     * GET 请求（带自动重试）
     */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        return executeWithRetry(url, headers) { builder ->
            builder.get()
        }
    }

    /**
     * POST 请求
     */
    suspend fun post(url: String, bodyJson: String, headers: Map<String, String> = emptyMap()): String {
        val body = bodyJson.toRequestBody(JSON_MEDIA)
        return suspendCoroutine { cont ->
            val builder = Request.Builder().url(url).post(body)
            headers.forEach { (k, v) -> builder.addHeader(k, v) }

            client.newCall(builder.build()).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    cont.resumeWithException(HttpException("网络请求失败: ${e.message}"))
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val respBody = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        cont.resume(respBody)
                    } else {
                        cont.resumeWithException(HttpException("HTTP ${response.code}: $respBody", response.code))
                    }
                }
            })
        }
    }

    /**
     * 带重试的 GET 请求（幂等）
     */
    private suspend fun executeWithRetry(
        url: String,
        headers: Map<String, String>,
        configure: (Request.Builder) -> Unit
    ): String {
        var lastError: HttpException? = null
        repeat(3) { attempt ->
            try {
                return suspendCoroutine { cont ->
                    val builder = Request.Builder().url(url)
                    headers.forEach { (k, v) -> builder.addHeader(k, v) }
                    configure(builder)

                    client.newCall(builder.build()).enqueue(object : okhttp3.Callback {
                        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                            cont.resumeWithException(HttpException("网络请求失败: ${e.message}"))
                        }
                        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                            val body = response.body?.string() ?: ""
                            if (response.isSuccessful) {
                                cont.resume(body)
                            } else {
                                cont.resumeWithException(HttpException("HTTP ${response.code}: $body", response.code))
                            }
                        }
                    })
                }
            } catch (e: HttpException) {
                lastError = e
                // 4xx 错误不重试，5xx/网络错误重试
                if (e.code in 400..499) throw e
                if (attempt < 2) {
                    kotlinx.coroutines.delay(500L * (attempt + 1))
                }
            }
        }
        throw lastError ?: HttpException("请求失败")
    }
}
