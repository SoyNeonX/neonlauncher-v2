package com.neonlauncher.ui

import com.neonlauncher.common.util.AppInfo
import com.neonlauncher.common.util.Paths2
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import java.nio.file.Files

/**
 * Primera vez, a pantalla completa y sin navegación:
 * paso 1 elegís cómo entrar, paso 2 ponés el nombre si vas offline.
 */
internal class OnboardingView(private val app: NeonApp) : AppView {

    private val stage = StackPane()
    private var step = 0

    private val nameField = TextField()
    private val error = Ui.label("", "hint")

    override val node: VBox = build()

    override fun refresh() {
        nameField.text = app.settings.username
    }

    private fun build(): VBox {
        stage.maxWidth = Double.MAX_VALUE
        stage.maxHeight = Double.MAX_VALUE
        showStep(0)

        val layers = StackPane()
        Artwork.onboarding()?.let { url ->
            layers.children.add(Region().apply { style = Artwork.coverStyle(url) })
            layers.children.add(Region().apply { style = "-fx-background-color: rgba(8, 4, 15, 0.62);" })
        }
        layers.children.add(stage)
        layers.maxWidth = Double.MAX_VALUE
        layers.maxHeight = Double.MAX_VALUE

        val version = Ui.label("NeonLauncher ${AppInfo.VERSION}", "onboarding-version")

        return VBox(0.0, layers, version).apply {
            maxWidth = Double.MAX_VALUE
            maxHeight = Double.MAX_VALUE
            alignment = Pos.CENTER
            padding = javafx.geometry.Insets(0.0, 0.0, 18.0, 0.0)
            VBox.setVgrow(layers, javafx.scene.layout.Priority.ALWAYS)
        }
    }

    private fun showStep(index: Int) {
        step = index
        val content: Node = if (index == 0) chooser() else nameStep()
        stage.children.setAll(content)
        Transitions.slideIn(content, 260.0, 18.0)
    }

    // ---------------------------------------------------------------- paso 1

    private fun chooser(): Node {
        val microsoft = Button("MICROSOFT").apply {
            styleClass.add("play-btn")
            minWidth = 232.0
            maxWidth = 232.0
            minHeight = 56.0
            setOnAction { app.startMicrosoftLogin() }
        }

        val neonId = Button("NEONID").apply {
            styleClass.add("ghost-btn")
            minWidth = 232.0
            maxWidth = 232.0
            minHeight = 56.0
            isDisable = true
            text = "NEONID · PRONTO"
        }

        val offline = Button("JUGAR OFFLINE").apply {
            styleClass.add("ghost-btn")
            minWidth = 476.0
            maxWidth = 476.0
            minHeight = 52.0
            setOnAction { showStep(1) }
        }

        return VBox(
            18.0,
            Brand.icon(84.0),
            Brand.wordmark(19.0, spaced = true),
            Ui.label(
                "Elegí cómo entrar. Con Microsoft jugás online en servidores premium.",
                "onboarding-sub"
            ).apply { isWrapText = true; maxWidth = 476.0 },
            spacer(6.0),
            HBox(12.0, microsoft, neonId).apply { alignment = Pos.CENTER },
            offline,
            Ui.label("NeonID va a ser la cuenta propia de NeonWorld, sin Microsoft.", "hint")
        ).apply {
            alignment = Pos.CENTER
            maxWidth = 640.0
        }
    }

    // ---------------------------------------------------------------- paso 2

    private fun nameStep(): Node {
        nameField.styleClass.add("text-input")
        nameField.promptText = "Tu nombre en el juego"
        nameField.maxWidth = 360.0
        nameField.minHeight = 46.0
        nameField.alignment = Pos.CENTER
        nameField.setOnAction { submitOffline() }

        val start = Button("EMPEZAR").apply {
            styleClass.add("play-btn")
            minWidth = 220.0
            minHeight = 50.0
            setOnAction { submitOffline() }
        }

        val back = Button("VOLVER").apply {
            styleClass.add("ghost-btn")
            minWidth = 140.0
            minHeight = 50.0
            setOnAction { showStep(0) }
        }

        return VBox(
            16.0,
            Brand.icon(72.0),
            Ui.label("¿Cómo te llamamos?", "onboarding-title"),
            Ui.label(
                "Es el nombre que va a usar Minecraft. De 3 a 16 caracteres: letras, números o guión bajo.",
                "onboarding-sub"
            ).apply { isWrapText = true; maxWidth = 420.0 },
            nameField,
            HBox(12.0, start, back).apply { alignment = Pos.CENTER },
            error
        ).apply {
            alignment = Pos.CENTER
            maxWidth = 560.0
        }
    }

    private fun submitOffline() {
        val name = nameField.text.trim()
        if (!com.neonlauncher.common.model.PlayerProfile.isValidName(name)) {
            error.text = "El nombre tiene que tener 3 a 16 caracteres (letras, números o _)."
            return
        }
        app.completeOnboarding(name)
    }

    private fun spacer(height: Double): Region = Region().apply {
        prefHeight = height
        minHeight = height
    }

    /** Ruta sugerida para las fotos: se pueden soltar acá sin tocar código. */
    companion object {
        val artDirectory = Paths2.launcherDataDir().resolve("art").also {
            runCatching { Files.createDirectories(it) }
        }
    }
}
