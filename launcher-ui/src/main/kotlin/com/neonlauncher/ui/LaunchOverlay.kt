package com.neonlauncher.ui

import javafx.animation.FadeTransition
import javafx.animation.Interpolator
import javafx.animation.KeyFrame
import javafx.animation.KeyValue
import javafx.animation.Timeline
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.util.Duration

/**
 * Transición de lanzamiento: cinco segundos desde que se aprieta JUGAR hasta que arranca
 * el proceso, con barra determinada y el nombre de la instancia.
 */
internal class LaunchOverlay : StackPane() {

    private val title = Ui.label("", "launch-title")
    private val subtitle = Ui.label("", "launch-sub")
    private val bar = ProgressBar(0.0)

    init {
        styleClass.add("launch-overlay")

        bar.styleClass.add("launch-bar")
        bar.maxWidth = 320.0
        bar.prefWidth = 320.0

        val content = VBox(
            14.0,
            Brand.icon(64.0),
            title,
            bar,
            subtitle
        ).apply {
            alignment = Pos.CENTER
            maxWidth = Region.USE_PREF_SIZE
            maxHeight = Region.USE_PREF_SIZE
        }

        children.add(content)
        alignment = Pos.CENTER
        isVisible = false
        isManaged = false
    }

    fun begin(instanceName: String, subtitleText: String, seconds: Double, onDone: () -> Unit) {
        title.text = "Abriendo $instanceName"
        subtitle.text = subtitleText
        bar.progress = 0.0

        isVisible = true
        isManaged = true
        opacity = 0.0
        FadeTransition(Duration.millis(180.0), this).apply { toValue = 1.0; play() }

        Timeline(
            KeyFrame(Duration.ZERO, KeyValue(bar.progressProperty(), 0.0)),
            KeyFrame(
                Duration.seconds(seconds),
                KeyValue(bar.progressProperty(), 1.0, Interpolator.LINEAR)
            )
        ).apply {
            setOnFinished { onDone() }
            play()
        }
    }

    fun setStatus(text: String) {
        subtitle.text = text
    }

    fun finish() {
        FadeTransition(Duration.millis(160.0), this).apply {
            toValue = 0.0
            setOnFinished {
                isVisible = false
                isManaged = false
                bar.progress = 0.0
            }
            play()
        }
    }
}
