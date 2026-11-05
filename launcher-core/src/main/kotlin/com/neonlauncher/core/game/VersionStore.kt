package com.neonlauncher.core.game

import com.neonlauncher.common.util.Json
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Lectura de los JSON de versión que ya existen en disco. */
object VersionStore {

    private const val MAX_CHAIN = 8

    fun read(mcDir: Path, id: String): VersionJson? {
        val file = mcDir.resolve("versions").resolve(id).resolve("$id.json")
        if (!Files.isRegularFile(file)) return null
        return runCatching { Json.read(file, VersionJson::class.java) }.getOrNull()
    }

    /**
     * Cadena de herencia, de la raíz al hijo: `[1.21.11, fabric-loader-0.18.4-1.21.11]`.
     * Así el hijo puede sobrescribir `mainClass` y sumar librerías/argumentos.
     */
    fun chain(mcDir: Path, id: String): List<VersionJson> {
        val result = mutableListOf<VersionJson>()
        val seen = mutableSetOf<String>()
        var current: String? = id
        while (current != null && seen.add(current) && result.size < MAX_CHAIN) {
            val json = read(mcDir, current) ?: break
            result.add(0, json)
            current = json.inheritsFrom?.takeIf { it.isNotBlank() }
        }
        return result
    }

    /** `2025-12-09T12:23:30+00:00` y `2026-03-08T10:09:55+0000` (Fabric) conviven. */
    fun parseInstant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(raw.take(19)).toInstant(ZoneOffset.UTC)
            }.getOrNull()
    }
}
