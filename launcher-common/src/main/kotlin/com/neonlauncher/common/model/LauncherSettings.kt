package com.neonlauncher.common.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Configuración persistente del launcher (`%APPDATA%\NeonLauncher\settings.json`).
 *
 * Todos los campos tienen default para que un `settings.json` viejo o incompleto siga
 * cargando sin romper el arranque.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LauncherSettings(
    val username: String = "Player",
    val gameDirectory: String? = null,
    val javaPath: String? = null,
    val lastVersion: String? = null,
    val memoryMb: Int = 3072,
    val windowWidth: Int = 1280,
    val windowHeight: Int = 720,
    val extraJvmArgs: String = "",
    val showSnapshots: Boolean = false,
    /** Primera vez: ya eligió Microsoft u offline. */
    val onboarded: Boolean = false,
    /** Instancias del jugador (y las del estudio). */
    val instances: List<Instance> = emptyList(),
    val selectedInstanceId: String? = null,
    /** Sesión de Microsoft activa; si está, el launcher arranca en modo online. */
    val microsoft: StoredAccount? = null
)

/**
 * Sesión de Microsoft persistida. Ojo: hoy el `refreshToken` queda en texto plano en el JSON
 * del usuario; el siguiente paso es cifrarlo con DPAPI de Windows.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StoredAccount(
    val name: String = "",
    val uuid: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAtEpochMs: Long = 0
) {
    fun isExpiringSoon(marginMs: Long = 2 * 60 * 60 * 1000L): Boolean =
        System.currentTimeMillis() + marginMs >= expiresAtEpochMs
}
