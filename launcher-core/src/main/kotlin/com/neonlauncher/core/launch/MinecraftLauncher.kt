package com.neonlauncher.core.launch

import com.neonlauncher.common.exception.LaunchException
import com.neonlauncher.common.model.Manifest
import com.neonlauncher.common.model.Profile
import com.neonlauncher.common.util.Result
import com.neonlauncher.common.util.Result.runCatching
import com.neonlauncher.core.java.JavaRuntime
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class MinecraftLauncher(
    private val gameDir: File,
    private val librariesDir: File,
    private val assetsDir: File,
    private val nativesDir: File
) {
    private val logger = LoggerFactory.getLogger(MinecraftLauncher::class.java)

    data class LaunchConfig(
        val javaRuntime: JavaRuntime,
        val manifest: Manifest,
        val profile: Profile,
        val accessToken: String,
        val extraJvmArgs: List<String> = emptyList(),
        val extraGameArgs: List<String> = emptyList()
    )

    fun launch(config: LaunchConfig): Result<Process> = runCatching {
        val command = buildCommand(config)
        logger.info("Launching Minecraft with command: {}", command.joinToString(" "))

        val pb = ProcessBuilder(command)
            .directory(gameDir)
            .redirectErrorStream(true)

        // Set environment variables
        pb.environment()["JAVA_HOME"] = config.javaRuntime.path.absolutePath

        val process = pb.start()

        // Start log reader thread
        Thread(process.inputStream::readAllBytes) {
            val output = String(it).trim()
            if (output.isNotBlank()) logger.info("[MC] {}", output)
        }.start()

        process
    }.onFailure { logger.error("Launch failed", it) }

    private fun buildCommand(config: LaunchConfig): List<String> {
        val cmd = mutableListOf<String>()

        // Java executable
        cmd.add(config.javaRuntime.getExecutable().absolutePath)

        // JVM args from manifest
        cmd.addAll(config.manifest.jvmArgs)

        // Memory
        cmd.add("-Xmx${config.profile.memoryMax}M")
        cmd.add("-Xms${min(config.profile.memoryMax / 4, 512)}M")

        // GC
        cmd.add("-XX:+UseG1GC")
        cmd.add("-XX:+ParallelRefProcEnabled")
        cmd.add("-XX:MaxGCPauseMillis=200")
        cmd.add("-XX:+UnlockExperimentalVMOptions")
        cmd.add("-XX:+DisableExplicitGC")
        cmd.add("-XX:+AlwaysPreTouch")

        // Natives
        cmd.add("-Djava.library.path=${nativesDir.absolutePath}")

        // Classpath
        val classpath = buildClasspath(config.manifest)
        cmd.add("-cp")
        cmd.add(classpath)

        // Main class
        cmd.add(config.manifest.mainClass)

        // Game args from manifest
        cmd.addAll(config.manifest.gameArgs)

        // Auth args
        cmd.add("--username")
        cmd.add(config.profile.microsoftAccount?.username ?: "Player")
        cmd.add("--uuid")
        cmd.add(config.profile.microsoftAccount?.uuid ?: "00000000-0000-0000-0000-000000000000")
        cmd.add("--accessToken")
        cmd.add(config.accessToken)
        cmd.add("--version")
        cmd.add(config.manifest.version)
        cmd.add("--gameDir")
        cmd.add(gameDir.absolutePath)
        cmd.add("--assetsDir")
        cmd.add(assetsDir.absolutePath)

        config.manifest.assetIndex?.let { assetIndex ->
            cmd.add("--assetIndex")
            cmd.add(assetIndex)
        }

        // Server config
        cmd.add("--server")
        cmd.add(config.manifest.config.serverIp)
        cmd.add("--port")
        cmd.add(config.manifest.config.serverPort.toString())

        // Custom args
        cmd.addAll(config.profile.customJvmArgs)
        cmd.addAll(config.extraJvmArgs)
        cmd.addAll(config.extraGameArgs)

        return cmd
    }

    private fun buildClasspath(manifest: Manifest): String {
        val paths = mutableListOf<String>()

        // Libraries from manifest
        manifest.libraries.forEach { lib ->
            val artifactPath = resolveLibraryPath(lib.name)
            if (artifactPath.exists()) {
                paths.add(artifactPath.absolutePath)
            }
        }

        // Forge/Fabric loader jars
        manifest.libraries.filter { it.name.contains("forge") || it.name.contains("fabric") || it.name.contains("quilt") }
            .forEach { lib ->
                val artifactPath = resolveLibraryPath(lib.name)
                if (artifactPath.exists()) paths.add(artifactPath.absolutePath)
            }

        // Mods
        manifest.mods.filter { it.required }.forEach { mod ->
            val modFile = File(gameDir, mod.file)
            if (modFile.exists()) paths.add(modFile.absolutePath)
        }

        // Minecraft client jar
        val clientJar = librariesDir.resolve("com/mojang/minecraft/${manifest.minecraftVersion}/minecraft-${manifest.minecraftVersion}-client.jar")
        if (clientJar.toFile().exists()) paths.add(clientJar.toString())

        return paths.joinToString(File.pathSeparator)
    }

    private fun resolveLibraryPath(mavenCoord: String): Path {
        // Parse maven coordinate: group:artifact:version[:classifier]
        val parts = mavenCoord.split(":")
        val group = parts[0].replace(".", "/")
        val artifact = parts[1]
        val version = parts[2]
        val classifier = parts.getOrElse(3) { "" }

        val fileName = if (classifier.isNotBlank()) "$artifact-$version-$classifier.jar" else "$artifact-$version.jar"
        return librariesDir.resolve("$group/$artifact/$version/$fileName")
    }

    fun prepareNatives(manifest: Manifest): Result<Unit> = runCatching {
        nativesDir.mkdirs()

        manifest.libraries
            .filter { it.natives != null }
            .forEach { lib ->
                val nativeJar = resolveLibraryPath(lib.name)
                if (Files.exists(nativeJar)) {
                    extractNatives(nativeJar, lib.natives!!)
                }
            }
    }.onFailure { logger.error("Failed to prepare natives", it) }

    private fun extractNatives(nativeJar: Path, nativesMap: Map<String, String>) {
        // Extract natives for current platform
        val os = when {
            System.getProperty("os.name").lowercase().contains("win") -> "windows"
            System.getProperty("os.name").lowercase().contains("mac") -> "macos"
            else -> "linux"
        }
        val arch = System.getProperty("os.arch").lowercase().let {
            when (it) {
                "amd64", "x86_64" -> "x86_64"
                "aarch64", "arm64" -> "arm64"
                else -> it
            }
        }

        val classifier = nativesMap[os]?.replace("${arch}", arch)
        if (classifier == null) return

        val classifiedJar = nativeJar.resolveSibling(nativeJar.fileName.toString().replace(".jar", "-$classifier.jar"))
        if (!Files.exists(classifiedJar)) return

        // Use Java's built-in zip extraction
        java.util.zip.ZipFile(classifiedJar.toFile()).use { zipFile ->
            zipFile.entries().asSequence()
                .filter { !it.isDirectory && !it.name.startsWith("META-INF/") }
                .forEach { entry ->
                    val targetFile = nativesDir.resolve(entry.name)
                    targetFile.parent?.toFile()?.mkdirs()
                    zipFile.getInputStream(entry).use { input ->
                        Files.copy(input, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    }
                }
        }
    }
}