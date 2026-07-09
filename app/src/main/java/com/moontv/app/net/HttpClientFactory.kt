package com.moontv.app.net

import com.moontv.app.config.MacCMSApiConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * 网络客户端工厂
 *
 * 提供两类客户端：
 * 1. 通用客户端：带超时、UA、日志，用于 TMDB / 苹果CMS
 * 2. 豆瓣客户端：额外注入豆瓣 Referer 头（对应 douban.ts 的 headers）
 *
 * 若 ECH 已启用，会注入 ECH 配置的 SSLSocketFactory（见 EchProvider）
 */
object HttpClientFactory {

    /** 通用日志拦截器 */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    /** 通用请求头拦截器：注入 UA */
    private val headerInterceptor = okhttp3.Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("User-Agent", MacCMSApiConfig.HEADERS["User-Agent"]!!)
            .header("Accept", MacCMSApiConfig.HEADERS["Accept"]!!)
            .build()
        chain.proceed(req)
    }

    /** 豆瓣请求头拦截器：注入 Referer / Origin（对应 douban.ts headers） */
    val doubanHeaderInterceptor = okhttp3.Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("User-Agent", MacCMSApiConfig.HEADERS["User-Agent"]!!)
            .header("Referer", "https://movie.douban.com/")
            .header("Accept", "application/json, text/plain, */*")
            .build()
        chain.proceed(req)
    }

    /** 通用客户端（苹果CMS / TMDB） */
    fun create(echEnabled: Boolean = false): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(headerInterceptor)
            .addInterceptor(loggingInterceptor)

        // 若 ECH 启用，注入 DEfO Conscrypt 的 SSLSocketFactory
        if (echEnabled) {
            com.moontv.app.net.ech.EchProvider.applyTo(builder)
        }
        return builder.build()
    }

    /** 豆瓣专用客户端（带 Referer，可选 ECH） */
    fun createForDouban(echEnabled: Boolean = false): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(doubanHeaderInterceptor)
            .addInterceptor(loggingInterceptor)
        if (echEnabled) {
            com.moontv.app.net.ech.EchProvider.applyTo(builder)
        }
        return builder.build()
    }
}
