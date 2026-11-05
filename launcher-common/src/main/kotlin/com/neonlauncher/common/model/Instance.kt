package com.neonlauncher.common.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

enum class InstanceOrigin {
    /** Instancias publicadas por NeonShy Studios (tienen carpeta y reglas propias). */
    STUDIO,
    /** Instancias que crea el jugador a partir de una versión instalada. */
    USER
}

/**
 * Una instancia es lo que el jugador abre: un nombre, una versión de Minecraft y su
 * configuración. Varias instancias pueden usar la misma versión sin pisarse.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Instance(
    val id: String,
    val name: String,
    val versionId: String,
    val origin: InstanceOrigin = InstanceOrigin.USER,
    /** 0 = usar la memoria global de Ajustes. */
    val memoryMb: Int = 0,
    /** Emoji o inicial que se muestra en el rail. */
    val icon: String = "",
    val createdAtEpochMs: Long = 0
) {
    val isStudio: Boolean get() = origin == InstanceOrigin.STUDIO
}
