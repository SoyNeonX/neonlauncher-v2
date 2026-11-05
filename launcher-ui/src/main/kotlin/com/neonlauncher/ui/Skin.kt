package com.neonlauncher.ui

import javafx.scene.Scene

/**
 * Identidad visual fija de NeonLauncher (paleta "NeonWorld VI": atardecer sobre violeta).
 * Es una sola piel a propósito: no hay selector de temas.
 */
internal object Skin {

    fun apply(scene: Scene) {
        val url = Skin::class.java.getResource("/css/app.css")?.toExternalForm() ?: return
        scene.stylesheets.setAll(listOf(url))
    }
}
