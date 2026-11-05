package com.neonlauncher.core.game

import java.nio.file.Path
import java.time.Instant

/** Una versión instalada y lista para lanzar (vanilla o con loader). */
data class GameInstallation(
    val id: String,
    val directory: Path,
    val baseId: String,
    val type: String,
    val releaseTime: Instant?,
    val javaMajor: Int,
    val loader: String?
) {
    val isVanilla: Boolean get() = loader == null

    override fun toString(): String = id
}
