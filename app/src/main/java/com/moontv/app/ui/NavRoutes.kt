package com.moontv.app.ui

/**
 * 导航路由常量定义
 *
 * 统一管理各页面的路由路径与参数，避免硬编码字符串散落各处。
 * 由于 Compose Navigation 不便传递 Parcelable，统一使用字符串参数：
 * - source：采集源 key（如 dyttzy）
 * - id：视频在对应源中的 ID
 * - episodeIndex：分集序号（从 0 开始，整型）
 *
 * 以 [companion object] 暴露常量与路由拼接方法，调用形如 `NavRoutes.HOME`、
 * `NavRoutes.detail(source, id)`、`NavRoutes.Args.SOURCE`。
 */
class NavRoutes {

    companion object {
        /** 首页（搜索 + 热门） */
        const val HOME = "home"

        /** 详情页：携带 source 与 id */
        const val DETAIL = "detail/{source}/{id}"

        /** 播放页：携带 source、id 与分集序号 */
        const val PLAYER = "player/{source}/{id}/{episodeIndex}"

        /** 设置页 */
        const val SETTINGS = "settings"

        /** 拼接详情页具体路由 */
        fun detail(source: String, id: String): String =
            "detail/$source/$id"

        /** 拼接播放页具体路由 */
        fun player(source: String, id: String, episodeIndex: Int): String =
            "player/$source/$id/$episodeIndex"
    }

    /**
     * 路由参数名，供 NavArgument / NavBackStackEntry 读取
     */
    object Args {
        const val SOURCE = "source"
        const val ID = "id"
        const val EPISODE_INDEX = "episodeIndex"
    }
}
