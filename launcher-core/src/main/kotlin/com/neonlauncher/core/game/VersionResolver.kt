package com.neonlauncher.core.game

import com.fasterxml.jackson.databind.JsonNode
import com.neonlauncher.common.util.OsFamily
import java.nio.file.Files
import java.nio.file.Path

/** Todo lo necesario para armar la línea de comandos de una versión. */
data class ResolvedVersion(
    val mainClass: String,
    val classpath: List<Path>,
    val missingLibraries: List<String>,
    val nativeJars: List<NativeJar>,
    val jvmArgs: List<String>,
    val gameArgs: List<String>,
    val legacyArguments: String?,
    val assetIndex: String,
    val javaMajor: Int,
    val loggingArgument: String?,
    val loggingFileId: String?
)

/**
 * Resuelve una [GameInstallation] a un classpath concreto en disco:
 * hereda del padre (Fabric → 1.21.11), filtra librerías por `rules` y arma los argumentos.
 *
 * No descarga nada: si falta una librería la reporta en [ResolvedVersion.missingLibraries]
 * para que la UI lo muestre en vez de lanzar un proceso condenado a fallar.
 */
class VersionResolver(private val mcDir: Path) {

    private val librariesDir = mcDir.resolve("libraries")
    private val versionsDir = mcDir.resolve("versions")

    fun resolve(installation: GameInstallation, features: Map<String, Boolean> = emptyMap()): ResolvedVersion {
        val chain = VersionStore.chain(mcDir, installation.id)

        // Librerías: el hijo pisa al padre si repite coordenada.
        val libraries = LinkedHashMap<String, Library>()
        chain.forEach { json ->
            json.libraries.forEach { lib -> if (lib.name.isNotBlank()) libraries[lib.name] = lib }
        }

        val classpath = mutableListOf<Path>()
        val missing = mutableListOf<String>()
        val natives = mutableListOf<NativeJar>()

        for (lib in libraries.values) {
            if (!Rules.allows(lib.rules, features)) continue

            val relative = lib.downloads?.artifact?.path ?: mavenRelativePath(lib.name)
            if (relative != null) {
                val file = librariesDir.resolve(relative)
                if (Files.isRegularFile(file)) classpath.add(file) else missing += lib.name
            }

            val nativesKey = lib.natives[OsFamily.current.id]
            if (!lib.natives.isNullOrEmpty() && !nativesKey.isNullOrBlank()) {
                val classifier = nativesKey.replace("\${arch}", Rules.architecture())
                val nativeRelative = lib.downloads?.classifiers?.get(classifier)?.path
                    ?: mavenRelativePath("${lib.name}:$classifier")
                if (nativeRelative != null) {
                    val file = librariesDir.resolve(nativeRelative)
                    if (Files.isRegularFile(file)) {
                        natives += NativeJar(file, lib.extract?.exclude.orEmpty())
                    } else {
                        missing += "${lib.name}:$classifier"
                    }
                }
            }
        }

        // Jar del cliente: el de la versión base (la que declara downloads.client) y, si el
        // loader trae el suyo propio, también ese. Una instalación de Fabric puede tener su
        // propio jar completo aunque falte el de la versión base, así que alcanza con uno.
        val baseJar = versionsDir.resolve(installation.baseId).resolve("${installation.baseId}.jar")
        val ownJar = if (installation.id != installation.baseId) {
            versionsDir.resolve(installation.id).resolve("${installation.id}.jar")
        } else {
            null
        }
        var hasClientJar = false
        if (Files.isRegularFile(baseJar)) {
            classpath.add(baseJar)
            hasClientJar = true
        }
        if (ownJar != null && Files.isRegularFile(ownJar)) {
            classpath.add(ownJar)
            hasClientJar = true
        }
        if (!hasClientJar) missing += "${installation.baseId}.jar"

        val reversed = chain.asReversed()
        val mainClass = reversed.firstNotNullOfOrNull { it.mainClass?.takeIf(String::isNotBlank) }
            ?: "net.minecraft.client.main.Main"
        val assetIndex = reversed.firstNotNullOfOrNull { it.assetIndex?.id?.takeIf(String::isNotBlank) }
            ?: chain.lastOrNull { !it.assets.isNullOrBlank() }?.assets
            ?: "legacy"
        val javaMajor = reversed.firstNotNullOfOrNull { it.javaVersion?.majorVersion } ?: 21
        val logging = reversed.firstNotNullOfOrNull { it.logging?.client }

        return ResolvedVersion(
            mainClass = mainClass,
            classpath = classpath,
            missingLibraries = missing,
            nativeJars = natives,
            jvmArgs = chain.flatMap { it.arguments?.jvm.orEmpty() }.flatMap { expand(it, features) },
            gameArgs = chain.flatMap { it.arguments?.game.orEmpty() }.flatMap { expand(it, features) },
            legacyArguments = reversed.firstNotNullOfOrNull { it.minecraftArguments?.takeIf(String::isNotBlank) },
            assetIndex = assetIndex,
            javaMajor = javaMajor,
            loggingArgument = logging?.argument,
            loggingFileId = logging?.file?.path?.substringAfterLast('/')
        )
    }

    /** Un argumento puede ser un string o `{ rules, value }`; se resuelve según las rules. */
    private fun expand(node: JsonNode, features: Map<String, Boolean>): List<String> {
        if (node.isTextual) return listOf(node.asText())
        if (!node.isObject) return emptyList()
        if (!Rules.allows(Rules.parse(node.get("rules")), features)) return emptyList()
        val value = node.get("value") ?: return emptyList()
        return when {
            value.isTextual -> listOf(value.asText())
            value.isArray -> value.filter { it.isTextual }.map { it.asText() }
            else -> emptyList()
        }
    }

    /** `org.lwjgl:lwjgl:3.3.3[:classifier]` → `org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3[-classifier].jar` */
    private fun mavenRelativePath(coordinate: String): String? {
        val parts = coordinate.split(':')
        if (parts.size < 3) return null
        val group = parts[0].replace('.', '/')
        val artifact = parts[1]
        val version = parts[2]
        val classifier = parts.getOrNull(3)?.takeIf(String::isNotBlank)
        val fileName = buildString {
            append(artifact).append('-').append(version)
            if (classifier != null) append('-').append(classifier)
            append(".jar")
        }
        return "$group/$artifact/$version/$fileName"
    }
}
