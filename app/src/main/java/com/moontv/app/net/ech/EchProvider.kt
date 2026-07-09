package com.moontv.app.net.ech

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import java.lang.reflect.Method
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import java.security.Provider
import java.security.Security
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private const val TAG = "EchProvider"

/**
 * ECH（Encrypted Client Hello）总入口。
 *
 * 负责探测并加载 DEfO fork 版本的 Conscrypt（带 ECH 支持），将其注册为 JCA Provider，
 * 并把"支持 ECH 的 SSLSocketFactory"注入 OkHttp，从而对被 SNI 拦截的影视源隐藏真实请求域名。
 *
 * 探测逻辑（全部基于反射，避免在编译期硬依赖 DEfO 的私有 API）：
 *  1. 反射加载 [CONSCRYPT_CLASS_NAME]（不存在说明连标准 Conscrypt 都没有）。
 *  2. 在该类上查找 [ECH_METHOD_NAME]（setEchConfigList(SSLSocket, ByteArray)）——
 *     这是 DEfO ECH fork 独有的方法，标准 conscrypt-android:2.5.2 没有该方法，
 *     借此区分"标准 Conscrypt"与"DEfO ECH 版"。
 *  3. 若 DEfO 版可用：用 Security.insertProviderAt(provider, 1) 注册，并标记 ECH 可用。
 *  4. 否则：不注入任何 SSLSocketFactory，降级为标准 OkHttp TLS，并打印警告。
 *
 * 注意：DEfO 的 Conscrypt 需自编译为 AAR（见 app/libs 与同目录 README），
 * 当前项目已配置 fallback 到标准 org.conscrypt:conscrypt-android:2.5.2（不支持 ECH）。
 *
 * 用法：
 * ```
 * val builder = OkHttpClient.Builder()...
 * EchProvider.applyTo(builder)   // 自动判断可用性，不可用则保持标准 TLS
 * val client = builder.build()
 * if (EchProvider.isEchAvailable()) { /* 已启用 ECH */ }
 * ```
 */
object EchProvider {

    /** Conscrypt 主类全限定名 */
    private const val CONSCRYPT_CLASS_NAME = "org.conscrypt.Conscrypt"

    /** 注册到 JCA 时的 Provider 名称 */
    private const val PROVIDER_NAME = "Conscrypt"

    /** DEfO ECH fork 独有方法：为单个 SSLSocket 设置 ECHConfigList */
    private const val ECH_METHOD_NAME = "setEchConfigList"

    @Volatile
    private var echAvailable = false

    /** 反射得到的 Conscrypt 类与 setEchConfigList 方法（仅 DEfO 版有效） */
    @Volatile
    private var conscryptClass: Class<*>? = null

    @Volatile
    private var setEchConfigListMethod: Method? = null

    /** 复用的 SSLSocketFactory 与 TrustManager，避免每个客户端重复创建 */
    @Volatile
    private var sharedFactory: SSLSocketFactory? = null

    @Volatile
    private var sharedTrustManager: X509TrustManager? = null

    init {
        initialize()
    }

    /**
     * 探测并初始化 ECH 环境。整个流程对失败容错：任何异常都只导致 ECH 不可用，
     * 不会抛出影响 App 启动。
     */
    fun initialize() {
        try {
            // 1. 反射加载 Conscrypt 主类
            val clazz = runCatching { Class.forName(CONSCRYPT_CLASS_NAME) }.getOrNull()
            if (clazz == null) {
                Log.w(TAG, "未找到 org.conscrypt.Conscrypt，ECH 不可用，降级为标准 OkHttp TLS。")
                echAvailable = false
                return
            }
            conscryptClass = clazz

            // 2. 探测是否为 DEfO ECH 版（查找 setEchConfigList 方法）
            val method = findEchMethod(clazz)
            if (method == null) {
                Log.w(
                    TAG,
                    "检测到标准 Conscrypt，但缺少 ECH 支持（无 $ECH_METHOD_NAME 方法）。" +
                        "降级为标准 TLS。请替换为 DEfO fork 编译的 AAR 以启用 ECH。"
                )
                echAvailable = false
                return
            }
            setEchConfigListMethod = method

            // 3. 注册为 JCA Provider（插入到最高优先级，覆盖系统默认）
            val provider = newProvider(clazz)
            if (provider == null) {
                Log.w(TAG, "Conscrypt.newProvider() 返回空，ECH 不可用，降级为标准 TLS。")
                echAvailable = false
                return
            }
            if (Security.getProvider(provider.name) == null) {
                Security.insertProviderAt(provider, 1)
                Log.i(TAG, "DEfO ECH 版 Conscrypt 已注册为 Provider[${provider.name}] @ index 1。")
            } else {
                Log.i(TAG, "Provider[${provider.name}] 已存在，跳过重复注册。")
            }

            echAvailable = true
            Log.i(TAG, "ECH 已就绪：Conscrypt 类=$clazz，方法=$method。")
        } catch (t: Throwable) {
            Log.w(TAG, "初始化 ECH 失败，降级为标准 TLS：${t.message}", t)
            echAvailable = false
        }
    }

    /** 当前是否真正启用了 ECH（DEfO 版已注册且 setEchConfigList 方法可用）。 */
    fun isEchAvailable(): Boolean = echAvailable

    /**
     * 将"支持 ECH 的 SSLSocketFactory"注入 OkHttp builder。
     *
     * - 若 ECH 可用：注入包装后的 [EchSSLSocketFactory] 与系统 TrustManager，
     *   并添加 [EchInterceptor] 用于在连接前预取 ECH 配置。
     * - 若 ECH 不可用：不修改 builder（保持标准 OkHttp TLS），并打印警告。
     *
     * 已被 [com.moontv.app.net.HttpClientFactory] 在 echEnabled=true 时调用。
     */
    fun applyTo(builder: OkHttpClient.Builder) {
        if (!echAvailable) {
            Log.w(TAG, "applyTo：ECH 不可用，跳过注入，使用标准 OkHttp TLS。")
            return
        }
        try {
            val (factory, trustManager) = ensureFactory()
            builder.sslSocketFactory(factory, trustManager)
            // 应用拦截器：在连接前预取并缓存 ECH 配置
            builder.addInterceptor(EchInterceptor())
            Log.i(TAG, "已注入 ECH SSLSocketFactory 与 ECH 预取拦截器。")
        } catch (t: Throwable) {
            Log.w(TAG, "注入 ECH SSLSocketFactory 失败，降级为标准 TLS：${t.message}", t)
        }
    }

    /**
     * 懒加载并复用 SSLSocketFactory + X509TrustManager（双重检查锁）。
     */
    private fun ensureFactory(): Pair<SSLSocketFactory, X509TrustManager> {
        sharedFactory?.let { return it to (sharedTrustManager ?: error("sharedTrustManager 未初始化")) }
        synchronized(this) {
            sharedFactory?.let { return it to sharedTrustManager!! }
            val provider = Security.getProvider(PROVIDER_NAME)
                ?: error("Provider[$PROVIDER_NAME] 未注册")
            val sslContext = SSLContext.getInstance("TLS", provider)
            sslContext.init(null, null, null)
            val trustManager = systemTrustManager()
            val delegate = sslContext.socketFactory
            val clazz = conscryptClass ?: error("conscryptClass 为空")
            val method = setEchConfigListMethod ?: error("setEchConfigListMethod 为空")
            val wrapped = EchSSLSocketFactory(delegate, clazz, method)
            // 注意顺序：先写 sharedTrustManager，再写 sharedFactory（volatile），
            // 这样任何读到 sharedFactory 非空的线程，必定能读到 sharedTrustManager 已赋值。
            sharedTrustManager = trustManager
            sharedFactory = wrapped
            return wrapped to trustManager
        }
    }

    /**
     * 取系统默认 TrustManager（PKIX + 系统 CA 库），用于验证目标站点证书。
     * 注册 Conscrypt 为 Provider 后，证书校验仍走系统受信根库。
     */
    private fun systemTrustManager(): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        return tmf.trustManagers.firstOrNull { it is X509TrustManager } as? X509TrustManager
            ?: error("未找到 X509TrustManager")
    }

    /** 反射调用 Conscrypt.newProvider() 获取 Provider 实例 */
    private fun newProvider(clazz: Class<*>): Provider? {
        return try {
            val m = clazz.getMethod("newProvider")
            m.invoke(null) as? Provider
        } catch (t: Throwable) {
            Log.w(TAG, "Conscrypt.newProvider() 调用失败：${t.message}")
            null
        }
    }

    /** 查找 DEfO fork 独有的 setEchConfigList(SSLSocket, ByteArray) 方法；不存在返回 null */
    private fun findEchMethod(clazz: Class<*>): Method? {
        return try {
            clazz.getMethod(ECH_METHOD_NAME, SSLSocket::class.java, ByteArray::class.java)
        } catch (e: NoSuchMethodException) {
            null
        }
    }

    /**
     * 包装 Conscrypt 的 SSLSocketFactory：在创建每个 SSLSocket 后、握手前，
     * 把目标域名已缓存的 ECHConfigList 注入进去（反射调用 setEchConfigList）。
     *
     * - 配置缺失或调用失败时仅打印日志，退化为普通 TLS（GREASE / 明文 SNI），不阻断连接。
     * - [EchInterceptor] 负责在连接前预取配置，使此处能命中缓存。
     */
    private class EchSSLSocketFactory(
        private val delegate: SSLSocketFactory,
        private val conscryptClass: Class<*>,
        private val setEchConfigList: Method,
    ) : SSLSocketFactory() {

        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

        override fun createSocket(): Socket = delegate.createSocket()

        override fun createSocket(host: String, port: Int): Socket {
            val s = delegate.createSocket(host, port)
            applyEchConfig(s as? SSLSocket, host)
            return s
        }

        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
            val s = delegate.createSocket(host, port, localHost, localPort)
            applyEchConfig(s as? SSLSocket, host)
            return s
        }

        /**
         * SocketFactory 的抽象方法重载（按 IP 建立）。
         * 注意：此入口只有 IP 没有 域名，而 ECH 配置是按域名缓存/查询的，
         * 故此处 [applyEchConfig] 命中缓存的概率极低，多半退化为普通 TLS——
         * 这符合预期：OkHttp 的主流路径走的是 [createSocket] (Socket, host, port, autoClose)，
         * 那里能拿到真实域名。此处仅用于满足抽象方法契约，避免编译/实例化失败。
         */
        override fun createSocket(host: InetAddress, port: Int): Socket {
            val s = delegate.createSocket(host, port)
            applyEchConfig(s as? SSLSocket, host.hostAddress)
            return s
        }

        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
            val s = delegate.createSocket(address, port, localAddress, localPort)
            applyEchConfig(s as? SSLSocket, address.hostAddress)
            return s
        }

        /**
         * OkHttp 建立 TLS 连接的关键入口：把已连接的 TCP 套接字包装为 SSLSocket。
         * 在返回前注入 ECH 配置，确保随后 startHandshake 时使用加密的 ClientHello。
         */
        override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
            val socket = delegate.createSocket(s, host, port, autoClose)
            applyEchConfig(socket as? SSLSocket, host)
            return socket
        }

        /** 把缓存中的 ECHConfigList（base64）解码后注入到 SSLSocket */
        private fun applyEchConfig(socket: SSLSocket?, host: String?) {
            if (socket == null || host.isNullOrEmpty()) return
            val configBase64 = EchConfigProvider.getCached(host) ?: return
            try {
                // ECHConfigList 在 [EchConfigProvider] 中以标准 base64（无换行）缓存，DEFAULT 解码即可
                val configBytes = Base64.decode(configBase64, Base64.DEFAULT)
                setEchConfigList.invoke(conscryptClass, socket, configBytes)
            } catch (t: Throwable) {
                Log.w(TAG, "应用 ECH 配置失败 host=$host：${t.message}")
            }
        }
    }
}
