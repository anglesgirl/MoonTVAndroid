package com.moontv.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 苹果 CMS V10 API 响应模型
 * 接口示例: /api.php/provide/vod/?ac=detail&ids=123
 */
@Serializable
data class MacCMSResponse(
    val code: Int = 0,
    val msg: String = "",
    val page: Int = 1,
    val pagecount: Int = 1,
    val limit: String = "",
    val total: String = "",
    val list: List<VodItem> = emptyList()
)

@Serializable
data class VodItem(
    @SerialName("vod_id") val vodId: Long = 0,
    @SerialName("vod_name") val vodName: String = "",
    @SerialName("vod_pic") val vodPic: String = "",
    @SerialName("vod_remarks") val vodRemarks: String? = null,
    @SerialName("vod_play_url") val vodPlayUrl: String = "",
    @SerialName("vod_class") val vodClass: String = "",
    @SerialName("vod_year") val vodYear: String = "",
    @SerialName("vod_content") val vodContent: String = "",
    @SerialName("vod_douban_id") val vodDoubanId: Long = 0,
    @SerialName("type_name") val typeName: String = "",
    @SerialName("vod_total") val vodTotal: Int? = null
)
