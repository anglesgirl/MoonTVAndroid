package com.moontv.app.data.model

import kotlinx.serialization.Serializable

/**
 * 视频搜索结果（对应原项目 src/lib/types.ts 的 SearchResult）
 * 各字段含义与 MoonTVPlus 保持一致
 */
@Serializable
data class SearchResult(
    val id: String = "",
    val title: String = "",
    val poster: String = "",
    /** 分集 m3u8 播放地址列表 */
    val episodes: List<String> = emptyList(),
    /** 分集标题列表，与 episodes 一一对应 */
    val episodesTitles: List<String> = emptyList(),
    /** 来源 key（如 dyttzy） */
    val source: String = "",
    /** 来源显示名 */
    val sourceName: String = "",
    val vodClass: String = "",
    val year: String = "",
    val desc: String = "",
    val typeName: String = "",
    val doubanId: Long = 0,
    val vodRemarks: String? = null,
    val vodTotal: Int? = null,
    /** 是否需要走 m3u8 代理模式 */
    val proxyMode: Boolean = false
)
