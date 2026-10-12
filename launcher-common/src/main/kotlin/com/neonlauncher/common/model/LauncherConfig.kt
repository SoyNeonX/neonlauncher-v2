package com.neonlauncher.common.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class LauncherConfig(
    @JsonProperty("launcherVersion") val launcherVersion: String = "1.0.0",
    @JsonProperty("updateChannel") val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    @JsonProperty("serverManifestUrl") val serverManifestUrl: String = "https://api.example.com/launcher/manifest.json",
    @JsonProperty("serverModsBaseUrl") val serverModsBaseUrl: String = "https://cdn.example.com/mods/",
    @JsonProperty("autoUpdateLauncher") val autoUpdateLauncher: Boolean = true,
    @JsonProperty("checkUpdatesOnStartup") val checkUpdatesOnStartup: Boolean = true,
    @JsonProperty("language") val language: String = "es_ES",
    @JsonProperty("theme") val theme: Theme = Theme.DARK,
    @JsonProperty("downloadThreads") val downloadThreads: Int = 4,
    @JsonProperty("verifyHashes") val verifyHashes: Boolean = true,
    @JsonProperty("gameDirectory") val gameDirectory: String? = null,
    @JsonProperty("javaAutoDetect") val javaAutoDetect: Boolean = true,
    @JsonProperty("showConsole") val showConsole: Boolean = false,
    @JsonProperty("minimizeOnLaunch") val minimizeOnLaunch: Boolean = true,
    @JsonProperty("closeOnLaunch") val closeOnLaunch: Boolean = false
)

enum class UpdateChannel {
    STABLE, BETA, DEV
}

enum class Theme {
    DARK, LIGHT, SYSTEM
}