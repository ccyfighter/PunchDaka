// 目标路径: build.gradle.kts  (项目根目录)
// 注意：只声明插件 id，不写版本，版本统一在 settings.gradle.kts 之外由 app 模块声明
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
