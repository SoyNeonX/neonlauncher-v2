package com.neonlauncher.core.manifest

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.neonlauncher.common.exception.ManifestException
import com.neonlauncher.common.model.Manifest
import com.neonlauncher.common.util.Result
import com.neonlauncher.common.util.Result.runCatching
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class ManifestManager(
    private val configDir: File,
    private val okHttpClient: OkHttpClient
) {
    private val logger = LoggerFactory.getLogger(ManifestManager::class.java)

    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule())
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.INDENT_OUTPUT, true)
    }

    private val manifestFile: File = File(configDir, "manifest.json")
    private val manifestCacheFile: File = File(configDir, "manifest.cache.json")

    fun loadLocalManifest(): Result<Manifest> = runCatching {
        if (!manifestFile.exists()) {
            throw ManifestException("Local manifest not found")
        }
        val json = Files.readString(manifestFile.toPath())
        objectMapper.readValue<Manifest>(json)
    }.onFailure { logger.error("Failed to load local manifest", it) }

    fun saveLocalManifest(manifest: Manifest): Result<Unit> = runCatching {
        val json = objectMapper.writeValueAsString(manifest)
        Files.writeString(manifestFile.toPath(), json)
    }.onFailure { logger.error("Failed to save local manifest", it) }

    fun fetchRemoteManifest(manifestUrl: String, etag: String? = null): Result<ManifestFetchResult> = runCatching {
        val requestBuilder = Request.Builder().url(manifestUrl)
        etag?.let { requestBuilder.header("If-None-Match", it) }

        val request = requestBuilder.build()
        okHttpClient.newCall(request).execute().use { response ->
            when (response.code) {
                200 -> {
                    val body = response.body?.string() ?: throw ManifestException("Empty response body")
                    val newEtag = response.header("ETag")
                    val manifest = objectMapper.readValue<Manifest>(body)
                    ManifestFetchResult(manifest, newEtag, false)
                }
                304 -> {
                    logger.info("Manifest not modified (304)")
                    val cached = loadLocalManifest().getOrThrow()
                    ManifestFetchResult(cached, etag, true)
                }
                else -> throw ManifestException("HTTP ${response.code}: ${response.message}")
            }
        }
    }.onFailure { logger.error("Failed to fetch remote manifest", it) }

    fun getLocalManifestVersion(): String? = loadLocalManifest().getOrNull()?.version

    data class ManifestFetchResult(
        val manifest: Manifest,
        val etag: String?,
        val fromCache: Boolean
    )
}

class ManifestDiffCalculator {
    data class DiffResult(
        val newMods: List<Manifest.ModEntry>,
        val updatedMods: List<Manifest.ModEntry>,
        val orphanMods: List<Manifest.ModEntry>,
        val unchangedMods: List<Manifest.ModEntry>
    )

    fun calculateDiff(local: Manifest?, remote: Manifest): DiffResult {
        val localMods = local?.mods?.associateBy { it.id } ?: emptyMap()
        val remoteMods = remote.mods.associateBy { it.id }

        val newMods = mutableListOf<Manifest.ModEntry>()
        val updatedMods = mutableListOf<Manifest.ModEntry>()
        val unchangedMods = mutableListOf<Manifest.ModEntry>()

        remoteMods.forEach { (id, remoteMod) ->
            localMods[id]?.let { localMod ->
                if (localMod.sha256 != remoteMod.sha256) {
                    updatedMods.add(remoteMod)
                } else {
                    unchangedMods.add(remoteMod)
                }
            } ?: newMods.add(remoteMod)
        }

        val orphanMods = localMods.values.filter { it.id !in remoteMods }.toList()

        return DiffResult(newMods, updatedMods, orphanMods, unchangedMods)
    }
}