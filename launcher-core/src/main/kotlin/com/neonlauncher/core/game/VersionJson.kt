package com.neonlauncher.core.game

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

/**
 * Modelo del `version.json` de Minecraft (el que publica Mojang y el que agregan Fabric/Forge).
 * Todos los campos son opcionales: un JSON viejo o uno de loader puede no traerlos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class VersionJson(
    val id: String = "",
    val type: String = "release",
    val mainClass: String? = null,
    val inheritsFrom: String? = null,
    val releaseTime: String? = null,
    val time: String? = null,
    val assets: String? = null,
    val assetIndex: AssetRef? = null,
    val downloads: VersionDownloads? = null,
    val libraries: List<Library> = emptyList(),
    val arguments: Arguments? = null,
    val minecraftArguments: String? = null,
    val javaVersion: JavaRef? = null,
    val logging: Logging? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AssetRef(
    val id: String = "",
    val sha1: String? = null,
    val size: Long = 0,
    val totalSize: Long = 0,
    val url: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VersionDownloads(
    val client: Artifact? = null,
    val server: Artifact? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Artifact(
    val path: String? = null,
    val url: String? = null,
    val sha1: String? = null,
    val size: Long = 0
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Library(
    val name: String = "",
    val url: String? = null,
    val rules: List<Rule> = emptyList(),
    val natives: Map<String, String> = emptyMap(),
    val extract: ExtractRules? = null,
    val downloads: LibraryDownloads? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LibraryDownloads(
    val artifact: Artifact? = null,
    val classifiers: Map<String, Artifact>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExtractRules(
    val exclude: List<String> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Rule(
    val action: String = "allow",
    val os: OsRule? = null,
    val features: Map<String, Boolean>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OsRule(
    val name: String? = null,
    val arch: String? = null,
    val version: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Arguments(
    val jvm: List<JsonNode> = emptyList(),
    val game: List<JsonNode> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JavaRef(
    val majorVersion: Int = 8,
    val component: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Logging(
    val client: LoggingClient? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LoggingClient(
    val argument: String? = null,
    val file: Artifact? = null,
    val type: String? = null
)
