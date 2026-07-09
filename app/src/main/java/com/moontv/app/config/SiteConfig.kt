package com.moontv.app.config

import kotlinx.serialization.Serializable

/**
 * 单个采集源配置（对应原项目 src/lib/config.ts 的 ApiSite）
 */
@Serializable
data class ApiSite(
    val key: String,
    val name: String,
    /** 资源站提供的 vod JSON API 根地址，如 http://xxx.com/api.php/provide/vod */
    val api: String,
    /** 可选：部分站点需网页详情根 URL 用于爬取 */
    val detail: String = "",
    /** 是否需 m3u8 代理模式 */
    val proxyMode: Boolean = false
)

/**
 * 站点全局配置（对应原项目 INIT_CONFIG / 配置文件）
 * 启动为空壳，由用户在设置页填写后持久化
 */
@Serializable
data class SiteConfig(
    val apiSite: Map<String, ApiSite> = emptyMap(),
    val tmdbApiKey: String = "",
    /** 可选：豆瓣数据代理 URL，留空则走 TMDB */
    val doubanProxyUrl: String = "",
    val cacheTime: Long = 7200L
) {
    companion object {
        /** 空壳默认配置 */
        val EMPTY = SiteConfig()
    }
}
