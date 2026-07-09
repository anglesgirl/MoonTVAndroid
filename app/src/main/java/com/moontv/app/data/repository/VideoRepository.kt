package com.moontv.app.data.repository

import com.moontv.app.config.ApiSite
import com.moontv.app.config.SiteConfig
import com.moontv.app.data.model.SearchResult
import com.moontv.app.data.model.TmdbItem
import com.moontv.app.data.remote.api.DoubanApi
import com.moontv.app.data.remote.api.MacCMSApi
import com.moontv.app.data.remote.api.TmdbApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 视频仓库：统筹苹果CMS多源搜索 + TMDB 元数据 + 可选豆瓣
 *
 * 对应原项目的 search / detail / 豆瓣推荐聚合逻辑。
 *
 * @param echEnabled 是否为网络客户端启用 ECH
 */
class VideoRepository(
    private val config: SiteConfig,
    private val echEnabled: Boolean = false
) {
    private val macCmsApi = MacCMSApi(echEnabled)
    private val tmdbApi = TmdbApi(config.tmdbApiKey, echEnabled)
    private val doubanApi = DoubanApi(config.doubanProxyUrl, echEnabled)

    /**
     * 多源聚合搜索（对应原项目多源并发搜索）
     * 并发请求所有采集源，合并结果
     */
    suspend fun searchAll(query: String): List<SearchResult> = coroutineScope {
        if (config.apiSite.isEmpty()) return@coroutineScope emptyList()
        config.apiSite.values.map { site ->
            async { runCatching { macCmsApi.search(site, query) }.getOrDefault(emptyList()) }
        }.awaitAll().flatten()
    }

    /** 单源详情 */
    suspend fun detail(source: String, id: String): SearchResult? {
        val site = config.apiSite[source] ?: return null
        return runCatching { macCmsApi.detail(site, id) }.getOrNull()
    }

    /**
     * 用标题跨源搜索，返回第一个有播放地址的结果
     * 用于从 TMDB 元数据桥接到苹果 CMS 播放源
     */
    suspend fun searchFirstPlayable(title: String): SearchResult? {
        val results = searchAll(title)
        return results.firstOrNull { it.episodes.isNotEmpty() }
    }

    /**
     * 用标题跨源搜索，返回所有有播放地址的结果
     * 用于详情页展示多个可用播放源
     */
    suspend fun searchAllPlayable(title: String): List<SearchResult> {
        val results = searchAll(title)
        return results.filter { it.episodes.isNotEmpty() }
    }

    /** TMDB 搜索（元数据补充） */
    suspend fun searchMetadata(query: String): List<TmdbItem> =
        runCatching { tmdbApi.search(query) }.getOrDefault(emptyList())

    /** TMDB 热门趋势（首页推荐） */
    suspend fun trending(): List<TmdbItem> =
        runCatching { tmdbApi.trending() }.getOrDefault(emptyList())

    /** 豆瓣搜索（可选） */
    suspend fun searchDouban(query: String) =
        runCatching { doubanApi.search(query) }.getOrDefault(emptyList())

    /** 当前是否配置了豆瓣代理 */
    val doubanEnabled: Boolean get() = config.doubanProxyUrl.isNotBlank()
}
