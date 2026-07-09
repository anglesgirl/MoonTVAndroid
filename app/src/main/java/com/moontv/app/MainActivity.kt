package com.moontv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moontv.app.config.SiteConfig
import com.moontv.app.ui.DetailScreen
import com.moontv.app.ui.HomeScreen
import com.moontv.app.ui.MainViewModel
import com.moontv.app.ui.NavRoutes
import com.moontv.app.ui.PlayerScreen
import com.moontv.app.ui.SettingsScreen

/**
 * 应用入口 Activity
 *
 * 负责：
 * - 从 [MoonTVApp.configRepository] 读取站点配置并以 Compose State 形式收集
 * - 创建 [MainViewModel] 并在配置变化时调用 [MainViewModel.updateConfig]
 * - 通过 NavHost 搭建 home / detail / player / settings 四个页面路由
 *
 * 导航参数一律使用字符串（source、id）与整型（episodeIndex），
 * 不通过 Parcelable 传递，保证路由可序列化、可深链接。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 从 Application 拿到配置仓库（已由 MoonTVApp 懒加载）
        val configRepository = (application as MoonTVApp).configRepository

        setContent {
            MoonTVTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()

                    // 收集配置流，初始为空壳配置
                    val config by configRepository.configFlow
                        .collectAsState(initial = SiteConfig.EMPTY)

                    // ViewModel 生命周期绑定到当前 Activity
                    val viewModel: MainViewModel = viewModel()

                    // 配置发生变化时同步给 ViewModel（搜索/详情均依赖最新配置）
                    LaunchedEffect(config) {
                        viewModel.updateConfig(config)
                    }

                    NavHost(
                        navController = navController,
                        startDestination = NavRoutes.HOME
                    ) {
                        // 首页：搜索 + 热门
                        composable(NavRoutes.HOME) {
                            HomeScreen(
                                viewModel = viewModel,
                                config = config,
                                onNavigateToDetail = { source, id ->
                                    navController.navigate(NavRoutes.detail(source, id))
                                },
                                onNavigateToSettings = {
                                    navController.navigate(NavRoutes.SETTINGS)
                                }
                            )
                        }

                        // 详情页：source 与 id 均为字符串
                        composable(
                            route = NavRoutes.DETAIL,
                            arguments = listOf(
                                navArgument(NavRoutes.Args.SOURCE) {
                                    type = NavType.StringType
                                },
                                navArgument(NavRoutes.Args.ID) {
                                    type = NavType.StringType
                                }
                            )
                        ) { backStackEntry ->
                            val args = backStackEntry.arguments
                            val source = args?.getString(NavRoutes.Args.SOURCE).orEmpty()
                            val id = args?.getString(NavRoutes.Args.ID).orEmpty()
                            DetailScreen(
                                viewModel = viewModel,
                                source = source,
                                id = id,
                                onPlayEpisode = { episodeIndex ->
                                    navController.navigate(
                                        NavRoutes.player(source, id, episodeIndex)
                                    )
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // 播放页：episodeIndex 为整型
                        composable(
                            route = NavRoutes.PLAYER,
                            arguments = listOf(
                                navArgument(NavRoutes.Args.SOURCE) {
                                    type = NavType.StringType
                                },
                                navArgument(NavRoutes.Args.ID) {
                                    type = NavType.StringType
                                },
                                navArgument(NavRoutes.Args.EPISODE_INDEX) {
                                    type = NavType.IntType
                                }
                            )
                        ) { backStackEntry ->
                            val args = backStackEntry.arguments
                            val source = args?.getString(NavRoutes.Args.SOURCE).orEmpty()
                            val id = args?.getString(NavRoutes.Args.ID).orEmpty()
                            val episodeIndex = args?.getInt(NavRoutes.Args.EPISODE_INDEX) ?: 0
                            PlayerScreen(
                                viewModel = viewModel,
                                source = source,
                                id = id,
                                episodeIndex = episodeIndex,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // 设置页
                        composable(NavRoutes.SETTINGS) {
                            SettingsScreen(
                                configRepository = configRepository,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 应用主题：统一使用 Material3 深色配色方案（影视类 App 更沉浸）
 */
@Composable
private fun MoonTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content
    )
}
