package com.moontv.app.net.ech

import android.util.Base64
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * ECH 配置获取器。
 *
 * 职责：通过 DoH（DNS over HTTPS）查询目标域名的 HTTPS 记录（TYPE65），
 * 从中解析出 ECHConfigList，供 [EchProvider] 在握手前注入到 SSLSocket。
 *
 * 为什么用 DoH：ECH 的前提是 DNS 查询本身也要加密，否则中间设备仍能从明文 DNS
 * 中看到请求域名；同时 HTTPS 记录里携带的 ech 参数才是 ECH 的公钥配置来源。
 *
 * 实现：
 * - 直接向 Cloudflare DoH JSON 端点（https://cloudflare-dns.com/dns-query）
 *   发 GET 请求，accept: application/dns-json，查询参数 name=<域名>&type=65。
 * - 解析 Answer 数组，找到 type=65 的记录，其 data 字段为 base64 编码的 HTTPS RR RDATA。
 *   再从 RDATA（SVCB/HTTPS 线格式）中提取 ech 参数（SvcParamKey=5），即真正的 ECHConfigList。
 * - 内置 LRU 缓存（域名 -> ECH 配置，TTL 1 小时），避免重复查询。
 * - 任何失败返回 null，调用方降级为普通连接（GREASE 或明文 SNI）。
 */
object EchConfigProvider {

    private const val TAG = "EchConfigProvider"

    /** Cloudflare DoH JSON 端点 */
    private const val DOH_HOST = "cloudflare-dns.com"
    private const val DOH_PATH = "dns-query"

    /** HTTPS 记录类型（RFC 9460） */
    private const val TYPE_HTTPS = 65

    /** ech 的 SvcParamKey（RFC 9460 第 9 节） */
    private const val SVC_PARAM_KEY_ECH = 5

    /** 缓存 TTL：1 小时 */
    private const val CACHE_TTL_MS = 60L * 60L * 1000L

    /** 缓存容量（按域名计） */
    private const val CACHE_MAX_SIZE = 64

    /**
     * 专用 OkHttp 客户端：仅用于 DoH 查询。
     * 注意：这里绝不能复用被 ECH 包装的客户端，否则会触发拦截器递归 / 死锁。
     * Cloudflare DoH 端点本身用普通 TLS 即可访问，作为 ECH 的引导信道。
     */
    private val dohClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** JSON 解析器：忽略未知字段，容错 DoH 响应中的额外信息 */
    private val json: Json by lazy { Json { ignoreUnknownKeys = true; isLenient = true } }

    /** LRU 缓存：域名 -> ECH 配置（base64） */
    private val cache: LruCache<String, CachedConfig> = LruCache(CACHE_MAX_SIZE)

    /** DoH JSON 响应顶层结构 */
    @Serializable
    private data class DohResponse(
        @SerialName("Status") val status: Int = 0,
        @SerialName("Answer") val answer: List<DohRecord> = emptyList()
    )

    /** DoH JSON 响应中的单条记录 */
    @Serializable
    private data class DohRecord(
        @SerialName("name") val name: String = "",
        @SerialName("type") val type: Int = 0,
        @SerialName("TTL") val ttl: Int = 0,
        @SerialName("data") val data: String = ""
    )

    /** 缓存条目：ECH 配置 + 过期时间戳 */
    private class CachedConfig(val configBase64: String, val expiresAt: Long)

