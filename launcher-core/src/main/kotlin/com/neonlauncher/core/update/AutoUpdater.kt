package com.neonlauncher.core.update

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.neonlauncher.common.exception.UpdateException
import com.neonlauncher.common.util.Result
import com.neonlauncher.common.util.Result.runCatching
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant

data class ReleaseInfo(
    val version: String,
    val releaseNotes: String,
    val releaseDate: Instant,
    val downloadUrl: String,
    val sha256: String,
    val fileName: String,
    val size: Long
)

data class UpdateCheckResult(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String?,
    val releaseInfo: ReleaseInfo?
)

class AutoUpdater(
    private val currentVersion: String,
    private val updateChannel: String,
    private val okHttpClient: OkHttpClient,
    private val baseDir: File
) {
    private val logger = LoggerFactory.getLogger(AutoUpdater::class.java)

    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule())
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    // GitHub Releases API or custom endpoint
    private val updateUrl = "https://api.github.com/repos/neonlabs/NeonLauncher/releases/latest"

    fun checkForUpdates(): Result<UpdateCheckResult> = runCatching {
        val request = Request.Builder()
            .url(updateUrl)
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UpdateException("Failed to check updates: HTTP ${response.code}")
            }

            val body = response.body?.string() ?: throw UpdateException("Empty response")
            val githubRelease = objectMapper.readValue<GitHubRelease>(body)

            val latestVersion = githubRelease.tagName.removePrefix("v")
            val hasUpdate = compareVersions(latestVersion, currentVersion) > 0

            val asset = githubRelease.assets.firstOrNull { it.name.endsWith(".exe") || it.name.endsWith(".msi") }
                ?: throw UpdateException("No suitable installer asset found")

            val releaseInfo = ReleaseInfo(
                version = latestVersion,
                releaseNotes = githubRelease.body ?: "",
                releaseDate = githubRelease.publishedAt,
                downloadUrl = asset.browserDownloadUrl,
                sha256 = "", // Would need separate verification
                fileName = asset.name,
                size = asset.size
            )

            UpdateCheckResult(hasUpdate, currentVersion, if (hasUpdate) latestVersion else null, if (hasUpdate) releaseInfo else null)
        }
    }.onFailure { logger.error("Update check failed", it) }

    fun downloadAndInstall(releaseInfo: ReleaseInfo, progressListener: (Long, Long) -> Unit = { _, _ -> }): Result<Unit> = runCatching {
        val request = Request.Builder().url(releaseInfo.downloadUrl).build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UpdateException("Download failed: HTTP ${response.code}")
            }

            val totalBytes = response.body?.contentLength() ?: -1L
            val installerFile = File(baseDir, "NeonLauncher-${releaseInfo.version}-installer.${getInstallerExt()}")

            var downloadedBytes = 0L
            val inputStream = response.body?.byteStream() ?: throw UpdateException("No response body")
            val outputStream = Files.newOutputStream(installerFile.toPath())
            val buffer = ByteArray(8192)

            try {
                var bytesRead = inputStream.read(buffer)
                while (bytesRead != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    progressListener(downloadedBytes, totalBytes)
                    bytesRead = inputStream.read(buffer)
                }
            } finally {
                outputStream.close()
                inputStream.close()
            }

            // Verify hash if provided
            if (releaseInfo.sha256.isNotBlank()) {
                val actualHash = calculateSha256(installerFile)
                if (!actualHash.equals(releaseInfo.sha256, ignoreCase = true)) {
                    installerFile.delete()
                    throw UpdateException("Installer hash mismatch")
                }
            }

            // Run installer silently
            runInstaller(installerFile)
        }
    }.onFailure { logger.error("Update install failed", it) }

    private fun runInstaller(installerFile: File) {
        val os = System.getProperty("os.name").lowercase()
        val cmd = when {
            os.contains("win") && installerFile.extension == "exe" -> listOf(installerFile.absolutePath, "/SILENT", "/NORESTART")
            os.contains("win") && installerFile.extension == "msi" -> listOf("msiexec", "/i", installerFile.absolutePath, "/qn", "/norestart")
            else -> throw UpdateException("Unsupported installer format for this platform")
        }

        val pb = ProcessBuilder(*cmd.toTypedArray())
        pb.inheritIO().start().waitFor()
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        Files.newInputStream(file.toPath()).use { input ->
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun getInstallerExt(): String = when {
        System.getProperty("os.name").lowercase().contains("win") -> "exe"
        System.getProperty("os.name").lowercase().contains("mac") -> "dmg"
        else -> "AppImage"
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }

    private data class GitHubRelease(
        val tagName: String,
        val body: String?,
        val publishedAt: Instant,
        val assets: List<GitHubAsset>
    )

    private data class GitHubAsset(
        val name: String,
        val browserDownloadUrl: String,
        val size: Long
    )
}