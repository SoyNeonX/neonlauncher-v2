package com.neonlauncher.ui

import com.neonlauncher.common.model.Instance
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.ComboBox
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox

/** Gestor de instancias: crear, renombrar, cambiar icono y RAM, borrar. */
internal class InstancesView(private val app: NeonApp) : AppView {

    private val list = VBox(8.0)

    override val node: VBox = build()

    override fun refresh() = render()

    private fun build(): VBox {
        val heading = VBox(
            2.0,
            Ui.title("Instancias"),
            Ui.label("Cada instancia tiene su nombre, su RAM y su icono. Las de NeonShy Studios usan una carpeta aparte.", "subtitle")
        )

        val create = Button("Nueva instancia").apply {
            styleClass.add("primary-btn")
            setOnAction { createInstance() }
        }

        val header = HBox(12.0, heading, Ui.grow(), create).apply { alignment = Pos.CENTER_LEFT }

        val body = ScrollPane(list).apply {
            isFitToWidth = true
            styleClass.add("scroll-pane")
            VBox.setVgrow(this, Priority.ALWAYS)
            maxHeight = Double.MAX_VALUE
        }

        return VBox(16.0, header, body).apply {
            maxWidth = Double.MAX_VALUE
            maxHeight = Double.MAX_VALUE
        }
    }

    private fun render() {
        val rows = app.instances
        list.children.setAll(
            if (rows.isEmpty()) listOf(Ui.hint("Todavía no hay instancias."))
            else rows.map { row(it) }
        )
    }

    private fun row(instance: Instance): Node {
        val version = app.versionFor(instance)
        val active = app.selectedInstance?.id == instance.id

        val badge = StackPane(
            Ui.label(instance.icon.ifBlank { instance.name.take(1).uppercase() }, "rail-badge-letter")
        ).apply {
            styleClass.add("rail-badge")
            if (instance.isStudio) styleClass.add("rail-badge-studio")
        }

        val ram = instance.memoryMb.takeIf { it >= 1024 } ?: app.settings.memoryMb
        val meta = buildString {
            append(if (instance.isStudio) "NeonShy Studios" else "Propia")
            append(" · ").append(instance.versionId)
            append(" · ").append(ram).append(" MB")
            if (version == null) append(" · falta descargar")
        }

        val actions = HBox(
            6.0,
            action("Jugar") { app.selectInstance(instance) },
            action("Editar") { editInstance(instance) },
            action("Borrar", disabled = instance.isStudio) { app.removeInstance(instance) }
        ).apply { alignment = Pos.CENTER_RIGHT }

        return HBox(
            12.0,
            badge,
            VBox(1.0, Ui.label(instance.name, "row-name"), Ui.label(meta, "hint")),
            Ui.grow(),
            if (active) Ui.chip("ACTIVA", "chip-live") else Label(""),
            actions
        ).apply {
            styleClass.add("row")
            if (active) styleClass.add("row-active")
            alignment = Pos.CENTER_LEFT
        }
    }

    private fun action(text: String, disabled: Boolean = false, action: () -> Unit): Node =
        Button(text).apply {
            styleClass.add("link-btn")
            isDisable = disabled
            setOnAction { action() }
        }

    // ---------------------------------------------------------------- diálogos

    private fun createInstance() {
        if (app.installations.isEmpty()) {
            app.setStatus("Primero instalá una versión (Instalar versión)")
            return
        }

        val versionBox = ComboBox<String>().apply {
            items.addAll(app.installations.map { it.id })
            value = app.installations.first().id
            maxWidth = Double.MAX_VALUE
        }

        val dialog = Dialog<ButtonType>().apply {
            initOwner(app.stage)
            title = "Nueva instancia"
            headerText = "Elegí la versión de Minecraft"
        }
        style(dialog)

        dialog.dialogPane.content = VBox(
            10.0,
            Ui.field("VERSIÓN"),
            versionBox,
            Ui.hint("Después podés cambiarle el nombre, el icono y la RAM.")
        )
        dialog.dialogPane.buttonTypes.addAll(
            ButtonType("Crear", ButtonBar.ButtonData.OK_DONE),
            ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE)
        )

        val chosen = dialog.showAndWait().orElse(null)
        if (chosen != null && chosen.buttonData == ButtonBar.ButtonData.OK_DONE) {
            versionBox.value?.let { app.createInstance(it) }
        }
    }

    private fun editInstance(instance: Instance) {
        val nameField = TextField(instance.name).apply { styleClass.add("text-input") }
        val iconField = TextField(instance.icon).apply {
            styleClass.add("text-input")
            promptText = "Emoji o letra"
            prefWidth = 90.0
        }

        val ramBox = ComboBox<Int>().apply {
            items.addAll(listOf(0, 2048, 3072, 4096, 6144, 8192, 12288))
            value = instance.memoryMb
            maxWidth = Double.MAX_VALUE
        }

        val dialog = Dialog<ButtonType>().apply {
            initOwner(app.stage)
            title = "Editar ${instance.name}"
            headerText = "Nombre, icono y memoria"
        }
        style(dialog)

        val grid = GridPane().apply {
            hgap = 10.0
            vgap = 8.0
            add(Ui.field("NOMBRE"), 0, 0)
            add(nameField, 1, 0)
            add(Ui.field("ICONO"), 0, 1)
            add(iconField, 1, 1)
            add(Ui.field("RAM"), 0, 2)
            add(ramBox, 1, 2)
        }

        dialog.dialogPane.content = VBox(
            10.0,
            grid,
            Ui.hint("RAM en 0 usa la memoria global de Ajustes. Las de NeonShy Studios no se pueden borrar.")
        )
        dialog.dialogPane.buttonTypes.addAll(
            ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE),
            ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE)
        )

        val chosen = dialog.showAndWait().orElse(null)
        if (chosen != null && chosen.buttonData == ButtonBar.ButtonData.OK_DONE) {
            app.updateInstance(
                instance.copy(
                    name = nameField.text.trim().ifBlank { instance.versionId },
                    icon = iconField.text.trim().take(2),
                    memoryMb = ramBox.value ?: 0
                )
            )
        }
    }

    private fun style(dialog: Dialog<ButtonType>) {
        runCatching {
            Skin::class.java.getResource("/css/app.css")?.let { dialog.dialogPane.stylesheets.add(it.toExternalForm()) }
        }
        dialog.dialogPane.style = "-fx-background-color: #14161b;"
    }
}
