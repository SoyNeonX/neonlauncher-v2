package com.neonlauncher.core.runtime

import com.neonlauncher.common.util.Paths2
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.zip.ZipFile

/**
 * Baja un JDK de Adoptium y lo deja en `%APPDATA%\NeonLauncher\jdk`.
 * Es la respuesta al "no tenés Java" sin mandar al usuario a navegar.
 */
object JavaInstaller {

    private val http: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    fun urlFor(major: Int): String {
        val arch = if (System.getProperty("os.arch").lowercase().contains("aarch64")) "aarch64" else "x64"
        return "https://api.adoptium.net/v3/binary/latest/$major/ga/windows/$arch/jdk/hotspot/normal/eclipse"
    }

    fun install(major: Int, onProgress: (Int) -> Unit): Result<Path> = runCatching {
        val root = Paths2.launcherDataDir().resolve("jdk")
        Files.createDirectories(root)

        val archive = root.resolve("jdk-$major.zip")
        download(urlFor(major), archive, onProgress)

        onProgress(100)
        val extracted = unzip(archive, root)
        runCatching { Files.deleteIfExists(archive) }

        findJavaHome(extracted) ?: error("El paquete de Java no tenía bin/java.exe")
    }

    private fun download(url: String, target: Path, onProgress: (Int) -> Unit) {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(10))
            .header("User-Agent", "NeonLauncher")
            .GET()
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) error("HTTP ${response.statusCode()} al bajar Java")

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
                    if (total > 0) onProgress(((written * 100) / total).toInt())
                }
            }
        }
    }

    private fun unzip(archive: Path, destination: Path): Path {
        ZipFile(archive.toFile()).use { zip ->
            for (entry in zip.entries()) {
                val out = destination.resolve(entry.name).normalize()
                if (!out.startsWith(destination)) continue
                if (entry.isDirectory) {
                    Files.createDirectories(out)
                } else {
                    out.parent?.let { Files.createDirectories(it) }
                    zip.getInputStream(entry).use { input ->
                        Files.copy(input, out, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }
        return destination
    }

    private fun findJavaHome(root: Path): Path? {
        val executable = Paths2.javaExecutable()
        Files.walk(root, 4).use { stream ->
            return stream
                .filter { Files.isRegularFile(it) && it.fileName.toString().equals(executable, true) }
                .map { it.parent.parent }
                .findFirst()
                .orElse(null)
        }
    }
}
