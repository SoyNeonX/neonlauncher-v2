package com.neonlauncher.ui

import com.neonlauncher.common.util.Paths2
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Arte empaquetado y overrides del jugador.
 *
 * Walls: hay 8 wallpapers y cada instancia usa uno distinto (el servidor tiene el de la aurora).
 * Si el jugador deja su foto en `%APPDATA%\NeonLauncher\art\`, esa gana.
 *
 * Nombres reconocidos en `art/`:
 * - `background.jpg|png` → pisa el fondo de cualquier instancia
 * - `hero.jpg|png`       → pisa la foto del recuadro grande
 * - `onboarding.jpg|png` → fondo de la primera pantalla
 * - `wp0..wp7.jpg`       → reemplazan los wallpapers empaquetados
 */
internal object Artwork {

    val directory: Path = Paths2.launcherDataDir().resolve("art").also {
        runCatching { Files.createDirectories(it) }
    }

    private const val COUNT = 8
    private val cache = mutableMapOf<String, String?>()

    fun backgroundFor(instanceId: String?, isServer: Boolean): String? =
        userFile("background") ?: bundled("wallpapers/wp${backgroundIndex(instanceId, isServer)}.jpg")

    fun heroFor(instanceId: String?, isServer: Boolean): String? =
        if (isServer) studioLogo() ?: userFile("hero")
        else userFile("hero") ?: bundled("wallpapers/wp${heroIndex(instanceId, isServer)}.jpg")

    fun onboarding(): String? = userFile("onboarding") ?: bundled("wallpapers/wp0.jpg")

    /** logo de NeonWorld VI (PNG con transparencia). */
    fun studioLogo(): String? = bundled("neonworld/logo.png")

    /** textura de tierra del GUI de Minecraft, para los paneles de ajustes. */
    fun guiTexture(): String? = bundled("gui/dirt.jpg")

    fun coverStyle(url: String): String =
        "-fx-background-image: url('$url'); -fx-background-size: cover; -fx-background-position: center center;"

    fun containStyle(url: String): String =
        "-fx-background-image: url('$url'); -fx-background-size: contain; -fx-background-repeat: no-repeat; -fx-background-position: center center;"

    /** Textura repetida a escala chica, como el fondo de opciones de Minecraft. */
    fun tileStyle(url: String, size: Double): String =
        "-fx-background-image: url('$url'); -fx-background-size: ${size.toInt()}px ${size.toInt()}px; -fx-background-repeat: repeat;"

    // ---------------------------------------------------------------- selección

    private fun backgroundIndex(id: String?, isServer: Boolean): Int =
        if (isServer) 2 else Math.floorMod(id?.hashCode() ?: 0, COUNT)

    private fun heroIndex(id: String?, isServer: Boolean): Int =
        Math.floorMod((id?.hashCode() ?: 0) + 1, COUNT)

    private fun userFile(name: String): String? = listOf("jpg", "jpeg", "png")
        .map { directory.resolve("$name.$it") }
        .firstOrNull { Files.isRegularFile(it) }
        ?.toUri()
        ?.toString()

    /**
     * JavaFX no resuelve bien `url()` dentro de un jar empaquetado, así que la primera vez
     * copiamos el recurso a `art/` y usamos un `file:` URL.
     */
    private fun bundled(resource: String): String? = cache.getOrPut(resource) {
        runCatching {
            val baseName = resource.substringAfterLast('/')
            val target = directory.resolve(baseName)
            if (!Files.isRegularFile(target)) {
                Artwork::class.java.getResourceAsStream("/images/$resource")?.use { input ->
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            if (Files.isRegularFile(target)) target.toUri().toString() else null
        }.getOrNull()
    }
}
