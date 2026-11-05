package com.neonlauncher.ui

import javafx.animation.FadeTransition
import javafx.animation.Interpolator
import javafx.animation.SequentialTransition
import javafx.animation.TranslateTransition
import javafx.scene.Node
import javafx.util.Duration

/** Transiciones chicas y consistentes para no animar a mano en cada vista. */
internal object Transitions {

    fun fadeIn(node: Node, millis: Double = 220.0, from: Double = 0.0) {
        node.opacity = from
        FadeTransition(Duration.millis(millis), node).apply {
            toValue = 1.0
            interpolator = Interpolator.EASE_BOTH
            play()
        }
    }

    /** Entra desde la derecha (para el contenido cuando cambia la instancia o la vista). */
    fun slideIn(node: Node, millis: Double = 240.0, distance: Double = 14.0) {
        node.opacity = 0.0
        node.translateX = distance
        val fade = FadeTransition(Duration.millis(millis), node).apply { toValue = 1.0 }
        val slide = TranslateTransition(Duration.millis(millis), node).apply {
            toX = 0.0
            interpolator = Interpolator.SPLINE(0.2, 0.0, 0.1, 1.0)
        }
        SequentialTransition(fade, slide).play()
    }

    fun pop(node: Node, millis: Double = 180.0) {
        node.scaleX = 0.98
        node.scaleY = 0.98
        node.opacity = 0.0
        val fade = FadeTransition(Duration.millis(millis), node).apply { toValue = 1.0 }
        fade.play()
        javafx.animation.ScaleTransition(Duration.millis(millis), node).apply {
            toX = 1.0
            toY = 1.0
            interpolator = Interpolator.EASE_OUT
            play()
        }
    }
}
