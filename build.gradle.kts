plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin), so no org.jetbrains.kotlin.android plugin.
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
