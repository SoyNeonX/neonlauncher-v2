package com.neonlauncher.ui

import javafx.scene.paint.Color

/** Acento de cada instancia: el servidor tiene su identidad, el resto se deriva del nombre. */
internal data class InstanceTheme(
    val accent: String,
    val accentSoft: String,
    val blobA: String,
    val blobB: String
) {
    val onAccent: String
        get() {
            val color = Color.web(accent)
            val luminance = 0.2126 * color.red + 0.7152 * color.green + 0.0722 * color.blue
            return if (luminance > 0.55) "#12100E" else "#FFFFFF"
        }
}

internal object Themes {

    /** NeonWorld VI: atardecer naranja/magenta. */
    private val neonWorld = InstanceTheme(
        accent = "#FF7A45",
        accentSoft = "rgba(255, 122, 69, 0.14)",
        blobA = "#FF3C8E",
        blobB = "#7B5CFF"
    )

    private val neutral = InstanceTheme(
        accent = "#8B93A7",
        accentSoft = "rgba(139, 147, 167, 0.14)",
        blobA = "#5B6478",
        blobB = "#2F3542"
    )

    fun forInstance(id: String?, isServer: Boolean): InstanceTheme {
        if (isServer) return neonWorld
        if (id.isNullOrBlank()) return neutral

        val hue = ((id.hashCode() and 0x7fffffff) % 360).toDouble()
        val base = Color.hsb(hue, 0.55, 0.92)
        val second = Color.hsb((hue + 42.0) % 360.0, 0.50, 0.80)

        return InstanceTheme(
            accent = hex(base),
            accentSoft = rgba(base, 0.14),
            blobA = hex(base),
            blobB = hex(second)
        )
    }

    private fun hex(color: Color): String = String.format(
        "#%02X%02X%02X",
        (color.red * 255).toInt().coerceIn(0, 255),
        (color.green * 255).toInt().coerceIn(0, 255),
        (color.blue * 255).toInt().coerceIn(0, 255)
    )

    private fun rgba(color: Color, alpha: Double): String = String.format(
        "rgba(%d, %d, %d, %.2f)",
        (color.red * 255).toInt().coerceIn(0, 255),
        (color.green * 255).toInt().coerceIn(0, 255),
        (color.blue * 255).toInt().coerceIn(0, 255),
        alpha
    )
}
