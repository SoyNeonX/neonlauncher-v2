package com.neonlauncher.ui

import com.neonlauncher.core.runtime.JavaRuntime
import com.neonlauncher.core.runtime.SystemProbe
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.control.ToggleButton
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import java.io.File

internal class SettingsView(private val app: NeonApp) : AppView {

    private val mcDirField = TextField()
    private val usernameField = TextField()
    private val javaCombo = ComboBox<JavaRuntime>()
    private val memoryButtons = LinkedHashMap<Int, ToggleButton>()
    private var memoryChoice: Int = 4096
    private val widthField = TextField()
    private val heightField = TextField()
    private val extraArgs = TextField()
    private val snapshots = CheckBox("Mostrar snapshots en la lista de instancias")
    private val savedLabel = Ui.label("", "hint")

    override val node: Node = build()

    override fun refresh() {
        mcDirField.text = app.settings.gameDirectory ?: app.mcDir.toString()
        usernameField.text = app.settings.username

        javaCombo.items.setAll(app.javaRuntimes)
        javaCombo.value = app.javaRuntimes
            .firstOrNull { it.executable.toString() == app.settings.javaPath }
            ?: app.javaRuntime

        memoryChoice = app.settings.memoryMb
        val closest = memoryButtons.keys.minByOrNull { kotlin.math.abs(it - memoryChoice) }
        memoryButtons[closest]?.isSelected = true
        widthField.text = app.settings.windowWidth.toString()
        heightField.text = app.settings.windowHeight.toString()
        extraArgs.text = app.settings.extraJvmArgs
        snapshots.isSelected = app.settings.showSnapshots
    }

    private fun build(): Node {
        val heading = VBox(
            2.0,
            Ui.title("Ajustes"),
            Ui.label("Cómo y con qué se lanza el juego", "subtitle")
        )

        val content = VBox(
            16.0,
            heading,
            gameSection(),
            javaSection(),
            performanceSection(),
            advancedSection(),
            HBox(12.0, primaryButton(), savedLabel).apply { alignment = Pos.CENTER_LEFT }
        ).apply {
            padding = javafx.geometry.Insets(0.0, 6.0, 24.0, 0.0)
            maxWidth = 980.0
        }

        val scroll = ScrollPane(content).apply {
            isFitToWidth = true
            maxHeight = Double.MAX_VALUE
            styleClass.add("scroll-pane")
            style = "-fx-background-color: transparent;"
        }

        // Fondo con la textura de tierra del GUI de Minecraft, repetida a escala chica.
        val texture = Artwork.guiTexture()
        val dirt = javafx.scene.layout.Region().apply {
            if (texture != null) style = Artwork.tileStyle(texture, 118.0)
            maxWidth = Double.MAX_VALUE
            maxHeight = Double.MAX_VALUE
        }
        val scrim = javafx.scene.layout.Region().apply {
            style = "-fx-background-color: rgba(9, 10, 14, 0.82);"
            maxWidth = Double.MAX_VALUE
            maxHeight = Double.MAX_VALUE
        }

        return javafx.scene.layout.StackPane(dirt, scrim, scroll).apply {
            alignment = Pos.TOP_CENTER
            padding = javafx.geometry.Insets(18.0)
            maxWidth = Double.MAX_VALUE
            maxHeight = Double.MAX_VALUE
        }
    }

    private fun section(title: String, vararg body: Node): VBox =
        VBox(10.0, Ui.section(title), *body).apply { styleClass.add("card") }

    private fun gameSection(): Node {
        mcDirField.styleClass.add("text-input")
        HBox.setHgrow(mcDirField, Priority.ALWAYS)
        usernameField.styleClass.add("text-input")

        val browse = Button("Elegir…").apply {
            styleClass.add("ghost-btn")
            style += "-fx-padding: 9 14 9 14;"
            setOnAction { chooseDirectory() }
        }

        return section(
            "JUEGO",
            Ui.field("CARPETA DE MINECRAFT"),
            HBox(8.0, mcDirField, browse).apply { alignment = Pos.CENTER_LEFT },
            Ui.field("NOMBRE DE JUGADOR"),
            usernameField,
            Ui.hint("Se usa para el perfil offline y para el UUID local, igual que el juego.")
        )
    }

