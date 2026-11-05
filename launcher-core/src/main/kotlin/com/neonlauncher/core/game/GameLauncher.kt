package com.neonlauncher.core.game

import com.neonlauncher.common.model.LauncherSettings
import com.neonlauncher.common.util.AppInfo
import com.neonlauncher.core.runtime.JavaRuntime
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class LaunchPlan(
    val command: List<String>,
    val workingDirectory: Path,
    val nativesDirectory: Path,
    val missingLibraries: List<String>
)

/**
 * Arma la línea de comandos y lanza el proceso de Minecraft en **modo offline**:
 * `accessToken` simbólico y el UUID derivado del nombre, que es exactamente lo que el juego
 * necesita para singleplayer. El seam para online es [PlayerProfile] / [LauncherSettings].
 */
class GameLauncher(private val mcDir: Path) {

    private val resolver = VersionResolver(mcDir)
    private val nativesRoot = mcDir.resolve("neonlauncher").resolve("natives")
    private val placeholder = Regex("\\$\\{([A-Za-z0-9_]+)}")

    fun buildPlan(
        installation: GameInstallation,
        settings: LauncherSettings,
        java: JavaRuntime,
        identity: Identity,
        gameDirOverride: Path? = null
    ): LaunchPlan {
        val gameDir = gameDirOverride
            ?: Paths.get(settings.gameDirectory?.takeIf { it.isNotBlank() } ?: mcDir.toString())
        val features = mapOf("has_custom_resolution" to true)
        val resolved = resolver.resolve(installation, features)

        val nativesDir = nativesRoot.resolve(installation.id)
        NativesExtractor.prepare(resolved.nativeJars, nativesDir)

        val classpath = resolved.classpath.joinToString(File.pathSeparator)

        val vars = buildMap {
            put("auth_player_name", identity.name)
            put("version_name", installation.id)
            put("game_directory", gameDir.toString())
            put("assets_root", mcDir.resolve("assets").toString())
            put("assets_index_name", resolved.assetIndex)
            put("auth_uuid", identity.uuid)
            put("auth_access_token", identity.accessToken)
            put("auth_session", identity.accessToken)
            put("clientid", "")
            put("auth_xuid", "")
            put("user_type", identity.userType)
            put("version_type", installation.type)
            put("natives_directory", nativesDir.toString())
            put("launcher_name", AppInfo.NAME)
            put("launcher_version", AppInfo.VERSION)
            put("classpath", classpath)
            put("classpath_separator", File.pathSeparator)
            put("library_directory", mcDir.resolve("libraries").toString())
            put("resolution_width", settings.windowWidth.toString())
            put("resolution_height", settings.windowHeight.toString())
            put("quickPlayPath", "")
        }

        val command = mutableListOf<String>()
        command += java.executable.toString()

        if (resolved.jvmArgs.none { it.startsWith("-Xmx") }) {
            command += "-Xmx${settings.memoryMb}M"
            command += "-Xms${minOf(settings.memoryMb / 4, 512)}M"
        }

        // Sin esto, el stdout del juego sale en la codificación ANSI de Windows y la consola
        // muestra acentos rotos (por ejemplo "está" -> "est?").
        if (resolved.jvmArgs.none { it.startsWith("-Dstdout.encoding") }) {
            command += "-Dstdout.encoding=UTF-8"
            command += "-Dstderr.encoding=UTF-8"
        }

        val loggingFile = resolved.loggingFileId
            ?.let { id -> mcDir.resolve("assets").resolve("log_configs").resolve(id) }
        if (resolved.loggingArgument != null && loggingFile != null && Files.isRegularFile(loggingFile)) {
            command += substitute(resolved.loggingArgument, vars + ("path" to loggingFile.toString()))
        }

        command += resolved.jvmArgs.map { substitute(it, vars) }

        if (command.none { it == "-cp" || it == "-classpath" }) {
            command += "-cp"
            command += classpath
        }

        if (settings.extraJvmArgs.isNotBlank()) {
            command += settings.extraJvmArgs.split(' ').filter { it.isNotBlank() }
        }

        command += resolved.mainClass

        val gameArguments = resolved.legacyArguments
            ?.split(' ')
            ?.filter { it.isNotBlank() }
            ?: resolved.gameArgs
        command += gameArguments.map { substitute(it, vars) }

        return LaunchPlan(command, gameDir, nativesDir, resolved.missingLibraries)
    }

    fun start(plan: LaunchPlan, onLine: (String) -> Unit, onExit: (Int) -> Unit): Process {
        Files.createDirectories(plan.workingDirectory)
        val process = ProcessBuilder(plan.command)
            .directory(plan.workingDirectory.toFile())
            .redirectErrorStream(true)
            .start()

        Thread({
            runCatching {
                process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine(onLine)
            }
        }, "neon-game-output").apply { isDaemon = true }.start()

        Thread({
            val code = runCatching { process.waitFor() }.getOrDefault(-1)
            onExit(code)
        }, "neon-game-exit").apply { isDaemon = true }.start()

        return process
    }

    private fun substitute(value: String, vars: Map<String, String>): String =
        placeholder.replace(value) { match -> vars[match.groupValues[1]] ?: "" }
}
