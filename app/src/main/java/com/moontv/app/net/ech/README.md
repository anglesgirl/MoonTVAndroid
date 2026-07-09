# ECH 模块（Encrypted Client Hello）

本目录为 MoonTVAndroid 提供 ECH（Encrypted Client Hello，加密的 Client Hello）能力，
用于在 TLS 握手阶段隐藏真实请求域名，规避针对 SNI 的网络拦截。

> 适用场景：本 App 是影视聚合客户端，部分采集源（苹果CMS / TMDB / 豆瓣代理）的域名
> 可能被运营商或中间设备基于 SNI 阻断。启用 ECH 后，ClientHello 中的 SNI 被加密，
> 中间设备只能看到一个"公共"（外层）域名，从而无法按域名拦截。

---

## 1. ECH 原理简述

普通 TLS 握手的第一条消息 `ClientHello` 以**明文**携带 `server_name`（即 SNI）扩展，
指明客户端要访问的真实域名。这是 SNI 拦截的根因。

ECH（前身为 ESNI，现由 IETF 草案 draft-ietf-tls-esni / RFC 化进程中定义）的做法：

1. 目标域名在 DNS 中发布一条 **HTTPS（TYPE65 / SVCB）记录**，其中携带 `ech` 参数，
   即 `ECHConfigList`（一段公钥配置，由服务端的 ECH 私钥对应）。
2. 客户端通过 **加密 DNS（DoH / DoT）** 查询该 HTTPS 记录——否则明文 DNS 又会泄露域名。
3. 握手时客户端用 `ECHConfigList` 把"真实 ClientHello"整体加密，封装在一个"外层
   ClientHello"里；外层 SNI 指向一个公共域名（如 `cloudflare-ech.com`），真实 SNI 在内层。
4. 只有真正持有 ECH 私钥的目标服务端才能解密内层并完成握手；中间设备既看不到真实
   SNI，也无法伪造。

> 因此 ECH 的两个前提：**加密 DNS** + **服务端发布 HTTPS/ECH 记录**。本模块在客户端
> 侧用 DoH 满足前者，并从 HTTPS 记录中提取 `ECHConfigList` 满足后者。

---

## 2. 模块组成

| 文件 | 职责 |
| --- | --- |
| `EchProvider.kt` | ECH 总入口。反射探测 DEfO fork 版 Conscrypt，注册为 JCA Provider，并把"支持 ECH 的 SSLSocketFactory"注入 OkHttp。提供 `applyTo(builder)` 与 `isEchAvailable()`。 |
| `EchConfigProvider.kt` | 通过 Cloudflare DoH 查询目标域名的 HTTPS 记录（TYPE65），从 SVCB 线格式 RDATA 中提取 `ECHConfigList`，带 1 小时 LRU 缓存。 |
| `EchDnsResolver.kt` | 配合 ECH 的 DoH 解析器，返回目标域名的 A 记录 IP 列表，带缓存（5 分钟）。供需要绕过系统 DNS 的自定义连接场景使用。 |
| `EchInterceptor.kt` | OkHttp **应用拦截器**，在连接前为目标域名预取并缓存 ECH 配置，并记录"需要 ECH 的域名"集合。 |

### 调用时序

```
HttpClientFactory.create(echEnabled = true)
        │
        ▼
EchProvider.applyTo(builder)            ← 注册 DEfO Provider + 注入包装后的 SSLSocketFactory
        │                                  （不可用则跳过，保持标准 TLS）
        ▼
client 发起请求
        │
        ▼
EchInterceptor.intercept()              ← 应用拦截器：在连接前同步预取 ECH 配置 → 写入缓存
        │                                  （DoH 走独立的、未接入 ECH 的 OkHttp 客户端，避免递归）
        ▼
OkHttp ConnectInterceptor / TLS 握手
        │
        ▼
EchSSLSocketFactory.createSocket(...)   ← 创建 SSLSocket 后、握手前，从缓存读取 ECHConfigList
        │                                  反射调用 Conscrypt.setEchConfigList(socket, bytes)
        ▼
startHandshake()                        ← 发出加密了真实 SNI 的 ClientHello
```

