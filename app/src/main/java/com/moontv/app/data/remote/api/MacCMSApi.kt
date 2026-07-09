package com.moontv.app.data.remote.api

import com.moontv.app.config.ApiSite
import com.moontv.app.config.MacCMSApiConfig
import com.moontv.app.data.model.MacCMSResponse
import com.moontv.app.data.model.SearchResult
import com.moontv.app.data.model.VodItem
import com.moontv.app.data.parser.VodPlayUrlParser
import com.moontv.app.net.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * 苹果 CMS V10 API 客户端
 *
 * 对应原项目 src/lib/downstream.ts 的 searchFromApi / getDetailFromApiV2。
 * 直接用 OkHttp + 正则解析，保持与原项目一致的解析逻辑。
 */
class MacCMSApi(
    private val echEnabled: Boolean = false,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {

    private val client by lazy { HttpClientFactory.create(echEnabled) }

    /**
     * 搜索视频（对应 searchFromApi）
     * 支持多页聚合：先取第1页拿 pagecount，再并发拉后续页
     */
    suspend fun search(apiSite: ApiSite, query: String, maxPages: Int = 5): List<SearchResult> =
        coroutineScope {
            val apiBase = apiSite.api
            val firstUrl = "$apiBase${MacCMSApiConfig.SEARCH_PATH}${query.encodeURL()}"

            val firstResp = fetch(firstUrl) ?: return@coroutineScope emptyList()
            val firstData = runCatching { json.decodeFromString<MacCMSResponse>(firstResp) }.getOrNull()
                ?: return@coroutineScope emptyList()

            if (firstData.list.isEmpty()) return@coroutineScope emptyList()

            val firstResults = firstData.list.map { mapItem(it, apiSite) }
                .filter { it.episodes.isNotEmpty() }

            val pageCount = firstData.pagecount.coerceAtLeast(1)
            val pagesToFetch = (pageCount - 1).coerceAtMost(maxPages - 1)

            if (pagesToFetch <= 0) return@coroutineScope firstResults

            // 并发拉取后续页
            val additional = (2..pagesToFetch + 1).map { page ->
                async(Dispatchers.IO) {
                    val pageUrl = "$apiBase${MacCMSApiConfig.PAGE_PATH
                        .replace("{query}", query.encodeURL())
                        .replace("{page}", page.toString())}"
                    fetch(pageUrl)
                }
            }.awaitAll()

            val moreResults = additional.mapNotNull { resp ->
                resp?.let {
                    runCatching { json.decodeFromString<MacCMSResponse>(it) }.getOrNull()
                }?.list?.map { mapItem(it, apiSite) }?.filter { it.episodes.isNotEmpty() }
            }.flatten()

            firstResults + moreResults
        }

    /**
     * 获取视频详情（对应 getDetailFromApiV2）
     */
    suspend fun detail(apiSite: ApiSite, id: String): SearchResult? = withContext(Dispatchers.IO) {
        val url = "${apiSite.api}${MacCMSApiConfig.DETAIL_PATH}$id"
        val resp = fetch(url) ?: return@withContext null
        val data = runCatching { json.decodeFromString<MacCMSResponse>(resp) }.getOrNull()
            ?: return@withContext null
        val item = data.list.firstOrNull() ?: return@withContext null
        mapItem(item, apiSite).copy(id = id)
    }

    /** 将 VodItem 映射为 SearchResult，解析 vod_play_url */
    private fun mapItem(item: VodItem, apiSite: ApiSite): SearchResult {
        val (titles, episodes) = VodPlayUrlParser.parse(item.vodPlayUrl)

        // 若播放源为空，兜底从 content 提取 m3u8
        val finalEpisodes = episodes.ifEmpty {
            VodPlayUrlParser.extractFromContent(item.vodContent)
        }

        return SearchResult(
            id = item.vodId.toString(),
            title = item.vodName.trim().replace(Regex("\\s+"), " "),
            poster = item.vodPic,
            episodes = finalEpisodes,
            episodesTitles = titles,
            source = apiSite.key,
            sourceName = apiSite.name,
            vodClass = item.vodClass,
            year = Regex("\\d{4}").find(item.vodYear)?.value ?: "unknown",
            desc = cleanHtmlTags(item.vodContent),
            typeName = item.typeName,
            doubanId = item.vodDoubanId,
            vodRemarks = item.vodRemarks,
            vodTotal = item.vodTotal,
            proxyMode = apiSite.proxyMode
        )
    }

    private fun cleanHtmlTags(text: String): String =
        text.replace(Regex("<[^>]+>"), "").trim()

    /** 执行请求，失败返回 null */
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
