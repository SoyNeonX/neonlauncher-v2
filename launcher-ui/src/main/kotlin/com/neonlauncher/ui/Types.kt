package com.neonlauncher.ui

import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox

internal enum class View { PLAY, INSTANCES, SETTINGS, LOGIN, ONBOARDING, VERSIONS }

internal enum class Phase { LOADING, READY, LAUNCHING, RUNNING, NO_GAME }

internal sealed interface LoginState {
    data object Idle : LoginState
    data class Code(val code: com.neonlauncher.core.auth.DeviceCode) : LoginState
    data class Status(val text: String) : LoginState
    data class Failed(val message: String) : LoginState
}

internal interface AppView {
    val node: Node
    fun refresh()
}

/** Buffer de la consola: sobrevive a la navegación entre vistas. */
internal class LogBuffer(private val capacity: Int = 3000) {

    private val lines = ArrayDeque<String>()
    var listener: ((String) -> Unit)? = null

    fun append(line: String) {
        if (lines.size >= capacity) lines.removeFirst()
        lines.addLast(line)
        listener?.invoke(line)
    }

    fun text(): String = lines.joinToString("\n")

    fun clear() {
        lines.clear()
    }
}

internal object Ui {

    fun label(text: String, vararg styles: String): Label = Label(text).apply {
        styleClass.addAll(styles)
    }

    fun title(text: String): Label = label(text, "title")
    fun section(text: String): Label = label(text, "section-title")
    fun field(text: String): Label = label(text, "field-label")
    fun hint(text: String): Label = label(text, "hint")

    fun chip(text: String, vararg extra: String): Label = label(text, "chip", *extra)

    fun grow(): Region = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
    fun vgrow(): Region = Region().apply { VBox.setVgrow(this, Priority.ALWAYS) }

    fun row(spacing: Double = 10.0, vararg children: Node): HBox =
        HBox(spacing, *children).apply { alignment = javafx.geometry.Pos.CENTER_LEFT }

    fun detailRow(name: String, value: String): HBox = HBox(
        8.0,
        label(name, "detail-name"),
        grow(),
        label(value, "detail-value")
    ).apply { alignment = javafx.geometry.Pos.CENTER_LEFT }

    fun avatar(name: String): StackPane {
        val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        return StackPane(
            Region().apply { styleClass.add("avatar") },
            label(letter, "avatar-letter")
        )
    }
}

internal fun openFolder(path: java.nio.file.Path) {
    runCatching { java.awt.Desktop.getDesktop().open(path.toFile()) }
}

internal fun openUrl(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI.create(url)) }
}

internal fun copyToClipboard(text: String) {
    runCatching {
        val content = javafx.scene.input.ClipboardContent()
        content.putString(text)
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content)
    }
}