    /**
     * 查询并返回目标域名的 ECHConfigList（base64 字符串）。
     *
     * - 先查缓存，命中且未过期则直接返回。
     * - 否则走 DoH 查询 HTTPS 记录，解析出 ECHConfigList 后写入缓存。
     * - 查询失败返回 null。
     *
     * @param host 目标域名（不含 scheme/path），如 "crypto.cloudflare.com"
     * @return base64 编码的 ECHConfigList，或 null
     */
    suspend fun fetchEchConfig(host: String): String? = withContext(Dispatchers.IO) {
        // 1. 命中缓存则直接返回
        getCached(host)?.let { return@withContext it }

        var result: String? = null
        try {
            val url: HttpUrl = HttpUrl.Builder()
                .scheme("https")
                .host(DOH_HOST)
                .addPathSegment(DOH_PATH)
                .addQueryParameter("name", host)
                .addQueryParameter("type", TYPE_HTTPS.toString())
                .build()

            val request: Request = Request.Builder()
                .url(url)
                .header("accept", "application/dns-json")
                .header("user-agent", "MoonTV-ECH/1.0")
                .build()

            dohClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "DoH 查询失败 host=$host code=${response.code}")
                    return@use
                }
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    Log.w(TAG, "DoH 响应体为空 host=$host")
                    return@use
                }
                val doh = json.decodeFromString<DohResponse>(body)
                if (doh.status != 0) {
                    Log.w(TAG, "DoH 返回非 0 状态 host=$host status=${doh.status}")
                }
                // 找到第一条 type=65 的 HTTPS 记录
                val httpsRecord = doh.answer.firstOrNull { it.type == TYPE_HTTPS }
                if (httpsRecord == null) {
                    Log.w(TAG, "DoH 响应未含 HTTPS(65) 记录 host=$host")
                    return@use
                }
                val echBase64 = extractEchConfigList(httpsRecord.data)
                if (echBase64 == null) {
                    Log.w(TAG, "无法从 HTTPS 记录中提取 ECHConfigList host=$host")
                    return@use
                }
                // 写入缓存
                cache.put(host, CachedConfig(echBase64, System.currentTimeMillis() + CACHE_TTL_MS))
                result = echBase64
                Log.d(TAG, "已获取并缓存 ECH 配置 host=$host ttl=${httpsRecord.ttl}s")
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取 ECH 配置异常 host=$host：${e.message}")
        }
        result
    }

    /**
     * 同步读取缓存中的 ECH 配置（不发起网络请求）。
     * 供 [EchProvider] 在创建 SSLSocket 时同步读取——此时不能挂起。
     *
     * @return base64 编码的 ECHConfigList，过期或不存在返回 null
     */
    fun getCached(host: String): String? {
        val entry = cache.get(host) ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            cache.remove(host)
            return null
        }
        return entry.configBase64
    }

    /**
     * 从 HTTPS 记录的 data 字段中提取 ECHConfigList（base64）。
     *
     * 兼容两种 DoH data 表示：
     *  1. base64 编码的 RR RDATA（Cloudflare/Google JSON DoH 对二进制 RR 的标准做法）：
     *     需按 SVCB/HTTPS 线格式解析，取出 ech 参数（SvcParamKey=5）的值。
     *  2. 文本呈现格式（如 "1 . alpn=h2 ech=<base64>"）：直接用正则取 ech= 后的 base64。
     *
     * @param dataField DoH Answer[].data
     * @return ECHConfigList 的 base64，或 null
     */
    private fun extractEchConfigList(dataField: String): String? {
        // 方式 1：data 为 base64 编码的 RDATA
        val rrData = base64Decode(dataField)
        if (rrData != null) {
            val echBytes = extractEchFromSvcbRdata(rrData)
            if (echBytes != null) {
                return Base64.encodeToString(echBytes, Base64.NO_WRAP)
            }
        }
        // 方式 2：data 为文本呈现格式，形如 "1 . alpn=h2 ech=<base64>"
        val regex = Regex("""\bech=([A-Za-z0-9+/_=-]+)""")
        return regex.find(dataField)?.groupValues?.getOrNull(1)
    }

    /**
     * 从 HTTPS/SVCB RR 的 RDATA（线格式）中提取 ech 参数的值（即原始 ECHConfigList 字节）。
     *
     * 线格式（RFC 9460）：
     *   Priority(2) | SvcDomainName(DNS wire name) | SvcParam*
     *   SvcParam: SvcParamKey(2) | SvcParamLen(2) | SvcParamValue(SvcParamLen)
     *
     * @return ech 参数值（ECHConfigList 原始字节），或 null
     */
    private fun extractEchFromSvcbRdata(rrData: ByteArray): ByteArray? {
        if (rrData.size < 3) return null // 至少 Priority(2) + 根标签(1)
        var pos = 2 // 跳过 2 字节 Priority

        // 跳过 SvcDomainName（DNS wire 格式，以 0 长度标签 0x00 结尾）
        while (pos < rrData.size) {
            val labelLen = rrData[pos].toInt() and 0xFF
            pos++
            if (labelLen == 0) break // 根标签，域名结束
            pos += labelLen
            if (pos > rrData.size) return null
        }

        // 逐个解析 SvcParams 直到 RDATA 结束
        while (pos + 4 <= rrData.size) {
            val key = ((rrData[pos].toInt() and 0xFF) shl 8) or (rrData[pos + 1].toInt() and 0xFF)
            val valueLen = ((rrData[pos + 2].toInt() and 0xFF) shl 8) or (rrData[pos + 3].toInt() and 0xFF)
            pos += 4
            if (pos + valueLen > rrData.size) return null
            if (key == SVC_PARAM_KEY_ECH) {
                // 命中 ech 参数，其值即为 ECHConfigList 原始字节
                return rrData.copyOfRange(pos, pos + valueLen)
            }
            pos += valueLen
        }
        return null
    }

    /** 容错的 base64 解码（兼容标准与 URL-safe 编码） */
    private fun base64Decode(s: String): ByteArray? = try {
        val normalized = s.replace('-', '+').replace('_', '/')
        Base64.decode(normalized, Base64.DEFAULT)
    } catch (t: Throwable) {
        null
    }
}
