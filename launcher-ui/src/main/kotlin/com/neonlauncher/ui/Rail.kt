package com.neonlauncher.ui

import com.neonlauncher.common.model.Instance
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.ScrollPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox

/**
 * Barra lateral izquierda: marca, instancias separadas por origen (NeonShy Studios / propias)
 * y el perfil abajo. La lista scrollea para no empujar el alto de la ventana.
 */
internal class Rail(private val app: NeonApp) : VBox(10.0) {

    private val items = VBox(6.0)
    private val scroll = ScrollPane(items).apply {
        isFitToWidth = true
        styleClass.add("scroll-pane")
        hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        VBox.setVgrow(this, Priority.ALWAYS)
        maxHeight = Double.MAX_VALUE
    }

    private val profileName = Ui.label("", "rail-profile-name")
    private val profileState = Ui.label("", "rail-profile-state")
    private val profileSkin = StackPane()

    init {
        styleClass.add("rail")
        prefWidth = 268.0
        minWidth = 268.0
        maxWidth = 268.0
        minHeight = 0.0

        val brand = HBox(
            10.0,
            Brand.icon(30.0),
            VBox(0.0, Ui.label("NEON", "rail-brand-a"), Ui.label("LAUNCHER", "rail-brand-b"))
        ).apply {
            alignment = Pos.CENTER_LEFT
            padding = javafx.geometry.Insets(2.0, 4.0, 10.0, 4.0)
        }

        val profile = HBox(10.0, profileSkin, VBox(1.0, profileName, profileState), Ui.grow()).apply {
            styleClass.add("rail-profile")
            alignment = Pos.CENTER_LEFT
            setOnMouseClicked { app.showAccountMenu(this) }
        }

        children.addAll(
            brand,
            scroll,
            action("Nueva instancia") { app.showView(View.INSTANCES) },
            action("Instalar versión") { app.showView(View.VERSIONS) },
            action("Ajustes") { app.showView(View.SETTINGS) },
            profile
        )
    }

    fun refresh() {
        val rows = mutableListOf<Node>()

        rows.add(Ui.label("NEONYSHY STUDIOS", "rail-section"))
        val studio = app.instances.filter { it.isStudio }
        if (studio.isEmpty()) {
            rows.add(Ui.label("Sin instancias del estudio", "hint"))
        } else {
            studio.forEach { rows.add(row(it)) }
        }

        rows.add(spacer(8.0))
        rows.add(Ui.label("PROPIAS", "rail-section"))
        val own = app.instances.filter { !it.isStudio }
        if (own.isEmpty()) {
            rows.add(Ui.label("Creá tu primera instancia", "hint"))
        } else {
            own.forEach { rows.add(row(it)) }
        }

        items.children.setAll(rows)
        Transitions.fadeIn(items, 180.0, 0.7)

        profileName.text = app.currentPlayerName()
        profileState.text = if (app.settings.microsoft != null) "Cuenta Microsoft" else "Cuenta offline"
        profileSkin.children.setAll(SkinHead.node(app.currentPlayerName(), 34.0))
    }

    private fun row(instance: Instance): Node {
        val active = app.selectedInstance?.id == instance.id
        val theme = app.currentTheme()

        val badge = StackPane(
            Ui.label(instance.icon.ifBlank { instance.name.take(1).uppercase() }, "rail-badge-letter")
        ).apply {
            styleClass.add("rail-badge")
            if (instance.isStudio) styleClass.add("rail-badge-studio")
        }

        val version = app.versionFor(instance)
        val subtitle = buildString {
            append(version?.loader ?: if (instance.versionId.isNotBlank()) "Vanilla" else "sin versión")
            append(" · ").append(instance.versionId)
        }

        val text = VBox(
            1.0,
            Ui.label(instance.name, "rail-item-title"),
            Ui.label(subtitle, "rail-item-sub")
        )

        val row = HBox(9.0, badge, text).apply {
            styleClass.add("rail-item")
            if (instance.isStudio) styleClass.add("rail-item-special")
            alignment = Pos.CENTER_LEFT
            setOnMouseClicked { app.selectInstance(instance) }
        }

        if (active) {
            row.style = "-fx-border-color: ${theme.accent}; -fx-background-color: ${theme.accentSoft};"
        } else if (instance.isStudio) {
            row.style = "-fx-border-color: ${theme.accent}80;"
        }
        return row
    }

    private fun action(text: String, action: () -> Unit): Node =
        HBox(8.0, Ui.label(text, "rail-action")).apply {
            styleClass.add("rail-action-row")
            alignment = Pos.CENTER_LEFT
            setOnMouseClicked { action() }
        }

    private fun spacer(height: Double): Region = Region().apply {
        prefHeight = height
        minHeight = height
    }
}
