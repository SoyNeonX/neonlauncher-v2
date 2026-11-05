package com.neonlauncher.ui

import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.ButtonBase
import javafx.scene.control.Label
import javafx.scene.control.TextInputControl
import javafx.scene.layout.HBox

/**
 * Barra de arrastre de la ventana (frameless). Solo lleva los botones de ventana:
 * la marca y la navegación viven en el rail de la derecha.
 */
internal class TitleBar(private val app: NeonApp) : HBox(8.0) {

    private var offsetX = 0.0
    private var offsetY = 0.0

    init {
        styleClass.add("titlebar")
        alignment = Pos.CENTER_LEFT
        children.addAll(Ui.grow(), windowButtons())

        setOnMousePressed { event ->
            if (isInteractive(event.target)) return@setOnMousePressed
            offsetX = app.stage.x - event.screenX
            offsetY = app.stage.y - event.screenY
        }
        setOnMouseDragged { event ->
            if (isInteractive(event.target)) return@setOnMouseDragged
            app.stage.x = event.screenX + offsetX
            app.stage.y = event.screenY + offsetY
        }
    }

    fun refreshAvatar() = Unit

    private fun windowButtons(): Node = HBox(2.0).apply {
        alignment = Pos.CENTER_RIGHT
        children.addAll(
            barButton("—") { app.stage.isIconified = true },
            barButton("✕", "close") { app.close() }
        )
    }

    private fun barButton(glyph: String, extra: String? = null, action: () -> Unit): Node {
        val button = Label(glyph).apply {
            styleClass.add("titlebar-btn")
            extra?.let { styleClass.add(it) }
            alignment = Pos.CENTER
            setOnMouseClicked { action() }
        }
        return button
    }

    private fun isInteractive(target: Any?): Boolean {
        var node = target as? Node
        while (node != null && node !== this) {
            if (node is ButtonBase || node is TextInputControl) return true
            node = node.parent
        }
        return false
    }
}
