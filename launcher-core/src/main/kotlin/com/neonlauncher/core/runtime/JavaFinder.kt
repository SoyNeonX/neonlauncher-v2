package com.neonlauncher.core.runtime

import com.neonlauncher.common.util.OsFamily
import com.neonlauncher.common.util.Paths2
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

data class JavaRuntime(
    val executable: Path,
    val version: String,
    val major: Int,
    val vendor: String,
    val origin: String
) {
    val label: String get() = if (vendor.isBlank()) "Java $major" else "Java $major · $vendor"

    override fun toString(): String = label
}

/**
 * Encuentra los Java instalados en la máquina. El launcher no baja JDKs: usa el que el usuario
 * tenga y avisa si no alcanza para la versión elegida.
 */
class JavaFinder {

    private val cache = mutableMapOf<String, JavaRuntime?>()

    fun detect(preferredPath: String? = null): List<JavaRuntime> {
        val candidates = LinkedHashMap<String, Path>()

        fun add(path: Path?) {
            if (path == null) return
            val normalized = runCatching { path.toAbsolutePath().normalize() }.getOrNull() ?: return
            if (!Files.isRegularFile(normalized)) return
            candidates.putIfAbsent(normalized.toString().lowercase(), normalized)
        }

        preferredPath?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { Paths.get(raw) }.getOrNull()?.let { path ->
                add(if (Files.isDirectory(path)) path.resolve("bin").resolve(Paths2.javaExecutable()) else path)
            }
        }

        System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() }?.let { home ->
            runCatching { Paths.get(home) }.getOrNull()
                ?.let { add(it.resolve("bin").resolve(Paths2.javaExecutable())) }
        }

        System.getenv("PATH")?.split(java.io.File.pathSeparator)?.forEach { dir ->
            runCatching { add(Paths.get(dir, Paths2.javaExecutable())) }
        }

        commonRoots().forEach { root ->
            runCatching {
                if (!Files.isDirectory(root)) return@runCatching
                Files.list(root).use { stream ->
                    stream.toList()
                        .filter { Files.isDirectory(it) }
                        .forEach { jdk -> add(jdk.resolve("bin").resolve(Paths2.javaExecutable())) }
                }
            }
        }

        return candidates.values.mapNotNull { probe(it) }.sortedByDescending { it.major }
    }

    /** El Java más chico que cumple la versión requerida; si ninguno cumple, el más nuevo. */
    fun pick(requiredMajor: Int, runtimes: List<JavaRuntime>): JavaRuntime? =
        runtimes.filter { it.major >= requiredMajor }.minByOrNull { it.major }
            ?: runtimes.maxByOrNull { it.major }

    private fun commonRoots(): List<Path> = when (OsFamily.current) {
        OsFamily.WINDOWS -> buildList {
            add(Paths.get("C:/Program Files/Eclipse Adoptium"))
            add(Paths.get("C:/Program Files/Java"))
            add(Paths.get("C:/Program Files/Microsoft"))
            add(Paths.get("C:/Program Files/Amazon Corretto"))
            add(Paths.get("C:/Program Files/Zulu"))
            add(Paths.get("C:/Program Files/BellSoft"))
            System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let {
                add(Paths.get(it, "Programs", "Eclipse Adoptium"))
            }
        }
        OsFamily.MACOS -> listOf(
            Paths.get("/Library/Java/JavaVirtualMachines"),
            Paths2.home.resolve("Library/Java/JavaVirtualMachines")
        )
        OsFamily.LINUX -> listOf(
            Paths.get("/usr/lib/jvm"),
            Paths.get("/usr/java"),
            Paths.get("/opt/java")
        )
    }

    private fun probe(executable: Path): JavaRuntime? {
        val key = executable.toString()
        cache[key]?.let { return it }
        val runtime = runProbe(executable)
        cache[key] = runtime
        return runtime
    }

    private fun runProbe(executable: Path): JavaRuntime? = runCatching {
        val process = ProcessBuilder(executable.toString(), "-version")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        val quoted = Regex("version \"([^\"]+)\"").find(output)?.groupValues?.get(1) ?: return null
        var major = quoted.substringBefore('.').substringBefore('-').toIntOrNull() ?: 0
        if (major == 1) {
            major = quoted.substringAfter('.').substringBefore('.').toIntOrNull() ?: 0
        }
        JavaRuntime(
            executable = executable,
            version = quoted,
            major = major,
            vendor = vendorOf(output),
            origin = executable.parent?.parent?.fileName?.toString().orEmpty()
        )
    }.getOrNull()

    private fun vendorOf(output: String): String = when {
        output.contains("Temurin") || output.contains("Adoptium") || output.contains("AdoptOpenJDK") -> "Temurin"
        output.contains("GraalVM") -> "GraalVM"
        output.contains("Microsoft") -> "Microsoft"
        output.contains("Corretto") -> "Corretto"
        output.contains("Zulu") -> "Zulu"
        output.contains("Oracle") -> "Oracle"
        output.contains("OpenJDK") -> "OpenJDK"
        else -> ""
    }
}
