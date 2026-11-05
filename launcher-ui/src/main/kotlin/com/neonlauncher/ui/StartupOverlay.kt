package com.neonlauncher.ui

import javafx.animation.FadeTransition
import javafx.animation.ScaleTransition
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.shape.Circle
import javafx.scene.paint.RadialGradient
import javafx.util.Duration

/** Pantalla de carga con pasos: updates → equipo → Java → instancias. */
internal class StartupOverlay : StackPane() {

    private val status = Ui.label("Iniciando…", "startup-status")
    private val detail = Ui.label("", "startup-detail")
    private val steps = listOf(
        stepPill(), stepPill(), stepPill(), stepPill()
    )

    init {
        styleClass.add("startup-overlay")

        val logo = Brand.icon(84.0)
        ScaleTransition(Duration.millis(1600.0), logo).apply {
            fromX = 0.97
            toX = 1.03
            fromY = 0.97
            toY = 1.03
            cycleCount = javafx.animation.Animation.INDEFINITE
            isAutoReverse = true
            play()
        }

        // Sin halo: fondo plano y el isotipo como único punto de color.
        val logoStack = StackPane(logo).apply {
            maxWidth = Region.USE_PREF_SIZE
            maxHeight = Region.USE_PREF_SIZE
        }

        val stepRow = HBox(8.0).apply {
            alignment = Pos.CENTER
            children.addAll(steps)
        }

        val content = VBox(
            16.0,
            logoStack,
            Brand.wordmark(17.0, spaced = true),
            stepRow,
            status,
            detail
        ).apply {
            alignment = Pos.CENTER
            maxWidth = Region.USE_PREF_SIZE
            maxHeight = Region.USE_PREF_SIZE
        }

        children.add(content)
        alignment = Pos.CENTER
    }

    fun setStatus(text: String, detailText: String = "") {
        status.text = text
        detail.text = detailText
        FadeTransition(Duration.millis(180.0), status).apply {
            fromValue = 0.35
            toValue = 1.0
            play()
        }
    }

    /** Ilumina los pasos hasta [index] (0..3). */
    fun setStep(index: Int) {
        steps.forEachIndexed { position, pill ->
            if (position <= index) {
                if (!pill.styleClass.contains("step-pill-on")) {
                    pill.styleClass.add("step-pill-on")
                    Transitions.pop(pill, 160.0)
                }
            } else {
                pill.styleClass.remove("step-pill-on")
            }
        }
    }

    fun hide(after: () -> Unit = {}) {
        FadeTransition(Duration.millis(300.0), this).apply {
            toValue = 0.0
            setOnFinished {
                isVisible = false
                isManaged = false
                // Ojo: adentro de un handler de animación JavaFX prohíbe showAndWait
                // ("not allowed during animation or layout processing"), así que se difiere.
                javafx.application.Platform.runLater { after() }
            }
            play()
        }
    }

    private companion object {
        fun stepPill(): Region = Region().apply {
            styleClass.add("step-pill")
            prefWidth = 54.0
            minWidth = 54.0
            maxWidth = 54.0
            prefHeight = 3.0
            minHeight = 3.0
            maxHeight = 3.0
        }
    }
}
