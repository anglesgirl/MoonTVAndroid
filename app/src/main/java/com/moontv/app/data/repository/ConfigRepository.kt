package com.moontv.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moontv.app.config.SiteConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "moontv_config")

/**
 * 配置仓库：持久化站点配置（对应原项目的配置文件 / INIT_CONFIG）
 * 空壳启动，用户在设置页填入后持久化到 DataStore
 */
class ConfigRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val key = stringPreferencesKey("site_config_json")

    /** 读取配置流 */
    val configFlow: Flow<SiteConfig> = context.dataStore.data.map { prefs ->
        prefs[key]?.let {
            runCatching { json.decodeFromString<SiteConfig>(it) }.getOrNull()
        } ?: SiteConfig.EMPTY
    }

    /** 保存配置 */
    suspend fun save(config: SiteConfig) {
        context.dataStore.edit { prefs ->
            prefs[key] = json.encodeToString(SiteConfig.serializer(), config)
        }
    }
}
