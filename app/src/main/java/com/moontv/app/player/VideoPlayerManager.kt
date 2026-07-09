package com.moontv.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.moontv.app.net.HttpClientFactory
import okhttp3.OkHttpClient

/**
 * 视频播放器管理器
 *
 * 对应原项目的 HLS.js + ArtPlayer 播放层。
 * 使用 Media3 ExoPlayer 的 HLS MediaSource 播放 m3u8 流。
 *
 * 特点：
 * - 通过自定义 OkHttpClient 作为数据源，复用 ECH / UA 配置
 * - 支持播放列表（多集切换）
 */
class VideoPlayerManager(
    private val context: Context,
    private val echEnabled: Boolean = false
) {
    private var player: ExoPlayer? = null

    /** 播放所用的 OkHttpClient（可复用 ECH 配置） */
    private val okHttpClient: OkHttpClient by lazy {
        // 播放流不注入日志拦截器，避免刷屏
        HttpClientFactory.create(echEnabled).newBuilder()
            .build()
    }

    /** 创建 ExoPlayer 实例 */
    fun create(): ExoPlayer {
        // 自定义数据源工厂：用我们的 OkHttpClient 替代默认的
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(
                com.moontv.app.config.MacCMSApiConfig.HEADERS["User-Agent"]!!
            )
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val hlsFactory = HlsMediaSource.Factory(dataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setBandwidthMeter(DefaultBandwidthMeter.getSingletonInstance(context))
            .build()
    }

    /**
     * 播放单个 m3u8 地址
     */
    fun play(player: ExoPlayer, url: String, startPositionMs: Long = 0L) {
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .build()
        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * 播放播放列表（多集）
     * @param urls 分集 m3u8 地址列表
     * @param startIndex 从第几集开始
     */
    fun playPlaylist(player: ExoPlayer, urls: List<String>, startIndex: Int = 0) {
        val mediaItems = urls.map { MediaItem.fromUri(it) }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.playWhenReady = true
    }

    fun release() {
        player?.release()
        player = null
    }
}
