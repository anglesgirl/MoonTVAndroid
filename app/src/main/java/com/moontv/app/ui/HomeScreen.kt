package com.moontv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moontv.app.config.SiteConfig
import com.moontv.app.config.TmdbConfig
import com.moontv.app.data.model.SearchResult
import com.moontv.app.data.model.TmdbItem

/**
 * 首页：搜索栏 + 热门趋势 + 搜索结果
 *
 * 展示逻辑：
 * - 未配置任何源或 TMDB Key 时显示空状态，引导去设置页
 * - 有搜索结果时：分两块展示「采集源结果」（带源标识，可进入详情）与「TMDB 结果」
 * - 无搜索结果时：展示 TMDB 热门趋势
 *
 * 网格使用 [LazyVerticalGrid]，并通过全宽 header 切分区块。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    config: SiteConfig,
    onNavigateToDetail: (source: String, id: String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val searchResults by viewModel.searchResults.collectAsState()
    val tmdbResults by viewModel.tmdbResults.collectAsState()
    val trending by viewModel.trending.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    var query by remember { mutableStateOf("") }

    // 当 TMDB Key 配置好（或变更）后自动加载热门趋势
    // 先同步配置到 ViewModel，避免使用陈旧 config 导致请求被跳过
    LaunchedEffect(config) {
        viewModel.updateConfig(config)
        if (config.tmdbApiKey.isNotBlank()) {
            viewModel.loadTrending()
        }
    }

    // 当前是否完全未配置（与 MainViewModel.isEmpty 保持一致，但此处可响应 config 变化）
    val empty = config.apiSite.isEmpty() && config.tmdbApiKey.isBlank()
    // 是否处于搜索结果展示态
    val hasSearched = searchResults.isNotEmpty() || tmdbResults.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MoonTV") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (empty) {
            // 空状态：引导去设置
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onGoToSettings = onNavigateToSettings
            )
        } else {
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)) {
                // 搜索栏
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { viewModel.search(query.trim()) },
                    loading = loading
                )

                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // 内容区
                Box(modifier = Modifier.fillMaxSize()) {
                    if (loading && !hasSearched && trending.isEmpty()) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        ContentGrid(
                            hasSearched = hasSearched,
                            searchResults = searchResults,
                            tmdbResults = tmdbResults,
                            trending = trending,
                            onCmsClick = { result ->
                                onNavigateToDetail(result.source, result.id)
                            },
                            onTmdbClick = { item ->
                                // TMDB 项无 CMS 源映射，点击以其标题触发聚合搜索
                                viewModel.search(item.displayTitle)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 搜索栏
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    loading: Boolean
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        placeholder = { Text("搜索电影、剧集、动漫…") },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}

/**
 * 内容网格：根据是否在搜索态切换展示内容
 */
@Composable
private fun ContentGrid(
    hasSearched: Boolean,
    searchResults: List<SearchResult>,
    tmdbResults: List<TmdbItem>,
    trending: List<TmdbItem>,
    onCmsClick: (SearchResult) -> Unit,
    onTmdbClick: (TmdbItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (hasSearched) {
            // 采集源结果区块
            if (searchResults.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader("采集源结果（${searchResults.size}）")
                }
                items(searchResults, key = { "${it.source}_${it.id}" }) { result ->
                    CmsResultCard(result = result, onClick = { onCmsClick(result) })
                }
            }
            // TMDB 结果区块
            if (tmdbResults.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader("TMDB 结果（${tmdbResults.size}）")
                }
                items(tmdbResults, key = { "tmdb_${it.id}" }) { item ->
                    TmdbResultCard(item = item, onClick = { onTmdbClick(item) })
                }
            }
            if (searchResults.isEmpty() && tmdbResults.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptySearchHint()
                }
            }
        } else {
            // 热门趋势
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("热门趋势")
            }
            items(trending, key = { "trending_${it.id}" }) { item ->
                TmdbResultCard(item = item, onClick = { onTmdbClick(item) })
            }
        }
    }
}

/**
 * 区块标题
 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

/**
 * 苹果 CMS 搜索结果卡片
 */
@Composable
private fun CmsResultCard(result: SearchResult, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            Box(modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)) {
                if (result.poster.isNotBlank()) {
                    AsyncImage(
                        model = result.poster,
                        contentDescription = result.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    PosterPlaceholder(title = result.title)
                }
                // 源标识徽标
                Surface(
                    color = Color(0xCC000000),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = result.sourceName.ifBlank { result.source },
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = result.title.ifBlank { "未知标题" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (result.year.isNotBlank()) {
                    Text(
                        text = result.year,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * TMDB 结果卡片（搜索结果与热门趋势共用）
 */
@Composable
private fun TmdbResultCard(item: TmdbItem, onClick: () -> Unit) {
    val posterUrl = item.posterPath?.let { TmdbConfig.IMAGE_BASE + it }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            Box(modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)) {
                if (!posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = item.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    PosterPlaceholder(title = item.displayTitle)
                }
                // 媒体类型徽标
                val typeLabel = when (item.mediaType) {
                    "tv" -> "剧集"
                    "movie" -> "电影"
                    else -> null
                }
                if (typeLabel != null) {
                    Surface(
                        color = Color(0xCC000000),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.displayTitle.ifBlank { "未知标题" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.displayYear.isNotBlank()) {
                    Text(
                        text = item.displayYear,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 海报占位
 */
@Composable
private fun PosterPlaceholder(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Movie,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title.take(6),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * 空状态：未配置源
 */
@Composable
private fun EmptyState(modifier: Modifier, onGoToSettings: () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "尚未配置采集源与 TMDB API Key",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "请前往设置页添加采集源与 TMDB API Key，配置完成后即可搜索与浏览影视。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.FilledTonalButton(onClick = onGoToSettings) {
                Text("前往设置")
            }
        }
    }
}

/**
 * 搜索无结果提示
 */
@Composable
private fun EmptySearchHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "没有找到相关结果，换个关键词试试",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
