# MoonTVAndroid

基于 [MoonTVPlus](https://github.com/anglesgirl/MoonTVPlus) 核心功能的安卓原生复现。

## 功能概述

将原 Web 项目（Next.js + HLS.js）的核心能力用安卓原生方式实现：

- **苹果 CMS V10 API 聚合**：多源并发搜索、详情拉取、`vod_play_url` 解析提取 m3u8
- **TMDB 元数据**（合规替代豆瓣爬取）：搜索、详情、热门趋势
- **豆瓣可选模块**：走 CDN 代理，仅在用户配置时启用
- **HLS 播放**：Media3 ExoPlayer 播放 m3u8 流媒体
- **ECH（Encrypted Client Hello）**：通过 DEfO Conscrypt fork 隐藏 TLS 握手中的 SNI，规避基于 SNI 的请求拦截
- **空壳启动**：不内置任何播放源，用户自行配置（与原项目一致）

## 技术栈

| 分类 | 选型 |
|------|------|
| 语言 | Kotlin 1.9.24 |
| UI | Jetpack Compose + Material3 |
| 网络 | OkHttp 4.12 + kotlinx.serialization |
| 播放器 | Media3 (ExoPlayer) 1.4.0 + HLS |
| ECH | DEfO Conscrypt fork（自编译） + Cloudflare DoH |
| 存储 | DataStore Preferences |
| 图片 | Coil Compose |

## 项目结构

```
app/src/main/java/com/moontv/app/
├── MoonTVApp.kt                  # Application，初始化 ECH
├── MainActivity.kt               # 入口 + 导航
├── config/
│   ├── SiteConfig.kt             # 站点配置模型（空壳源）
│   └── ApiConfigs.kt             # API 路径常量
├── data/
│   ├── model/                    # 数据模型（MacCMS/TMDB/Douban）
│   ├── parser/
│   │   ├── VodPlayUrlParser.kt  # vod_play_url 拆分（对应 downstream.ts）
│   │   └── VideoNameParser.kt    # 文件名集数解析（对应 video-parser.ts）
│   ├── remote/api/
│   │   ├── MacCMSApi.kt          # 苹果CMS搜索/详情
│   │   ├── TmdbApi.kt            # TMDB 元数据
│   │   └── DoubanApi.kt          # 豆瓣可选模块
│   └── repository/
│       ├── ConfigRepository.kt   # 配置持久化
│       └── VideoRepository.kt    # 多源聚合
├── net/
│   ├── HttpClientFactory.kt      # OkHttp 客户端工厂
│   └── ech/                      # ECH 模块
│       ├── EchProvider.kt        # ECH 注入入口
│       ├── EchConfigProvider.kt  # DoH 查 HTTPS 记录
│       ├── EchDnsResolver.kt     # DoH 解析
│       ├── EchInterceptor.kt     # ECH 预取拦截器
│       └── README.md             # ECH 编译说明
├── player/
│   └── VideoPlayerManager.kt     # ExoPlayer + HLS
└── ui/
    ├── NavRoutes.kt              # 路由
    ├── HomeScreen.kt             # 搜索+热门
    ├── DetailScreen.kt           # 详情+分集
    ├── PlayerScreen.kt           # 播放器
    └── SettingsScreen.kt         # 源配置
```

## 与原项目的对应关系

| 原项目 (MoonTVPlus) | 本项目 | 说明 |
|---------------------|--------|------|
| `src/lib/downstream.ts` | `MacCMSApi.kt` | 苹果CMS搜索/详情，`vod_play_url` 解析逻辑一致 |
| `src/lib/video-parser.ts` | `VideoNameParser.kt` | 文件名集数/季解析 |
| `src/lib/douban.ts` | `DoubanApi.kt` | 豆瓣数据获取（可选） |
| `src/lib/douban.client.ts` | `DoubanApi.kt` | 豆瓣代理/CDN 配置 |
| `src/lib/tmdb.client.ts` | `TmdbApi.kt` | TMDB 替代豆瓣作为主元数据源 |
| HLS.js + ArtPlayer | `VideoPlayerManager.kt` | Media3 ExoPlayer HLS |
| `src/lib/config.ts` | `SiteConfig.kt` + `ApiConfigs.kt` | 站点配置 |
| 配置文件 (api_site) | `ConfigRepository.kt` | DataStore 持久化 |

## 编译

### 环境要求
- JDK 17
- Android SDK 34
- Gradle 8.5+

### 步骤

1. 生成 Gradle Wrapper（项目未内置）：
```bash
cd MoonTVAndroid
gradle wrapper --gradle-version 8.7
```

2. 编译：
```bash
./gradlew :app:assembleDebug
```

### 启用 ECH（可选，隐藏 SNI）

默认使用标准 Conscrypt（不支持 ECH），降级为普通 TLS。要启用 ECH：

1. 按 [app/src/main/java/com/moontv/app/net/ech/README.md](app/src/main/java/com/moontv/app/net/ech/README.md) 编译 DEfO Conscrypt fork 的 AAR
2. 将编译产物放入 `app/libs/conscrypt-android-defo.aar`
3. 修改 `app/build.gradle.kts`，注释标准依赖、启用 DEfO 依赖：
```kotlin
// implementation("org.conscrypt:conscrypt-android:2.5.2")
implementation(files("libs/conscrypt-android-defo.aar"))
```
4. 重新编译。ECH 会自动生效（见 `EchProvider.init()`）

## 配置使用

首次启动为空壳，进入设置页配置：

1. **TMDB API Key**：到 [themoviedb.org](https://www.themoviedb.org/settings/api) 申请，填入后启用元数据
2. **采集源**：填写苹果CMS资源站
   - `key`：唯一标识（如 `dyttzy`）
   - `name`：显示名
   - `api`：资源站 vod API 根地址，如 `http://xxx.com/api.php/provide/vod`
3. **豆瓣代理**（可选）：填入代理 URL 启用豆瓣数据

配置示例（对应原项目的配置文件）：
```
TMDB API Key: your_tmdb_key
采集源:
  - key: dyttzy
    name: 示例资源
    api: http://xxx.com/api.php/provide/vod
```

## 合规说明

- 本项目为空壳播放器框架，不内置任何视频源
- 元数据使用 TMDB 官方 API（合规），豆瓣为可选降级方案
- 苹果CMS API 调用为技术中立实现，用户需自行确保所接入资源站的合法性
- ECH 模块仅用于保护网络通信隐私，不改变请求内容的合法性
- 仅供个人学习使用，请遵守当地法律法规
