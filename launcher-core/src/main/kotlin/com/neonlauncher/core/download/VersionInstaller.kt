package com.neonlauncher.core.download

import com.neonlauncher.common.util.Json
import com.neonlauncher.common.util.OsFamily
import com.neonlauncher.core.game.Rules
import com.neonlauncher.core.game.VersionJson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Instala una versión de Minecraft desde el catálogo oficial: JSON, jar del cliente,
 * librerías, índice de assets y todos los objetos.
 *
 * Es idempotente: todo lo que ya existe con el hash correcto se saltea, así reintentar
 * después de un corte no baja nada de nuevo.
 */
class VersionInstaller(
    private val mcDir: Path,
    private val threads: Int = 8
) {

    private data class Task(val url: String, val target: Path, val sha1: String)

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun install(
        remote: RemoteVersion,
        onStatus: (String) -> Unit,
        onProgress: (Double) -> Unit
    ): Result<Unit> = runCatching {
        val versionsDir = mcDir.resolve("versions").resolve(remote.id)
        Files.createDirectories(versionsDir)

        onStatus("Descargando el manifiesto de ${remote.id}…")
        val jsonFile = versionsDir.resolve("${remote.id}.json")
        download(Task(remote.url, jsonFile, ""))
        installFromJson(Json.read(jsonFile, VersionJson::class.java), remote.id, onStatus, onProgress)
    }

    /** Verifica y rebaja lo que falte de una versión ya instalada, sin tocar nada más. */
    fun repair(
        versionId: String,
        onStatus: (String) -> Unit,
        onProgress: (Double) -> Unit
    ): Result<Unit> = runCatching {
        val jsonFile = mcDir.resolve("versions").resolve(versionId).resolve("$versionId.json")
        if (!Files.isRegularFile(jsonFile)) error("No encontré el manifiesto de $versionId")
        installFromJson(Json.read(jsonFile, VersionJson::class.java), versionId, onStatus, onProgress)
    }

    private fun installFromJson(
        versionJson: VersionJson,
        versionId: String,
        onStatus: (String) -> Unit,
        onProgress: (Double) -> Unit
    ) {
        val versionsDir = mcDir.resolve("versions").resolve(versionId)
        Files.createDirectories(versionsDir)

        val tasks = mutableListOf<Task>()

        versionJson.downloads?.client?.url?.let { url ->
            tasks.add(Task(url, versionsDir.resolve("$versionId.jar"), versionJson.downloads.client.sha1.orEmpty()))
        }

        onStatus("Revisando librerías…")
        versionJson.libraries.forEach { library ->
            if (!Rules.allows(library.rules, emptyMap())) return@forEach

            val artifact = library.downloads?.artifact
            if (artifact?.url != null && artifact.path != null) {
                tasks.add(Task(artifact.url, mcDir.resolve("libraries").resolve(artifact.path), artifact.sha1.orEmpty()))
            }

            val nativesKey = library.natives[OsFamily.current.id]
            if (!nativesKey.isNullOrBlank()) {
                val classifier = nativesKey.replace("\${arch}", Rules.architecture())
                val native = library.downloads?.classifiers?.get(classifier)
                if (native?.url != null && native.path != null) {
                    tasks.add(Task(native.url, mcDir.resolve("libraries").resolve(native.path), native.sha1.orEmpty()))
                }
            }
        }

        var assetIndexFile: Path? = null
        versionJson.assetIndex?.url?.let { url ->
            val target = mcDir.resolve("assets").resolve("indexes").resolve("${versionJson.assetIndex.id}.json")
            tasks.add(Task(url, target, versionJson.assetIndex.sha1.orEmpty()))
            assetIndexFile = target
        }

        onStatus("Descargando ${tasks.size} archivos del juego…")
        runBatch(tasks, 0.0, 0.55, onProgress)

        assetIndexFile?.let { indexFile ->
            if (Files.isRegularFile(indexFile)) {
                val objects = Json.mapper.readTree(indexFile.toFile()).path("objects")
                val objectTasks = mutableListOf<Task>()
                objects.fields().forEach { (_, value) ->
                    val hash = value.path("hash").asText("")
                    if (hash.length >= 2) {
                        val target = mcDir.resolve("assets").resolve("objects")
                            .resolve(hash.substring(0, 2)).resolve(hash)
                        objectTasks.add(
                            Task("https://resources.download.minecraft.net/${hash.substring(0, 2)}/$hash", target, hash)
                        )
                    }
                }
                onStatus("Descargando ${objectTasks.size} assets (puede tardar)…")
                runBatch(objectTasks, 0.55, 1.0, onProgress)
            }
        }

        onStatus("Listo")
        onProgress(1.0)
    }

    // ------------------------------------------------------------ descarga

    private fun runBatch(tasks: List<Task>, from: Double, to: Double, onProgress: (Double) -> Unit) {
        if (tasks.isEmpty()) return
        val pool = Executors.newFixedThreadPool(threads)
        val done = AtomicInteger(0)
        val total = tasks.size

        try {
            tasks.forEach { task ->
                pool.submit {
                    runCatching { download(task) }
                    val finished = done.incrementAndGet()
                    val fraction = from + (to - from) * (finished.toDouble() / total)
                    onProgress(fraction)
                }
            }
        } finally {
            pool.shutdown()
            pool.awaitTermination(30, TimeUnit.MINUTES)
        }
    }

    private fun download(task: Task) {
        Files.createDirectories(task.target.parent)

        if (Files.isRegularFile(task.target)) {
            if (task.sha1.isBlank() || sha1(task.target).equals(task.sha1, ignoreCase = true)) return
        }

        val temp = task.target.resolveSibling("${task.target.fileName}.part")
        val request = HttpRequest.newBuilder(URI.create(task.url))
            .timeout(Duration.ofMinutes(5))
            .header("User-Agent", "NeonLauncher")
            .GET()
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) error("HTTP ${response.statusCode()} en ${task.url}")

        response.body().use { input ->
            Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING)
        }

        if (task.sha1.isNotBlank() && !sha1(temp).equals(task.sha1, ignoreCase = true)) {
            Files.deleteIfExists(temp)
            error("Hash inválido en ${task.target.fileName}")
        }
        Files.move(temp, task.target, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun sha1(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-1")
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
}
