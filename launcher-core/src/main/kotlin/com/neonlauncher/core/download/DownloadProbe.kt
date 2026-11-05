package com.neonlauncher.core.download

import com.neonlauncher.common.util.Paths2

/**
 * Prueba del descargador: lee el catálogo y "reinstala" una versión que ya existe
 * (todo se saltea por hash). `gradlew :launcher-core:downloadProbe`
 */
fun main() {
    val catalog = MojangCatalog().versions()
    println("catálogo: ${catalog.size} versiones")
    catalog.take(3).forEach { println("  ${it.id} (${it.type}) ${it.releaseTime}") }

    val target = catalog.firstOrNull { it.id == "1.21.11" } ?: catalog.first()
    println("probando ${target.id}…")

    val result = VersionInstaller(Paths2.defaultMinecraftDir()).install(
        target,
        onStatus = { println("· $it") },
        onProgress = { }
    )
    println("resultado: $result")
}