    private fun javaSection(): Node {
        javaCombo.styleClass.add("combo")
        javaCombo.maxWidth = Double.MAX_VALUE

        val rescan = Button("Volver a detectar").apply {
            styleClass.add("ghost-btn")
            style += "-fx-padding: 9 14 9 14;"
            setOnAction { app.reloadEnvironment() }
        }

        val downloadJava = Button("Descargar Java 21").apply {
            styleClass.add("ghost-btn")
            style += "-fx-padding: 9 14 9 14;"
            setOnAction { app.installJava(21) }
        }

        return section(
            "JAVA",
            Ui.field("RUNTIME"),
            javaCombo,
            HBox(
                10.0,
                rescan,
                downloadJava,
                Ui.hint("Se buscan JDK en JAVA_HOME, PATH y rutas típicas. Si no hay, se puede bajar uno.")
            ).apply { alignment = Pos.CENTER_LEFT }
        )
    }

    private fun performanceSection(): Node {
        val options = listOf(2048, 3072, 4096, 6144, 8192, 12288)
        val picker = HBox(8.0).apply {
            alignment = Pos.CENTER_LEFT
            options.forEach { megabytes ->
                val button = ToggleButton(formatMemory(megabytes)).apply {
                    styleClass.add("seg")
                    setOnAction {
                        memoryChoice = megabytes
                        if (isSelected) memoryButtons.values.forEach { if (it !== this) it.isSelected = false }
                    }
                }
                memoryButtons[megabytes] = button
                children.add(button)
            }
        }

        val recommended = app.hardware?.let { SystemProbe.recommendedMemoryMb(it) }
        val recommendedHint = Ui.hint(
            when {
                recommended == null -> ""
                else -> "Tu equipo: ${app.hardware?.ramLabel} · ${app.hardware?.cores} núcleos. " +
                    "Recomendado para Minecraft: ${formatMemory(recommended)}."
            }
        ).apply {
            isWrapText = true
            minWidth = 0.0
            maxWidth = Double.MAX_VALUE
        }

        val recommendedRow = HBox(
            10.0,
            recommendedHint,
            Button("Usar ${formatMemory(recommended ?: 4096)}").apply {
                styleClass.add("link-btn")
                isDisable = recommended == null
                setOnAction {
                    recommended?.let { value ->
                        memoryChoice = value
                        memoryButtons.keys.minByOrNull { kotlin.math.abs(it - value) }?.let { nearest ->
                            memoryButtons[nearest]?.isSelected = true
                        }
                    }
                }
            }
        ).apply {
            alignment = Pos.CENTER_LEFT
            HBox.setHgrow(recommendedHint, Priority.ALWAYS)
        }

        widthField.styleClass.add("text-input")
        heightField.styleClass.add("text-input")
        widthField.prefWidth = 90.0
        heightField.prefWidth = 90.0

        return section(
            "RENDIMIENTO",
            Ui.field("MEMORIA MÁXIMA PARA EL JUEGO"),
            picker,
            recommendedRow,
            Ui.field("RESOLUCIÓN DE VENTANA"),
            HBox(
                8.0,
                widthField,
                Ui.label("×", "value"),
                heightField,
                Ui.hint("Se aplica al lanzar (--width / --height).")
            ).apply { alignment = Pos.CENTER_LEFT }
        )
    }

    private fun formatMemory(megabytes: Int): String =
        if (megabytes % 1024 == 0) "${megabytes / 1024} GB" else "$megabytes MB"

    private fun advancedSection(): Node {
        extraArgs.styleClass.add("text-input")
        snapshots.styleClass.add("check")

        return section(
            "AVANZADO",
            Ui.field("ARGUMENTOS JVM EXTRA"),
            extraArgs,
            snapshots
        )
    }

    private fun primaryButton(): Button = Button("Guardar cambios").apply {
        styleClass.add("primary-btn")
        setOnAction { save() }
    }

    private fun save() {
        val updated = app.settings.copy(
            username = usernameField.text,
            gameDirectory = mcDirField.text.trim().ifBlank { null },
            javaPath = javaCombo.value?.executable?.toString(),
            memoryMb = memoryChoice,
            windowWidth = widthField.text.trim().toIntOrNull() ?: app.settings.windowWidth,
            windowHeight = heightField.text.trim().toIntOrNull() ?: app.settings.windowHeight,
            extraJvmArgs = extraArgs.text.trim(),
            showSnapshots = snapshots.isSelected
        )
        app.applySettings(updated)
        savedLabel.text = "Guardado ✓"
        refresh()
    }

    private fun chooseDirectory() {
        val chooser = DirectoryChooser()
        chooser.title = "Carpeta de Minecraft"
        runCatching { chooser.initialDirectory = File(mcDirField.text) }
        chooser.showDialog(app.stage)?.let { mcDirField.text = it.absolutePath }
    }
}
