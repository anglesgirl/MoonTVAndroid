package com.moontv.app.data.parser

/**
 * 视频文件名解析（对应原项目 src/lib/video-parser.ts）
 * 用于从文件名中提取集数/季信息
 */
data class ParsedVideoInfo(
    val episode: Int? = null,
    val season: Int? = null,
    val title: String? = null,
    val isOVA: Boolean = false
)

object VideoNameParser {

    // 按优先级排列的正则模式
    private val patterns = listOf(
        Triple(Regex("""(?i)OVA\s*(\d+(?:\.\d+)?)"""), true, false),
        // S01E01
        Triple(Regex("""[Ss](\d+)[Ee](\d{1,4}(?:\.\d+)?)"""), false, true),
        // [01], (01)
        Triple(Regex("""[[(](\d+(?:\.\d+)?)[)\]]"""), false, false),
        // E01
        Triple(Regex("""[Ee](\d+(?:\.\d+)?)"""), false, false),
        // 第01集 / 第01话
        Triple(Regex("""第(\d+(?:\.\d+)?)[集话]"""), false, false),
        // _01- / -01-
        Triple(Regex("""[_-](\d+(?:\.\d+)?)[_-]"""), false, false),
        // 01.mp4 开头纯数字
        Triple(Regex("""^(\d+(?:\.\d+)?)[^\d.]"""), false, false)
    )

    fun parse(fileName: String): ParsedVideoInfo {
        for ((pattern, isOVA, extractSeason) in patterns) {
            val match = pattern.find(fileName) ?: continue
            when {
                extractSeason -> {
                    val season = match.groupValues.getOrNull(1)?.toIntOrNull() ?: continue
                    val episode = match.groupValues.getOrNull(2)?.toFloatOrNull() ?: continue
                    if (season in 1..99 && episode in 1.0..9999.0) {
                        return ParsedVideoInfo(episode = episode.toInt(), season = season)
                    }
                }
                isOVA -> {
                    val ep = match.groupValues.getOrNull(1)?.toFloatOrNull() ?: continue
                    if (ep in 1.0..9999.0) return ParsedVideoInfo(episode = ep.toInt(), isOVA = true)
                }
                else -> {
                    val ep = match.groupValues.getOrNull(1)?.toFloatOrNull() ?: continue
                    if (ep in 1.0..9999.0) return ParsedVideoInfo(episode = ep.toInt())
                }
            }
        }
        return ParsedVideoInfo()
    }
}
