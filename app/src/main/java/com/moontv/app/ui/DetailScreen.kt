package com.moontv.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moontv.app.data.model.SearchResult

/**
 * 详情页：展示视频信息与分集列表
 *
 * 进入页面时通过 [LaunchedEffect] 调用 [MainViewModel.detail] 拉取详情，
 * 拿到 [SearchResult] 后渲染海报、标题、年份、简介与分集列表。
 * 点击分集通过 [onPlayEpisode] 进入播放页并传入分集序号。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: MainViewModel,
    source: String,
    id: String,
    onPlayEpisode: (episodeIndex: Int) -> Unit,
    onBack: () -> Unit
) {
    // 详情数据与加载状态（局部 state）
    var detail by remember { mutableStateOf<SearchResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    // source/id 变化时重新加载
    LaunchedEffect(source, id) {
        loading = true
        failed = false
        val result = viewModel.detail(source, id)
        detail = result
        failed = result == null
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = detail?.title ?: "详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                failed || detail == null -> {
                    Text(
                        text = "加载详情失败，请检查采集源配置或稍后重试",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }
                else -> {
                    DetailContent(
                        detail = detail!!,
                        onPlayEpisode = onPlayEpisode
                    )
                }
            }
        }
    }
}

/**
 * 详情主体：单个 [LazyColumn]，header 区 + 分集列表
 */
@Composable
private fun DetailContent(
    detail: SearchResult,
    onPlayEpisode: (episodeIndex: Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 顶部信息区
        item { DetailHeader(detail) }

        // 简介区
        if (detail.desc.isNotBlank()) {
            item {
                Text(
                    text = detail.desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 分集标题
        item {
            Text(
                text = "分集（${detail.episodes.size}）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // 分集列表
        itemsIndexed(detail.episodes) { index, _ ->
            EpisodeItem(
                index = index,
                title = detail.episodesTitles.getOrNull(index),
                onClick = { onPlayEpisode(index) }
            )
        }
    }
}

/**
 * 顶部信息：海报 + 标题/年份/来源
 */
@Composable
private fun DetailHeader(detail: SearchResult) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // 海报
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            if (detail.poster.isNotBlank()) {
                AsyncImage(
                    model = detail.poster,
                    contentDescription = detail.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxSize()
                ) {}
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = detail.title.ifBlank { "未知标题" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (detail.year.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = detail.year,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (detail.sourceName.isNotBlank() || detail.source.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = detail.sourceName.ifBlank { detail.source },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (detail.typeName.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = detail.typeName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 单集条目
 */
@Composable
private fun EpisodeItem(
    index: Int,
    title: String?,
    onClick: () -> Unit
) {
    val displayTitle = if (!title.isNullOrBlank()) {
        title
    } else {
        "第 ${index + 1} 集"
    }
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = displayTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "播放",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
