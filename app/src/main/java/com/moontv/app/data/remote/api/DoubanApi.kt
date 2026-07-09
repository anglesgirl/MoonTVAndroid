package com.moontv.app.data.remote.api

import com.moontv.app.config.DoubanConfig
import com.moontv.app.data.model.DoubanItem
import com.moontv.app.data.model.DoubanResult
import com.moontv.app.data.model.DoubanSearchResult
import com.moontv.app.net.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * 豆瓣可选数据客户端
 *
 * 对应原项目 src/lib/douban.client.ts，仅当用户配置了豆瓣代理时启用。
 * 默认走 cmliussss CDN（与原项目一致），规避直连被墙问题。
 *
 * 注意：豆瓣官方 API 已关闭，此模块访问的是豆瓣内部接口，存在不稳定风险，
 * 推荐优先使用 TMDB。
 */
class DoubanApi(
    private val proxyUrl: String = DoubanConfig.DEFAULT_PROXY,
    private val echEnabled: Boolean = false,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {

    private val client by lazy { HttpClientFactory.createForDouban(echEnabled) }

    /** 豆瓣列表接口原始返回结构 */
    @Serializable
    private data class DoubanListRawResponse(
        val total: Int = 0,
        val subjects: List<DoubanListSubject> = emptyList()
    )

    @Serializable
    private data class DoubanListSubject(
        val id: String = "",
        val title: String = "",
        val cover: String = "",
        val rate: String = "",
        @SerialName("card_subtitle") val cardSubtitle: String = ""
    )

    /**
     * 搜索豆瓣影视（对应 /api/douban/search，走 subject_suggest 接口）
     */
    suspend fun search(query: String): List<DoubanSearchResult> = withContext(Dispatchers.IO) {
        val target = "${proxyUrl}${DoubanConfig.SEARCH_PATH}${query.encodeURL()}"
        val resp = fetch(target) ?: return@withContext emptyList()
        runCatching {
            json.decodeFromString<List<DoubanSearchResult>>(resp)
        }.getOrDefault(emptyList())
    }

    /**
     * 获取分类列表（对应 fetchDoubanList，走 j/search_subjects）
     * @param type "movie" 或 "tv"
     * @param tag 标签，如 热门、最新、经典
     */
    suspend fun list(type: String, tag: String, limit: Int = 20, start: Int = 0): DoubanResult =
        withContext(Dispatchers.IO) {
            val target = "${proxyUrl}${DoubanConfig.LIST_PATH
                .replace("{type}", type)
                .replace("{tag}", tag.encodeURL())
                .replace("{limit}", limit.toString())
                .replace("{start}", start.toString())}"
            val resp = fetch(target) ?: return@withContext DoubanResult(
                code = 500, message = "请求失败"
            )
            runCatching {
                val raw = json.decodeFromString<DoubanListRawResponse>(resp)
                val list = raw.subjects.map { sub ->
                    DoubanItem(
                        id = sub.id,
                        title = sub.title,
                        poster = sub.cover,
                        rate = sub.rate,
                        year = Regex("(\\d{4})").find(sub.cardSubtitle)?.value ?: ""
                    )
                }
                DoubanResult(code = 200, message = "获取成功", list = list)
            }.getOrDefault(DoubanResult(code = 500, message = "解析失败"))
        }

    private fun fetch(url: String): String? = runCatching {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            resp.body?.string()
        }
    }.getOrNull()

    private fun String.encodeURL(): String =
        java.net.URLEncoder.encode(this, "UTF-8")
}
