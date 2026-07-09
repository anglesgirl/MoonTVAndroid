package com.moontv.app.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import com.moontv.app.net.ech.EchProvider
import com.moontv.app.player.VideoPlayerManager

/**
 * 播放页：使用 Media3 ExoPlayer 播放分集 m3u8 列表
 *
 * 流程：
 * 1. 进入页面时调用 [MainViewModel.detail] 获取分集地址列表
 * 2. 由 [VideoPlayerManager] 创建 [androidx.media3.exoplayer.ExoPlayer]
 * 3. 用 [AndroidView] 嵌入 [PlayerView]，并调用 playPlaylist 从指定分集开始播放
 * 4. 横屏全屏沉浸式播放
 * 5. 离开页面时通过 [DisposableEffect] 释放播放器并恢复系统 UI / 方向
 */
@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    source: String,
    id: String,
    episodeIndex: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // 详情（含分集列表）
    var episodes by remember { mutableStateOf<List<String>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    // 播放器管理器：复用 ECH 可用性
    val manager = remember { VideoPlayerManager(context, EchProvider.isEchAvailable()) }
    val player = remember { manager.create() }

    // 离开页面时释放播放器，恢复方向与系统栏
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    // 横屏全屏沉浸式：进入时切横屏并隐藏系统栏，离开时恢复
    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val originalOrientation = activity?.requestedOrientation

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            activity?.requestedOrientation =
                originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, true) }
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 拉取详情拿到分集列表
    LaunchedEffect(source, id) {
        loading = true
        failed = false
        val detail = viewModel.detail(source, id)
        val eps = detail?.episodes
        episodes = eps
        failed = eps.isNullOrEmpty()
        loading = false
    }

    // 详情就绪后开始播放指定分集
    LaunchedEffect(episodes) {
        val eps = episodes
        if (!eps.isNullOrEmpty()) {
            manager.playPlaylist(player, eps, episodeIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        } else if (failed || episodes.isNullOrEmpty()) {
            val hint = if (source == "tmdb_search") {
                "无法获取播放地址\n\n未在采集源中找到可播放内容，请确认已配置苹果 CMS 采集源"
            } else {
                "无法获取播放地址，请返回重试"
            }
            Text(
                text = hint,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        } else {
            // 嵌入 Media3 PlayerView
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 返回按钮（叠加在左上角）
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White
            )
        }
    }
}
