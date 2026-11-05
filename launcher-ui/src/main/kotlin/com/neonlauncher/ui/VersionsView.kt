package com.neonlauncher.ui

import com.neonlauncher.core.download.RemoteVersion
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ProgressBar
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox

/** Catálogo oficial para bajar cualquier versión de Minecraft, con progreso real. */
internal class VersionsView(private val app: NeonApp) : AppView {

    private enum class Filter(val label: String) {
        ALL("Todas"),
        RELEASES("Finales"),
        SNAPSHOTS("Snapshots")
    }

    private val search = TextField()
    private val list = VBox(4.0)
    private val status = Ui.label("", "hint")
    private val progress = ProgressBar(0.0)
    private var filter = Filter.RELEASES

    override val node: VBox = build()

    override fun refresh() {
        progress.progress = app.installProgress
        progress.isVisible = app.installingVersion != null
        progress.isManaged = progress.isVisible
        status.text = when {
            app.installingVersion != null -> "${app.installingVersion}: ${app.installStatus}"
            app.catalogError != null -> app.catalogError ?: ""
            app.remoteVersions.isEmpty() -> "Cargando el catálogo de Mojang…"
            else -> "${app.remoteVersions.size} versiones · ${app.installations.size} instaladas"
        }
        render()
    }

    private fun build(): VBox {
        val heading = VBox(
            2.0,
            Ui.title("Instalar versión"),
            Ui.label("Se descarga el juego oficial desde Mojang: cliente, librerías y assets.", "subtitle")
        )

        search.promptText = "Buscar versión…"
        search.styleClass.add("text-input")
        search.prefWidth = 220.0
        search.textProperty().addListener { _, _, _ -> render() }

        val group = ToggleGroup()
        val filters = HBox(6.0).apply {
            Filter.values().forEach { option ->
                val button = ToggleButton(option.label).apply {
                    styleClass.add("seg")
                    toggleGroup = group
                    isSelected = option == filter
                    setOnAction {
                        filter = option
                        if (!isSelected) isSelected = true
                        render()
                    }
                }
                children.add(button)
            }
        }

        val toolbar = HBox(12.0, search, filters, Ui.grow(), status).apply { alignment = Pos.CENTER_LEFT }

        val body = ScrollPane(list).apply {
            isFitToWidth = true
            styleClass.add("scroll-pane")
            VBox.setVgrow(this, Priority.ALWAYS)
            maxHeight = Double.MAX_VALUE
        }

        progress.styleClass.add("launch-bar")
        progress.maxWidth = Double.MAX_VALUE

        return VBox(14.0, heading, toolbar, body, progress).apply {
            maxWidth = Double.MAX_VALUE
            maxHeight = Double.MAX_VALUE
        }
    }

    private fun render() {
        val query = search.text.trim().lowercase()
        val installed = app.installations.associateBy { it.id }

        val filtered = app.remoteVersions
            .asSequence()
            .filter {
                when (filter) {
                    Filter.ALL -> true
                    Filter.RELEASES -> it.type == "release"
                    Filter.SNAPSHOTS -> it.type != "release"
                }
            }
            .filter { query.isBlank() || it.id.lowercase().contains(query) }
            .take(80)
            .toList()

        list.children.setAll(
            if (filtered.isEmpty()) listOf(Ui.hint("Sin resultados."))
            else filtered.map { row(it, installed[it.id]) }
        )
    }

    private fun row(remote: RemoteVersion, installed: com.neonlauncher.core.game.GameInstallation?): Node {
        val busy = app.installingVersion == remote.id

        val meta = buildString {
            append(remote.type)
            remote.releaseTime?.let { append(" · ").append(it.toString().take(10)) }
        }

        val badge = StackPane(Ui.label("▣", "rail-badge-letter")).apply { styleClass.add("rail-badge") }

        val actions = HBox(6.0).apply {
            alignment = Pos.CENTER_RIGHT
            when {
                busy -> children.add(Ui.chip("INSTALANDO…"))
                installed != null -> {
                    children.add(Ui.chip("INSTALADA", "chip-live"))
                    children.add(action("Reparar") { app.repairInstance(installed) })
                    children.add(action("Desinstalar") { app.deleteVersion(installed) })
                    children.add(action("Crear instancia") { app.createInstance(installed.id) })
                }
                else -> children.add(
                    Button("Instalar").apply {
                        styleClass.add("ghost-btn")
                        style += "-fx-padding: 7 16 7 16; -fx-font-size: 11px;"
                        isDisable = app.installingVersion != null
                        setOnAction { app.installVersion(remote) }
                    }
                )
            }
        }

        return HBox(
            12.0,
            badge,
            VBox(1.0, Ui.label(remote.id, "row-name"), Ui.label(meta, "hint")),
            Ui.grow(),
            actions
        ).apply {
            styleClass.add("row")
            alignment = Pos.CENTER_LEFT
        }
    }

    private fun action(text: String, action: () -> Unit): Node =
        Button(text).apply {
            styleClass.add("link-btn")
            setOnAction { action() }
        }
}
