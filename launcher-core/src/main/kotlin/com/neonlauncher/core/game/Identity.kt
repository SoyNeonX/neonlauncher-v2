package com.neonlauncher.core.game

/**
 * Quién juega: offline (UUID derivado del nombre) o una cuenta de Microsoft con su token real.
 * El resto del lanzamiento no necesita saber cuál es.
 */
data class Identity(
    val name: String,
    val uuid: String,
    val accessToken: String,
    val userType: String
)
