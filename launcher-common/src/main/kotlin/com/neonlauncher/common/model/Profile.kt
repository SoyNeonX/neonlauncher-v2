package com.neonlauncher.common.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProfileContainer(
    @JsonProperty("selectedProfile") val selectedProfile: String = "default",
    @JsonProperty("profiles") val profiles: Map<String, Profile> = mapOf()
) {
    fun getSelectedProfile(): Profile = profiles[selectedProfile] ?: Profile()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Profile(
    @JsonProperty("name") val name: String = "Default",
    @JsonProperty("microsoftAccount") val microsoftAccount: MicrosoftAccount? = null,
    @JsonProperty("javaPath") val javaPath: String? = null,
    @JsonProperty("memoryMax") val memoryMax: Int = 4096,
    @JsonProperty("windowWidth") val windowWidth: Int = 1280,
    @JsonProperty("windowHeight") val windowHeight: Int = 720,
    @JsonProperty("fullscreen") val fullscreen: Boolean = false,
    @JsonProperty("customJvmArgs") val customJvmArgs: List<String> = emptyList(),
    @JsonProperty("enabledMods") val enabledMods: List<String> = emptyList(),
    @JsonProperty("lastManifestVersion") val lastManifestVersion: String? = null,
    @JsonProperty("createdAt") val createdAt: Instant = Instant.now(),
    @JsonProperty("lastUsed") val lastUsed: Instant = Instant.now()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MicrosoftAccount(
    @JsonProperty("accessToken") val accessToken: String,
    @JsonProperty("refreshToken") val refreshToken: String,
    @JsonProperty("expiresAt") val expiresAt: Long,
    @JsonProperty("uuid") val uuid: String,
    @JsonProperty("username") val username: String,
    @JsonProperty("skinUrl") val skinUrl: String? = null,
    @JsonProperty("capeUrl") val capeUrl: String? = null,
    @JsonProperty("xuid") val xuid: String? = null,
    @JsonProperty("clientId") val clientId: String? = null
) {
    fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt
    fun isExpiringSoon(thresholdMinutes: Int = 5): Boolean = System.currentTimeMillis() >= (expiresAt - thresholdMinutes * 60 * 1000L)
}