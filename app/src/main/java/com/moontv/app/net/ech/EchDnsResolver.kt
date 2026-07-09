package com.moontv.app.net.ech

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
 * 配合 ECH 的 DoH 解析器。
 *
 * ECH 要求 DNS 也走加密通道——否则中间设备仍能从明文 DNS 查询中看到目标域名
 * （SNI 虽被加密，但 DNS 查询明文泄露同等隐私）。本类通过 Cloudflare DoH
 * （type=A）解析域名，返回 IPv4 列表，供需要自定义连接（绕过系统 DNS）的场景使用。
 *
 * 实现：
 * - GET https://cloudflare-dns.com/dns-query?name=<域名>&type=1，accept: application/dns-json
 * - 解析 Answer 数组中 type=1 的记录，data 即为 IPv4 点分字符串。
 * - 内置 LRU 缓存（TTL 5 分钟），避免重复查询。
 */
object EchDnsResolver {

    private const val TAG = "EchDnsResolver"

    private const val DOH_HOST = "cloudflare-dns.com"
    private const val DOH_PATH = "dns-query"

    /** A 记录类型 */
    private const val TYPE_A = 1

    /** 缓存 TTL：5 分钟（IP 变更比 ECH 配置更频繁，故更短） */
    private const val CACHE_TTL_MS = 5L * 60L * 1000L

    private const val CACHE_MAX_SIZE = 64

    /** 专用 DoH 客户端（普通 TLS，不接入 ECH 包装，避免递归） */
    private val dohClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val json: Json by lazy { Json { ignoreUnknownKeys = true; isLenient = true } }

    /** LRU 缓存：域名 -> IP 列表 */
    private val cache: LruCache<String, CachedIps> = LruCache(CACHE_MAX_SIZE)

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

    /** 缓存条目：IP 列表 + 过期时间戳 */
    private class CachedIps(val ips: List<String>, val expiresAt: Long)

    /**
     * 通过 DoH 解析目标域名的 A 记录，返回 IPv4 列表。
     *
     * - 先查缓存，命中且未过期则直接返回。
     * - 否则走 DoH 查询 type=A，解析出 IPv4 列表后写入缓存。
     * - 解析失败返回空列表，调用方应降级为系统 DNS 或放弃。
     *
     * @param host 目标域名
     * @return IPv4 字符串列表（可能为空）
     */
    suspend fun resolve(host: String): List<String> = withContext(Dispatchers.IO) {
        // 1. 命中缓存则直接返回
        getCached(host)?.let { return@withContext it }

        var result: List<String> = emptyList()
        try {
            val url: HttpUrl = HttpUrl.Builder()
                .scheme("https")
                .host(DOH_HOST)
                .addPathSegment(DOH_PATH)
                .addQueryParameter("name", host)
                .addQueryParameter("type", TYPE_A.toString())
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
                // 取所有 type=1 的 A 记录，data 即为 IPv4 点分字符串
                val ips = doh.answer
                    .filter { it.type == TYPE_A }
                    .map { it.data.trim() }
                    .filter { it.isNotEmpty() && isValidIPv4(it) }
                    .distinct()

                if (ips.isEmpty()) {
                    Log.w(TAG, "DoH 响应未含 A(1) 记录 host=$host")
                    return@use
                }
                cache.put(host, CachedIps(ips, System.currentTimeMillis() + CACHE_TTL_MS))
                result = ips
                Log.d(TAG, "已解析并缓存 A 记录 host=$host ips=$ips")
            }
        } catch (e: Exception) {
            Log.w(TAG, "DoH 解析异常 host=$host：${e.message}")
        }
        result
    }

    /**
     * 同步读取缓存中的 IP 列表（不发起网络请求）。
     *
     * @return 已缓存的 IPv4 列表，过期或不存在返回 null
     */
    fun getCached(host: String): List<String>? {
        val entry = cache.get(host) ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            cache.remove(host)
            return null
        }
        return entry.ips
    }

    /** 简单校验是否为合法 IPv4 点分格式 */
    private fun isValidIPv4(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            val n = part.toIntOrNull() ?: return false
            n in 0..255
        }
    }
}
