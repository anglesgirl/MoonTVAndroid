package com.moontv.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moontv.app.config.SiteConfig
import com.moontv.app.data.model.SearchResult
import com.moontv.app.data.model.TmdbItem
import com.moontv.app.data.repository.VideoRepository
import com.moontv.app.net.ech.EchProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主 ViewModel：管理搜索、详情、推荐状态
 */
class MainViewModel(
    private var config: SiteConfig = SiteConfig.EMPTY
) : ViewModel() {

    private val echEnabled = EchProvider.isEchAvailable()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _tmdbResults = MutableStateFlow<List<TmdbItem>>(emptyList())
    val tmdbResults: StateFlow<List<TmdbItem>> = _tmdbResults.asStateFlow()

    private val _trending = MutableStateFlow<List<TmdbItem>>(emptyList())
    val trending: StateFlow<List<TmdbItem>> = _trending.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 当前是否未配置任何源 */
    val isEmpty: Boolean get() = config.apiSite.isEmpty() && config.tmdbApiKey.isBlank()

    /** 更新配置（设置页保存后调用） */
    fun updateConfig(newConfig: SiteConfig) {
        config = newConfig
    }

    /** 搜索视频（多源聚合 + TMDB 元数据） */
    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching {
                val repo = VideoRepository(config, echEnabled)
                // 并发：苹果CMS多源搜索 + TMDB搜索
                kotlinx.coroutines.coroutineScope {
                    val cmsDeferred = async {
                        runCatching { repo.searchAll(query) }.getOrDefault(emptyList())
                    }
                    val tmdbDeferred = async {
                        runCatching { repo.searchMetadata(query) }.getOrDefault(emptyList())
                    }
                    _searchResults.value = cmsDeferred.await()
                    _tmdbResults.value = tmdbDeferred.await()
                }
            }.onFailure { _error.value = it.message ?: "搜索失败" }
            _loading.value = false
        }
    }

    /** 加载首页热门（TMDB trending） */
    fun loadTrending() {
        if (config.tmdbApiKey.isBlank()) return
        viewModelScope.launch {
            _loading.value = true
            runCatching {
                _trending.value = VideoRepository(config, echEnabled).trending()
            }.onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    /** 获取视频详情 */
    suspend fun detail(source: String, id: String): SearchResult? {
        return runCatching {
            VideoRepository(config, echEnabled).detail(source, id)
        }.getOrNull()
    }
}
