package com.moontv.app.data.remote.api

import com.moontv.app.config.TmdbConfig
import com.moontv.app.data.model.TmdbDetail
import com.moontv.app.data.model.TmdbItem
import com.moontv.app.data.model.TmdbSearchResponse
import com.moontv.app.net.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * TMDB 元数据客户端（替代豆瓣爬取的合规方案）
 * 官方文档: https://developer.themoviedb.org/reference
 *
 * 需用户在设置页填入 API Key（对应原项目的 TMDB_API_KEY 环境变量）
 */
class TmdbApi(
    private val apiKey: String,
    private val echEnabled: Boolean = false,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {

    private val client by lazy { HttpClientFactory.create(echEnabled) }

    /** 多类型搜索（电影+剧集），对应原项目搜索页的影视发现 */
    suspend fun search(query: String, page: Int = 1): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        val url = "${TmdbConfig.BASE_URL}search/multi".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("language", "zh-CN")
            .addQueryParameter("query", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("include_adult", "false")
            .build()
        val resp = fetch(url.toString()) ?: return@withContext emptyList()
        val data = runCatching { json.decodeFromString<TmdbSearchResponse>(resp) }.getOrNull()
            ?: return@withContext emptyList()
        data.results.filter { it.mediaType == "movie" || it.mediaType == "tv" }
    }

    /** 获取详情（movie 或 tv） */
    suspend fun detail(id: Long, isMovie: Boolean): TmdbDetail? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        val type = if (isMovie) "movie" else "tv"
        val url = "${TmdbConfig.BASE_URL}$type/$id".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("language", "zh-CN")
            .build()
        val resp = fetch(url.toString()) ?: return@withContext null
        runCatching { json.decodeFromString<TmdbDetail>(resp) }.getOrNull()
    }

    /** 热门/趋势（对应原项目豆瓣推荐位的替代） */
    suspend fun trending(mediaType: String = "all", window: String = "week"): List<TmdbItem> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext emptyList()
            val url = "${TmdbConfig.BASE_URL}trending/$mediaType/$window".toHttpUrl().newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("language", "zh-CN")
                .build()
            val resp = fetch(url.toString()) ?: return@withContext emptyList()
            val data = runCatching { json.decodeFromString<TmdbSearchResponse>(resp) }.getOrNull()
                ?: return@withContext emptyList()
            data.results.filter { it.mediaType == "movie" || it.mediaType == "tv" }
        }

    /** 拼接海报完整 URL */
    fun posterUrl(path: String?): String? = path?.let { TmdbConfig.IMAGE_BASE + it }

    /** 拼接背景图完整 URL */
    fun backdropUrl(path: String?): String? = path?.let { TmdbConfig.IMAGE_BASE_ORIGINAL + it }

    private fun fetch(url: String): String? = runCatching {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            resp.body?.string()
        }
    }.getOrNull()
}
