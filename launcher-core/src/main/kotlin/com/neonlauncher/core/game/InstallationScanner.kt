package com.neonlauncher.core.game

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Busca las versiones instaladas dentro de un `.minecraft` (el del launcher oficial o el que
 * el usuario elija). Sólo se listan versiones lanzables: JSON legible + jar del cliente presente.
 */
class InstallationScanner(private val mcDir: Path) {

    val versionsDir: Path get() = mcDir.resolve("versions")

    fun scan(): List<GameInstallation> {
        if (!Files.isDirectory(versionsDir)) return emptyList()
        val candidates = Files.list(versionsDir).use { it.toList() }
            .filter { Files.isDirectory(it) }
            .mapNotNull { dir -> runCatching { read(dir) }.getOrNull() }

        return candidates.sortedWith(
            compareByDescending<GameInstallation> { it.releaseTime ?: Instant.EPOCH }
                .thenByDescending { it.id }
        )
    }

    private fun read(dir: Path): GameInstallation? {
        val id = dir.fileName.toString()
        if (!Files.isRegularFile(dir.resolve("$id.json"))) return null

        val chain = VersionStore.chain(mcDir, id)
        if (chain.isEmpty()) return null

        val self = chain.last()
        val base = chain.firstOrNull { it.downloads?.client != null } ?: chain.first()
        val baseId = base.id.ifBlank { id }

        // Alcanza con que exista el jar propio (Fabric suele traer uno completo) o el base.
        val baseJar = versionsDir.resolve(baseId).resolve("$baseId.jar")
        val ownJar = versionsDir.resolve(id).resolve("$id.jar")
        if (!Files.isRegularFile(baseJar) && !Files.isRegularFile(ownJar)) return null

        val javaMajor = chain.asReversed().firstNotNullOfOrNull { it.javaVersion?.majorVersion } ?: 21
        val releaseTime = VersionStore.parseInstant(self.releaseTime)
            ?: VersionStore.parseInstant(base.releaseTime)

        return GameInstallation(
            id = id,
            directory = dir,
            baseId = baseId,
            type = self.type.ifBlank { "release" },
            releaseTime = releaseTime,
            javaMajor = javaMajor,
            loader = loaderOf(id)
        )
    }

    private fun loaderOf(id: String): String? = when {
        id.startsWith("fabric-loader-") -> {
            val version = id.removePrefix("fabric-loader-").substringBeforeLast("-", "")
            if (version.isBlank()) "Fabric" else "Fabric $version"
        }
        id.contains("neoforge", ignoreCase = true) -> "NeoForge"
        id.contains("forge", ignoreCase = true) -> "Forge"
        id.contains("quilt", ignoreCase = true) -> "Quilt"
        else -> null
    }
}
