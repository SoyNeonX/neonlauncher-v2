package com.neonlauncher.ui

import javafx.scene.Node
import javafx.scene.effect.GaussianBlur
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.paint.CycleMethod
import javafx.scene.paint.RadialGradient
import javafx.scene.paint.Stop
import javafx.scene.shape.Circle
import javafx.scene.shape.Rectangle

/**
 * Fondo de la ventana: foto si hay una en `art/background.*`, si no degradados radiales.
 */
internal object Backdrop {

    fun layer(width: Double = 1180.0, height: Double = 740.0, theme: InstanceTheme? = null, image: String? = null): Node {
        val stack = StackPane().apply {
            isMouseTransparent = true
            prefWidth = width
            prefHeight = height
        }

        val photo = image
        photo?.let { url ->
            stack.children.add(fixed(Region().apply { style = Artwork.coverStyle(url) }))
            // Oscurece la foto para que el texto siga legible.
            stack.children.add(fixed(Region().apply { style = "-fx-background-color: rgba(8, 8, 12, 0.62);" }))
        }

        val colorA = theme?.blobA ?: "#5B6478"
        val colorB = theme?.blobB ?: "#2F3542"

        val blobs = Pane().apply {
            isMouseTransparent = true
            prefWidth = width
            prefHeight = height
            clip = Rectangle(width, height)
            children.addAll(
                blob(560.0, Color.web(colorA), width * 0.70, -height * 0.34, if (photo == null) 0.20 else 0.10),
                blob(500.0, Color.web(colorB), width * 0.98, height * 0.60, if (photo == null) 0.16 else 0.09),
                blob(420.0, Color.web(colorA), -width * 0.14, height * 0.95, if (photo == null) 0.10 else 0.06)
            )
        }
        stack.children.add(blobs)
        return stack
    }

    private fun fixed(node: Region): Region = node.apply {
        prefWidth = 1180.0
        prefHeight = 740.0
        maxWidth = Double.MAX_VALUE
        maxHeight = Double.MAX_VALUE
    }

    private fun blob(radius: Double, color: Color, x: Double, y: Double, alpha: Double): Circle =
        Circle(radius).apply {
            fill = RadialGradient(
                0.0, 0.0, 0.5, 0.5, 1.0, true, CycleMethod.NO_CYCLE,
                Stop(0.0, color.deriveColor(0.0, 1.0, 1.0, alpha)),
                Stop(1.0, Color.TRANSPARENT)
            )
            effect = GaussianBlur(radius * 0.34)
            layoutX = x
            layoutY = y
        }
}