### 关键设计点

- **为何 EchInterceptor 必须是应用拦截器**：OkHttp 的网络拦截器运行在 `ConnectInterceptor`
  （含 TLS 握手）之后，那时 SNI 已发出。应用拦截器运行在最前端，能保证预取在连接前完成。
- **为何 DoH 用独立客户端**：`EchConfigProvider` / `EchDnsResolver` 各自持有一个**未接入 ECH**
  的普通 OkHttp 客户端。若复用被 ECH 包装的客户端，会触发 `EchInterceptor` 递归与死锁。
  Cloudflare DoH 端点本身用普通 TLS 即可作为 ECH 的"引导信道"。
- **容错降级**：任何环节失败（无 DEfO、DoH 失败、解析失败、setEchConfigList 失败）
  都只打印警告并退化为普通 TLS（GREASE 或明文 SNI），**不阻断**正常请求。

---

## 3. 底层依赖：DEfO fork 版 Conscrypt

Android 系统自带的 Conscrypt（以及 Maven 上的 `org.conscrypt:conscrypt-android`）**不支持 ECH**。
ECH 需要一个基于带 ECH 的 BoringSSL 重新编译的 Conscrypt fork。这类 fork 由 **DEfO 项目**
（Draft ECH for OpenSSL，由 Trinity College Dublin 的 Stephen Farrell 等推动，受 OTF 资助）维护。

> 参考来源：DEfO ECH 互操作性报告把 "Conscrypt-GP" 列为带 ECH 的 Android Conscrypt fork，
> 基于 BoringSSL，仅做客户端 ECH。详见 <https://defo.ie> 与
> <https://github.com/defo-project/ech-interop-report>。

### 可选的 fork 仓库

- <https://github.com/nickoala/conscrypt-fork>（早期社区 fork，README 中常被引用）
- <https://github.com/guardianproject/conscrypt>（`2.6-alpha` 带 ECH 的分支，DEfO 互操作报告所指）
- DEfO 项目主页与相关组件：<https://defo.ie>、<https://github.com/defo-project>

本模块**不依赖** fork 的编译期 API——全部通过反射调用，探测依据是 `org.conscrypt.Conscrypt`
类上是否存在 `setEchConfigList(SSLSocket, ByteArray)` 方法：

- 存在该方法 → 判定为 DEfO ECH 版，注册 Provider 并启用 ECH。
- 不存在（如标准 `conscrypt-android:2.5.2`）→ 判定为标准 Conscrypt，降级为标准 TLS。

> 如你使用的 fork 把该方法命名为别的名字，修改 `EchProvider.findEchMethod()` 中的
> `ECH_METHOD_NAME` 常量即可。

### 自编译 AAR（需 BoringSSL）

DEfO 版 Conscrypt 的 native 层来自带 ECH 的 BoringSSL，编译步骤概要（Linux/macOS）：

```bash
# 1. 准备 NDK 与环境变量
export ANDROID_HOME=/path/to/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/<version>

# 2. 拉取 fork（以 guardianproject 为例，切到带 ECH 的分支）
git clone https://github.com/guardianproject/conscrypt.git
cd conscrypt
git checkout <ech-branch>      # 如 2.6-alpha / ech 分支

# 3. 配置 BoringSSL（fork 的 prebuilts 或脚本会拉取带 ECH 的 BoringSSL）
#    确认 BoringSSL 启用了 ECH（默认开启），否则需自行编译并指向。

# 4. 用 Gradle 构建 Android AAR
./gradlew :conscrypt-android:assembleRelease
# 产物：conscrypt-android/build/outputs/aar/conscrypt-android-release.aar
```

> 不同 fork 的构建脚本略有差异，请以仓库自带 `BUILDING.md` / `README` 为准。
> 关键是 native 库必须链接**带 ECH 的 BoringSSL**，否则 `setEchConfigList` 不会生效。

