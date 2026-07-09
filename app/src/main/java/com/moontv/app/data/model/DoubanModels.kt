package com.moontv.app.data.model

import kotlinx.serialization.Serializable

/**
 * 豆瓣可选模块的响应模型（对应 douban.client.ts 的 DoubanResult/DoubanItem）
 * 仅在用户自行配置代理时启用，默认走 TMDB
 */
@Serializable
data class DoubanResult(
    val code: Int = 200,
    val message: String = "",
    val list: List<DoubanItem> = emptyList()
)

@Serializable
data class DoubanItem(
    val id: String = "",
    val title: String = "",
    val poster: String = "",
    val rate: String = "",
    val year: String = ""
)

@Serializable
data class DoubanSearchResult(
    val id: String = "",
    val title: String = "",
    val year: String = "",
    val type: String? = null,
    val sub_title: String? = null,
    val episode: String? = null,
    val img: String? = null
)
