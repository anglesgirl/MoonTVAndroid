package com.moontv.app.net.ech

import android.util.Log
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * ECH 预取拦截器（OkHttp 应用拦截器）。
 *
 * 由于 [EchProvider] 注入的 SSLSocketFactory 是全局生效的，本拦截器的核心作用是：
 * 在真正建立 TLS 连接之前，为目标域名**预取并缓存** ECH 配置，使得稍后
 * [EchProvider.EchSSLSocketFactory] 在创建 SSLSocket 时能同步命中缓存、把
 * ECHConfigList 注入进去。
 *
 * 为什么必须是"应用拦截器"而非"网络拦截器"：
 * OkHttp 的网络拦截器运行在 ConnectInterceptor（含 TLS 握手）之后，那时 SNI
 * 已经发出去了。应用拦截器运行在整个链路最前端，能保证预取在连接之前完成。
 *
 * 副作用：
 * - 将"需要 ECH 的域名"记录到 [hosts] 集合，便于诊断与统计。
 * - 仅当 [EchProvider.isEchAvailable] 为真时才预取；缓存命中时跳过网络请求。
 * - 使用 runBlocking 阻塞当前调用线程以同步预取——故本客户端应在后台线程使用，
 *   不要在主线程发起网络请求（这与 App 通用网络规范一致）。
 *
 * @see EchProvider.applyTo
 */
class EchInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        if (EchProvider.isEchAvailable() && host.isNotEmpty()) {
            // 记录该域名需要 ECH
            echHosts.add(host)
            // 缓存未命中时同步预取，确保后续 TLS 握手时缓存已就绪
            if (EchConfigProvider.getCached(host) == null) {
                runBlocking {
                    EchConfigProvider.fetchEchConfig(host)
                }
            }
        }
        return chain.proceed(chain.request())
    }

    companion object {
        private const val TAG = "EchInterceptor"

        /** 线程安全的"需要 ECH 的域名"集合（基于 ConcurrentHashMap，弱一致迭代） */
        private val echHosts: MutableSet<String> =
            Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

        /** 已记录的需要 ECH 的域名（只读视图，供诊断/统计使用） */
        val hosts: Set<String>
            get() = echHosts

        /** 清空已记录的域名集合（主要用于测试） */
        fun reset() {
            echHosts.clear()
            Log.d(TAG, "已清空 ECH 域名记录")
        }
    }
}
