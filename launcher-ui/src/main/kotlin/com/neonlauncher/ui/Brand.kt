package com.neonlauncher.ui

import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.shape.Line

/**
 * Marca de NeonLauncher: "NL" en un cubo-badge con textura de bloques.
 * Las tres piezas (icono, wordmark y cubo decorativo) se reusan en toda la app.
 */
internal object Brand {

    const val NAME = "NEON"
    const val REST = "LAUNCHER"

    /** Isotipo. Pensado para 24–40 px. */
    fun icon(size: Double = 30.0): Node {
        val radius = size * 0.26
        val badge = StackPane().apply {
            style = "-fx-background-color: #161019;" +
                " -fx-background-radius: $radius; -fx-border-color: rgba(255, 244, 230, 0.28);" +
                " -fx-border-radius: $radius; -fx-border-width: 1;"
            prefWidth = size
            prefHeight = size
            minWidth = size
            minHeight = size
            maxWidth = size
            maxHeight = size
        }

        badge.children.add(blockTexture(size))
        badge.children.add(
            Label("NL").apply {
                style = "-fx-font-family: \"Bahnschrift\"; -fx-font-size: ${size * 0.42}px;" +
                    " -fx-font-weight: bold; -fx-text-fill: #FFF4E6;"
            }
        )
        return badge
    }

    /** Wordmark monocromo. `spaced` reparte las letras para el lockup grande. */
    fun wordmark(size: Double = 13.0, spaced: Boolean = false): Node {
        val text = if (spaced) {
            "N\u2009E\u2009O\u2009N   L\u2009A\u2009U\u2009N\u2009C\u2009H\u2009E\u2009R"
        } else {
            "NEON LAUNCHER"
        }
        return Label(text).apply {
            style = "-fx-font-family: \"Bahnschrift\"; -fx-font-size: ${size}px;" +
                " -fx-font-weight: bold; -fx-text-fill: #FFF4E6;"
            maxWidth = javafx.scene.layout.Region.USE_PREF_SIZE
            minWidth = javafx.scene.layout.Region.USE_PREF_SIZE
        }
    }

    /** Bloque isométrico decorativo. */
    fun cube(size: Double): Node {
        val s = size / 100.0
        fun p(vararg xy: Double): javafx.scene.shape.Polygon =
            javafx.scene.shape.Polygon(*xy.map { it * s }.toDoubleArray())

        val top = p(50.0, 6.0, 94.0, 30.0, 50.0, 54.0, 6.0, 30.0).apply {
            fill = javafx.scene.paint.LinearGradient(
                0.0, 0.0, 1.0, 1.0, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                javafx.scene.paint.Stop(0.0, javafx.scene.paint.Color.web("#FFC08A")),
                javafx.scene.paint.Stop(1.0, javafx.scene.paint.Color.web("#FF7A45"))
            )
        }
        val left = p(6.0, 30.0, 50.0, 54.0, 50.0, 96.0, 6.0, 72.0).apply {
            fill = javafx.scene.paint.Color.web("#FF3C8E", 0.42)
        }
        val right = p(94.0, 30.0, 50.0, 54.0, 50.0, 96.0, 94.0, 72.0).apply {
            fill = javafx.scene.paint.Color.web("#FF3C8E", 0.28)
        }

        val pane = Pane(top, left, right).apply {
            prefWidth = size
            prefHeight = size
            minWidth = size
            minHeight = size
            maxWidth = size
            maxHeight = size
        }
        return pane
    }

    /** Bloque isométrico en wireframe, para usar como marca de agua. */
    fun cubeWire(size: Double, from: String = "#FFC08A", to: String = "#FF3C8E"): Node {
        val s = size / 100.0
        fun p(vararg xy: Double): javafx.scene.shape.Polygon =
            javafx.scene.shape.Polygon(*xy.map { it * s }.toDoubleArray())

        val stroke = javafx.scene.paint.LinearGradient(
            0.0, 0.0, 1.0, 1.0, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
            javafx.scene.paint.Stop(0.0, javafx.scene.paint.Color.web(from)),
            javafx.scene.paint.Stop(1.0, javafx.scene.paint.Color.web(to))
        )

        fun edge(vararg xy: Double): javafx.scene.shape.Polygon = p(*xy).apply {
            fill = javafx.scene.paint.Color.TRANSPARENT
            this.stroke = stroke
            strokeWidth = 2.0
            strokeLineJoin = javafx.scene.shape.StrokeLineJoin.ROUND
        }

        val group = javafx.scene.Group(
            edge(50.0, 6.0, 94.0, 30.0, 50.0, 54.0, 6.0, 30.0),
            edge(6.0, 30.0, 50.0, 54.0, 50.0, 96.0, 6.0, 72.0),
            edge(94.0, 30.0, 50.0, 54.0, 50.0, 96.0, 94.0, 72.0),
            edge(6.0, 72.0, 50.0, 96.0, 94.0, 72.0, 50.0, 54.0, 6.0, 30.0, 50.0, 6.0, 94.0, 30.0)
        ).apply {
            effect = javafx.scene.effect.DropShadow(
                0.0, 0.0, 26.0, javafx.scene.paint.Color.web("#FF3C8E", 0.35)
            )
        }

        return Pane(group).apply {
            prefWidth = size
            prefHeight = size
            minWidth = size
            minHeight = size
            maxWidth = size
            maxHeight = size
        }
    }

    /** Línea de escaneo sutil, para el aire retro. */
    fun scanlines(width: Double, height: Double, gap: Double = 3.0): Node = Pane().apply {
        var y = 0.0
        while (y < height) {
            children.add(
                Line(0.0, y, width, y).apply {
                    stroke = javafx.scene.paint.Color.web("#FFFFFF", 0.03)
                    strokeWidth = 1.0
                }
            )
            y += gap
        }
        isMouseTransparent = true
    }

    private fun blockTexture(size: Double): Node = Pane().apply {
        val cell = size / 5.0
        for (row in 0 until 5) {
            for (col in 0 until 5) {
                if ((row + col) % 2 != 0) continue
                val block = Region().apply {
                    style = "-fx-background-color: rgba(255,255,255,0.05);"
                    prefWidth = cell - 1.5
                    prefHeight = cell - 1.5
                    minWidth = cell - 1.5
                    minHeight = cell - 1.5
                    maxWidth = cell - 1.5
                    maxHeight = cell - 1.5
                    relocate(col * cell + 0.75, row * cell + 0.75)
                }
                children.add(block)
            }
        }
        isMouseTransparent = true
    }
}
