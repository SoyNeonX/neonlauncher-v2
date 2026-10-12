package com.neonlauncher.common.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class Manifest(
    @JsonProperty("version") val version: String,
    @JsonProperty("minecraft") val minecraftVersion: String,
    @JsonProperty("forgeVersion") val forgeVersion: String? = null,
    @JsonProperty("fabricVersion") val fabricVersion: String? = null,
    @JsonProperty("quiltVersion") val quiltVersion: String? = null,
    @JsonProperty("loader") val loader: LoaderType = LoaderType.FORGE,
    @JsonProperty("javaVersion") val javaVersion: Int = 21,
    @JsonProperty("mainClass") val mainClass: String = "net.minecraft.client.main.Main",
    @JsonProperty("tweakers") val tweakers: List<String> = emptyList(),
    @JsonProperty("libraries") val libraries: List<Library> = emptyList(),
    @JsonProperty("mods") val mods: List<ModEntry> = emptyList(),
    @JsonProperty("config") val config: ServerConfig = ServerConfig(),
    @JsonProperty("jvmArgs") val jvmArgs: List<String> = emptyList(),
    @JsonProperty("gameArgs") val gameArgs: List<String> = emptyList(),
    @JsonProperty("assetIndex") val assetIndex: String? = null,
    @JsonProperty("releaseTime") val releaseTime: Instant? = null
) {
    fun getLoaderVersion(): String = when (loader) {
        LoaderType.FORGE -> forgeVersion ?: "47.2.0"
        LoaderType.FABRIC -> fabricVersion ?: "0.16.9"
        LoaderType.QUILT -> quiltVersion ?: "0.16.9"
        LoaderType.VANILLA -> minecraftVersion
    }
}

enum class LoaderType {
    FORGE, FABRIC, QUILT, VANILLA
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Library(
    @JsonProperty("name") val name: String,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("sha256") val sha256: String? = null,
    @JsonProperty("natives") val natives: Map<String, String>? = null,
    @JsonProperty("extract") val extract: ExtractRules? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExtractRules(
    @JsonProperty("exclude") val exclude: List<String> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ModEntry(
    @JsonProperty("id") val id: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("version") val version: String,
    @JsonProperty("file") val file: String,
    @JsonProperty("url") val url: String,
    @JsonProperty("sha256") val sha256: String,
    @JsonProperty("required") val required: Boolean = true,
    @JsonProperty("side") val side: ModSide = ModSide.BOTH,
    @JsonProperty("dependencies") val dependencies: List<String> = emptyList(),
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("authors") val authors: List<String> = emptyList()
)

enum class ModSide {
    CLIENT, SERVER, BOTH
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ServerConfig(
    @JsonProperty("serverIp") val serverIp: String = "localhost",
    @JsonProperty("serverPort") val serverPort: Int = 25565,
    @JsonProperty("resourcePack") val resourcePack: String? = null,
    @JsonProperty("resourcePackSha1") val resourcePackSha1: String? = null,
    @JsonProperty("motd") val motd: String? = null,
    @JsonProperty("favicon") val favicon: String? = null
)