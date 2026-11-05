package com.neonlauncher.common.util

import java.nio.file.Path
import java.nio.file.Paths

/** Nombres de SO en el formato que usan los JSON de Minecraft. */
enum class OsFamily(val id: String) {
    WINDOWS("windows"),
    MACOS("osx"),
    LINUX("linux");

    companion object {
        val current: OsFamily by lazy {
            val name = System.getProperty("os.name").lowercase()
            when {
                name.contains("win") -> WINDOWS
                name.contains("mac") || name.contains("darwin") -> MACOS
                else -> LINUX
            }
        }
    }
}

object Paths2 {

    val home: Path get() = Paths.get(System.getProperty("user.home"))

    fun javaExecutable(): String = if (OsFamily.current == OsFamily.WINDOWS) "java.exe" else "java"

    /** `%APPDATA%\NeonLauncher`, `~/Library/Application Support/NeonLauncher` o `~/.neonlauncher`. */
    fun launcherDataDir(): Path = when (OsFamily.current) {
        OsFamily.WINDOWS -> Paths.get(System.getenv("APPDATA") ?: home.toString(), "NeonLauncher")
        OsFamily.MACOS -> home.resolve("Library/Application Support/NeonLauncher")
        OsFamily.LINUX -> home.resolve(".neonlauncher")
    }

    /** Instalación de Minecraft del launcher oficial. */
    fun defaultMinecraftDir(): Path = when (OsFamily.current) {
        OsFamily.WINDOWS -> Paths.get(System.getenv("APPDATA") ?: home.toString(), ".minecraft")
        OsFamily.MACOS -> home.resolve("Library/Application Support/minecraft")
        OsFamily.LINUX -> home.resolve(".minecraft")
    }
}

fun String?.orBlank(): String = this ?: ""

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (value >= 100) "${value.toInt()} ${units[index]}" else String.format("%.1f %s", value, units[index])
}
