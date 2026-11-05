package com.neonlauncher.core.update

import com.neonlauncher.common.util.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration

data class LauncherUpdate(
    val version: String,
    val notes: String,
    val downloadUrl: String,
    val sha256: String
)

/**
 * Actualizaciones del launcher leyendo **GitHub Releases** del propio repositorio.
 *
 * Convención de publicación (ver `docs/UPDATES.md`):
 * - tag `v2.1.0`
 * - asset `NeonLauncher-2.1.0-Setup.exe` (el instalador)
 * - notas en el cuerpo del release
 *
 * Si [REPOSITORY] está vacío, no chequea nada (modo desarrollo).
 */
object UpdateChecker {

    /** owner/repo del que se leen los releases. */
    const val REPOSITORY: String = "SoyNeonX/neonlauncher-v2"

    private const val API = "https://api.github.com/repos"

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /** Devuelve la release nueva, o null si ya estás al día. */
    fun check(current: String): LauncherUpdate? {
        if (REPOSITORY.isBlank()) return null

        val request = HttpRequest.newBuilder(URI.create("$API/$REPOSITORY/releases/latest"))
            .timeout(Duration.ofSeconds(12))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "NeonLauncher")
            .GET()
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) return null

        val node = Json.mapper.readTree(response.body())
        val version = node.path("tag_name").asText("").removePrefix("v").trim()
        if (version.isBlank() || !isNewer(version, current)) return null

        val installer = node.path("assets")
            .firstOrNull { it.path("name").asText("").endsWith(".exe", ignoreCase = true) }
            ?: return null

        val digest = installer.path("digest").asText("").removePrefix("sha256:")

        return LauncherUpdate(
            version = version,
            notes = node.path("body").asText(""),
            downloadUrl = installer.path("browser_download_url").asText(""),
            sha256 = digest
        )
    }

    /** Baja el instalador a [target] con progreso 0..1 y verifica el hash si GitHub lo publica. */
    fun download(
        update: LauncherUpdate,
        target: Path,
        onProgress: (Double) -> Unit = {}
    ): Result<Path> = runCatching {
        target.parent?.let { Files.createDirectories(it) }

        val request = HttpRequest.newBuilder(URI.create(update.downloadUrl))
            .timeout(Duration.ofMinutes(10))
            .header("User-Agent", "NeonLauncher")
            .GET()
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) error("HTTP ${response.statusCode()} al bajar el instalador")

        val total = response.headers().firstValueAsLong("content-length").orElse(-1L)
        var written = 0L

        response.body().use { input ->
            Files.newOutputStream(target).use { output ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    written += read
                    if (total > 0) onProgress(written.toDouble() / total)
                }
            }
        }

        if (update.sha256.isNotBlank()) {
            val actual = sha256(target)
            if (!actual.equals(update.sha256, ignoreCase = true)) {
                Files.deleteIfExists(target)
                error("El hash del instalador no coincide")
            }
        }
        onProgress(1.0)
        target
    }

    /** Abre el instalador. El launcher se cierra para que pueda reemplazar los archivos. */
    fun launchInstaller(file: Path): Result<Unit> = runCatching {
        ProcessBuilder(file.toAbsolutePath().toString())
            .directory(file.toAbsolutePath().parent.toFile())
            .inheritIO()
            .start()
        Unit
    }

    private fun sha256(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}
