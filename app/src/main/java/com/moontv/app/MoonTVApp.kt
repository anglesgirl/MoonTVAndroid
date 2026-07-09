package com.moontv.app

import android.app.Application
import com.moontv.app.data.repository.ConfigRepository
import com.moontv.app.net.ech.EchProvider

/**
 * 应用入口
 *
 * 在此初始化 ECH Provider（尝试加载 DEfO Conscrypt）
 */
class MoonTVApp : Application() {

    val configRepository by lazy { ConfigRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // 初始化 ECH：尝试加载 DEfO fork，失败则降级标准 TLS
        EchProvider.initialize()
    }
}
