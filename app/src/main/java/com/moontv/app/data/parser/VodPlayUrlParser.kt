package com.moontv.app.data.parser

/**
 * 视频播放地址解析器
 *
 * 对应原项目 src/lib/downstream.ts 的 vod_play_url 解析逻辑。
 *
 * 苹果CMS的 vod_play_url 格式：
 *   "播放组1$$$播放组2"
 *   每个播放组内: "第1集$链接1#第2集$链接2#..."
 *   链接通常以 .m3u8 结尾
 *
 * 解析策略：选择分集数最多的播放组
 */
object VodPlayUrlParser {

    /** 从内容中兜底提取 m3u8 链接（对应原项目 M3U8_PATTERN） */
    private val M3U8_PATTERN = Regex("""(https?://[^\s"'<>]+?\.m3u8)""")

    /**
     * 解析 vod_play_url，返回(分集标题列表, 分集链接列表)
     * 选择分集最多的播放组
     */
    fun parse(vodPlayUrl: String): Pair<List<String>, List<String>> {
        if (vodPlayUrl.isBlank()) return emptyList<String>() to emptyList<String>()

        var bestTitles: List<String> = emptyList()
        var bestEpisodes: List<String> = emptyList()

        // 1. 先用 $$$ 分割多个播放组
        vodPlayUrl.split("$$$").forEach { group ->
            val matchTitles = mutableListOf<String>()
            val matchEpisodes = mutableListOf<String>()

            // 2. 分集之间用 # 分割，标题和链接用 $ 分割
            group.split("#").forEach { titleUrl ->
                val parts = titleUrl.split("$")
                if (parts.size == 2 && parts[1].endsWith(".m3u8")) {
                    matchTitles.add(parts[0])
                    matchEpisodes.add(parts[1])
                }
            }

            // 3. 选择分集最多的组
            if (matchEpisodes.size > bestEpisodes.size) {
                bestEpisodes = matchEpisodes
                bestTitles = matchTitles
            }
        }

        return bestTitles to bestEpisodes
    }

    /**
     * 从 vod_content 兜底提取 m3u8 链接
     * 对应原项目：matches = videoDetail.vod_content.match(M3U8_PATTERN)
     */
    fun extractFromContent(vodContent: String): List<String> =
        M3U8_PATTERN.findAll(vodContent)
            .map { it.value.removePrefix("$") }
            .toList()
}
