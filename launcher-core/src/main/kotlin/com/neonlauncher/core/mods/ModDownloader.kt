package com.neonlauncher.core.mods

import com.neonlauncher.common.exception.HashMismatchException
import com.neonlauncher.common.exception.ModDownloadException
import com.neonlauncher.common.model.Manifest
import com.neonlauncher.common.util.Result
import com.neonlauncher.common.util.Result.runCatching
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ModDownloader(
    private val modsDir: File,
    private val okHttpClient: OkHttpClient,
    private val maxThreads: Int = 4,
    private val verifyHashes: Boolean = true
) {
    private val logger = LoggerFactory.getLogger(ModDownloader::class.java)
    private val executor: ExecutorService = Executors.newFixedThreadPool(maxThreads)

    data class DownloadResult(
        val modId: String,
        val success: Boolean,
        val file: File? = null,
        val error: String? = null
    )

    data class ProgressUpdate(
        val modId: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val completed: Boolean
    )

    fun downloadMods(
        mods: List<Manifest.ModEntry>,
        baseUrl: String,
        progressListener: (ProgressUpdate) -> Unit = {}
    ): List<DownloadResult> {
        val futures = mods.map { mod ->
            CompletableFuture.supplyAsync({
                downloadMod(mod, baseUrl, progressListener)
            }, executor)
        }

        return futures.map { it.join() }
    }

    private fun downloadMod(
        mod: Manifest.ModEntry,
        baseUrl: String,
        progressListener: (ProgressUpdate) -> Unit
    ): DownloadResult = runCatching {
        val url = if (mod.url.startsWith("http")) mod.url else "$baseUrl${mod.file}"
        val request = Request.Builder().url(url).build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ModDownloadException("HTTP ${response.code}: ${response.message}")
            }

            val totalBytes = response.body?.contentLength() ?: -1L
            val tempFile = File(modsDir, "${mod.id}.jar.tmp")
            val targetFile = File(modsDir, "${mod.file}").apply { parentFile?.mkdirs() }

            var downloadedBytes = 0L
            val inputStream = response.body?.byteStream() ?: throw ModDownloadException("No response body")
            val outputStream = Files.newOutputStream(tempFile.toPath())
            val buffer = ByteArray(8192)

            try {
                var bytesRead = inputStream.read(buffer)
                while (bytesRead != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        progressListener(ProgressUpdate(mod.id, downloadedBytes, totalBytes, false))
                    }
                    bytesRead = inputStream.read(buffer)
                }
            } finally {
                outputStream.close()
                inputStream.close()
            }

            if (verifyHashes) {
                val actualHash = calculateSha256(tempFile)
                if (!actualHash.equals(mod.sha256, ignoreCase = true)) {
                    tempFile.delete()
                    throw HashMismatchException(
                        "Hash mismatch for ${mod.id}: expected ${mod.sha256}, got $actualHash"
                    )
                }
            }

            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            progressListener(ProgressUpdate(mod.id, totalBytes, totalBytes, true))

            DownloadResult(mod.id, true, targetFile)
        }.getOrElse { e ->
            logger.error("Failed to download ${mod.id}", e)
            DownloadResult(mod.id, false, error = e.message)
        }
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

    fun shutdown() {
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)
    }
}