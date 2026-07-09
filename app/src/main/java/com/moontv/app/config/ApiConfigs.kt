package com.moontv.app.config

/**
 * 苹果 CMS V10 API 路径常量（对应原项目 src/lib/config.ts 的 API_CONFIG）
 *
 * 标准苹果CMS接口：
 *   列表: /api.php/provide/vod/?ac=list&wd=关键词
 *   详情: /api.php/provide/vod/?ac=detail&ids=视频ID
 *   分页: &pg=页码
 */
object MacCMSApiConfig {
    /** 搜索路径，拼在 api 根地址后 */
    const val SEARCH_PATH = "?ac=list&wd="
    /** 分页搜索模板，{query}/{page} 会被替换 */
    const val PAGE_PATH = "?ac=list&wd={query}&pg={page}"
    /** 详情路径 */
    const val DETAIL_PATH = "?ac=detail&ids="

    val HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
        "Accept" to "application/json, text/plain, */*"
    )
}

/**
 * TMDB API 配置
 */
object TmdbConfig {
    const val BASE_URL = "https://api.themoviedb.org/3/"
    const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
    const val IMAGE_BASE_ORIGINAL = "https://image.tmdb.org/t/p/original"
}

/**
 * 豆瓣数据源（对应 douban.client.ts）
 * 默认走 cmliussss CDN（与原项目一致），可被用户配置覆盖
 */
object DoubanConfig {
    const val DEFAULT_PROXY = "https://m.douban.cmliussss.net/"
    /** 搜索建议接口 */
    const val SEARCH_PATH = "j/subject_suggest?q="
    /** 热门/分类 */
    const val LIST_PATH = "j/search_subjects?type={type}&tag={tag}&sort=recommend&page_limit={limit}&page_start={start}"
}
