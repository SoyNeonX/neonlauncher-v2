package com.neonlauncher.core.mods

import java.nio.file.Files
import java.nio.file.Path

data class ModFile(
    val file: Path,
    val displayName: String,
    val size: Long,
    val enabled: Boolean
)

/**
 * Mods presentes en `<gameDir>/mods`. Deshabilitar es renombrar a `.jar.disabled`, el truco de
 * siempre: el loader ignora todo lo que no termine en `.jar`.
 */
class ModRepository(private val modsDir: Path) {

    fun exists(): Boolean = Files.isDirectory(modsDir)

    fun list(): List<ModFile> {
        if (!Files.isDirectory(modsDir)) return emptyList()
        return Files.list(modsDir).use { it.toList() }
            .mapNotNull { path -> runCatching { toMod(path) }.getOrNull() }
            .sortedBy { it.displayName.lowercase() }
    }

    fun toggle(mod: ModFile): Boolean = runCatching {
        val name = mod.file.fileName.toString()
        val target = if (mod.enabled) {
            mod.file.resolveSibling("$name.disabled")
        } else {
            mod.file.resolveSibling(name.removeSuffix(".disabled"))
        }
        Files.move(mod.file, target)
        true
    }.getOrDefault(false)

    private fun toMod(path: Path): ModFile? {
        if (!Files.isRegularFile(path)) return null
        val name = path.fileName.toString()
        return when {
            name.endsWith(".jar.disabled", ignoreCase = true) ->
                ModFile(path, name.dropLast(".disabled".length), Files.size(path), false)

            name.endsWith(".jar", ignoreCase = true) ->
                ModFile(path, name, Files.size(path), true)

            else -> null
        }
    }
}
