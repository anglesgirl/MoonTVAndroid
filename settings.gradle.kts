pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 本地 AAR 仓库（用于引入 DEfO ECH 版本的 Conscrypt）
        flatDir {
            dirs 'app/libs'
        }
    }
}

rootProject.name = "MoonTVAndroid"
include ':app'