---

## 4. 接入项目

### 4.1 放置 AAR

将自编译出的 AAR 重命名为约定名并放入 `app/libs`：

```
app/libs/conscrypt-android-defo.aar
```

`settings.gradle.kts` 已配置本地 flatDir 仓库：

```kotlin
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral()
        flatDir { dirs("app/libs") }   // 用于引入 DEfO ECH 版 Conscrypt
    }
}
```

### 4.2 切换 `app/build.gradle.kts` 依赖

当前默认使用标准（无 ECH）Conscrypt 作为 fallback：

```kotlin
// Conscrypt：为低版本设备提供 TLS1.3，并作为 ECH 的底层宿主
// 注意：开启 ECH 需替换为 DEfO fork 编译的 AAR（见 net/ech/README）
implementation("org.conscrypt:conscrypt-android:2.5.2")
// 如已自编译 DEfO 版本，注释上面一行，取消下面注释：
// implementation(files("libs/conscrypt-android-defo.aar"))
```

**启用 ECH** 时：注释 `org.conscrypt:conscrypt-android:2.5.2`，启用
`implementation(files("libs/conscrypt-android-defo.aar"))`，重新构建即可。

### 4.3 在代码中开启 ECH

`HttpClientFactory` 已预留入口，传入 `echEnabled = true`：

```kotlin
val client = HttpClientFactory.create(echEnabled = true)
// 或豆瓣专用：HttpClientFactory.createForDouban(echEnabled = true)

if (EchProvider.isEchAvailable()) {
    // ECH 已真正启用（DEfO 版已注册）
} else {
    // 降级为标准 TLS（未编译 DEfO AAR，或探测失败）
}
```

---

## 5. 当前 fallback 行为

在**未编译并替换 DEfO AAR** 的情况下（即当前仓库默认状态）：

- `app/build.gradle.kts` 依赖标准 `org.conscrypt:conscrypt-android:2.5.2`。
- `EchProvider.initialize()` 反射能加载 `org.conscrypt.Conscrypt`，但**找不到**
  `setEchConfigList` 方法 → 判定为标准 Conscrypt，`isEchAvailable()` 返回 `false`，
  打印警告 `检测到标准 Conscrypt，但缺少 ECH 支持…`。
- `applyTo(builder)` 不做任何注入，OkHttp 使用系统默认 TLS（Conscrypt 仍提供 TLS1.3，
  但 SNI 明文、无 ECH）。
- `EchInterceptor` 因 `isEchAvailable() == false` 不会发起 DoH 预取，零额外开销。

即：**ECH 是渐进增强的**——默认零影响，替换 AAR 后自动启用，无需改动业务代码。

---

## 6. 限制与注意事项

- **服务端须支持 ECH**：仅当目标域名在 DNS 中发布了含 `ech` 的 HTTPS 记录、且其前端
  （如 Cloudflare）支持 ECH 解密时，ECH 才会真正生效；否则握手会退化为 GREASE/明文 SNI。
- **DoH 引导信道**：ECH 配置依赖对 `cloudflare-dns.com` 的 DoH 查询。若该端点本身也被
  封锁，则 ECH 无法引导（这是任何 ECH 客户端的共性限制）。可考虑增加备用 DoH 端点。
- **跨域重定向**：`EchInterceptor` 作为应用拦截器只在调用开始时预取一次；跨主机重定向
  到的新域名若也需 ECH，会在其首次请求时按同样流程预取。
- **不要在主线程使用**：`EchInterceptor` 用 `runBlocking` 同步预取，应确保该客户端仅在
  后台线程发起请求（与 App 通用网络规范一致）。
- **反射 API 的稳定性**：`setEchConfigList` 的方法签名以 DEfO fork 现状为准；若上游
  fork 调整命名/签名，需同步更新 `EchProvider.findEchMethod()`。
```
