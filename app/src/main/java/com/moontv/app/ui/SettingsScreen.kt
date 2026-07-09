package com.moontv.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.moontv.app.config.ApiSite
import com.moontv.app.config.SiteConfig
import com.moontv.app.data.repository.ConfigRepository
import com.moontv.app.net.ech.EchProvider
import kotlinx.coroutines.launch

/**
 * 采集源表单项的可编辑状态
 */
private data class SiteFormState(
    val key: String,
    val name: String,
    val api: String
)

/**
 * 设置页：配置 TMDB API Key、豆瓣代理 URL 与采集源列表
 *
 * - 进入时从 [ConfigRepository.configFlow] 读取并初始化表单（仅初始化一次，避免覆盖用户编辑）
 * - 采集源可增删，每项含 key / name / api 三个字段
 * - 保存按钮构建 [SiteConfig] 并调用 [ConfigRepository.save]
 * - 展示 ECH 可用性状态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    configRepository: ConfigRepository,
    onBack: () -> Unit
) {
    // 初始为 null，待真实配置首次下发后再初始化表单
    val config by configRepository.configFlow.collectAsState(initial = null)

    // 表单状态
    var initialized by remember { mutableStateOf(false) }
    var tmdbApiKey by remember { mutableStateOf("") }
    var doubanProxyUrl by remember { mutableStateOf("") }
    var sites by remember { mutableStateOf(listOf<SiteFormState>()) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val echAvailable = remember { EchProvider.isEchAvailable() }

    // 首次拿到真实配置后初始化表单（仅一次）
    LaunchedEffect(config) {
        if (!initialized && config != null) {
            tmdbApiKey = config!!.tmdbApiKey
            doubanProxyUrl = config!!.doubanProxyUrl
            sites = config!!.apiSite.values.map {
                SiteFormState(it.key, it.name, it.api)
            }
            initialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ECH 状态
            item {
                Column {
                    SectionLabel("网络环境")
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ECH（加密 ClientHello）")
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(if (echAvailable) "已启用" else "未启用")
                                }
                            )
                        }
                    }
                }
            }

            // TMDB API Key
            item {
                Column {
                    SectionLabel("TMDB API Key")
                    OutlinedTextField(
                        value = tmdbApiKey,
                        onValueChange = { tmdbApiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("themoviedb.org 申请的 API Key") },
                        singleLine = true
                    )
                }
            }

            // 豆瓣代理 URL
            item {
                Column {
                    SectionLabel("豆瓣代理 URL（可选）")
                    OutlinedTextField(
                        value = doubanProxyUrl,
                        onValueChange = { doubanProxyUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("如 https://m.douban.cmliussss.net/") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                }
            }

            // 采集源列表
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("采集源")
                    Text(
                        text = "共 ${sites.size} 个",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            itemsIndexed(sites, key = { index, _ -> index }) { index, site ->
                SiteFormItem(
                    index = index,
                    state = site,
                    onKeyChange = { newKey ->
                        sites = sites.toMutableList().apply {
                            this[index] = this[index].copy(key = newKey)
                        }
                    },
                    onNameChange = { newName ->
                        sites = sites.toMutableList().apply {
                            this[index] = this[index].copy(name = newName)
                        }
                    },
                    onApiChange = { newApi ->
                        sites = sites.toMutableList().apply {
                            this[index] = this[index].copy(api = newApi)
                        }
                    },
                    onDelete = {
                        sites = sites.toMutableList().apply { removeAt(index) }
                    }
                )
            }

            // 新增采集源
            item {
                OutlinedButton(
                    onClick = { sites = sites + SiteFormState("", "", "") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("添加采集源")
                }
            }

            // 保存按钮
            item {
                Button(
                    onClick = {
                        val cfg = SiteConfig(
                            apiSite = sites
                                .filter { it.key.isNotBlank() }
                                .associate { it.key.trim() to ApiSite(it.key.trim(), it.name.trim(), it.api.trim()) },
                            tmdbApiKey = tmdbApiKey.trim(),
                            doubanProxyUrl = doubanProxyUrl.trim()
                        )
                        scope.launch {
                            configRepository.save(cfg)
                            snackbarHostState.showSnackbar("配置已保存")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存配置")
                }
            }
        }
    }
}

/**
 * 单个采集源表单项：key / name / api + 删除
 */
@Composable
private fun SiteFormItem(
    index: Int,
    state: SiteFormState,
    onKeyChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onApiChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "采集源 ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除采集源")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.key,
                onValueChange = onKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Key（唯一标识）") },
                placeholder = { Text("如 dyttzy") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None
                )
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称") },
                placeholder = { Text("如 电影天堂") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.api,
                onValueChange = onApiChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API 地址") },
                placeholder = { Text("如 https://xxx.com/api.php/provide/vod") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
        }
    }
}

/**
 * 区块标题
 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}
