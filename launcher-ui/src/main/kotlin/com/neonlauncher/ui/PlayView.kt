package com.neonlauncher.ui

import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ContentDisplay
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox

/**
 * Inicio centrado: nombre de la instancia, recuadro con el arte y el botón de jugar al medio.
 * El acento y el fondo cambian según la instancia activa.
 */
internal class PlayView(private val app: NeonApp) : AppView {

    private val eyebrow = Ui.label("", "hero-eyebrow")
    private val title = Ui.label("", "hero-name")
    private val subline = Ui.label("", "hero-sub")
    private val bannerHolder = StackPane()
    private val playIcon = Region().apply { styleClass.add("play-icon") }
    private val playButton = Button("JUGAR")
    private val hint = Ui.label("", "hint")

    override val node: VBox = build()

    override fun refresh() {
        val theme = app.currentTheme()
        val instance = app.selectedInstance
        val version = app.versionFor(instance)

        eyebrow.text = if (instance?.isStudio == true) "POR NEONYSHY STUDIOS" else "INSTANCIA PROPIA"
        title.text = instance?.name ?: "Sin instancia"
        eyebrow.style = "-fx-text-fill: ${theme.accent};"

        subline.text = buildString {
            append("Minecraft ")
            append(version?.id ?: instance?.versionId ?: "—")
            version?.loader?.let { append(" · ").append(it) }
            app.javaRuntime?.let { append(" · ").append(it.label) }
            val ram = instance?.memoryMb?.takeIf { it >= 1024 } ?: app.settings.memoryMb
            append(" · ").append(ram).append(" MB")
        }

        bannerHolder.children.setAll(banner(theme))
        Transitions.fadeIn(bannerHolder, 220.0, 0.5)

        playButton.style = "-fx-background-color: ${theme.accent}; -fx-text-fill: ${theme.onAccent};"
        playIcon.style = "-fx-background-color: ${theme.onAccent};"

        val running = app.gameProcess?.isAlive == true
        playButton.text = if (running) "DETENER" else "JUGAR"
        if (running) {
            if (!playButton.styleClass.contains("stop")) playButton.styleClass.add("stop")
        } else {
            playButton.styleClass.remove("stop")
        }

        val java = app.javaRuntime
        val blocked = instance == null || version == null || java == null ||
            java.major < version.javaMajor ||
            (instance.isStudio && app.updateBlocked())
        playButton.isDisable = !running && blocked
        hint.text = hintText()
    }

    private fun build(): VBox {
        playButton.apply {
            styleClass.add("play-btn")
            graphic = playIcon
            contentDisplay = ContentDisplay.LEFT
            graphicTextGap = 10.0
            minWidth = 280.0
            maxWidth = 280.0
            minHeight = 58.0
            setOnAction { app.onPlayPressed() }
        }

        bannerHolder.apply {
            maxWidth = 820.0
            minHeight = 210.0
            prefHeight = 260.0
        }

        val text = VBox(4.0, eyebrow, title, subline).apply { alignment = Pos.CENTER }

        return VBox(16.0, text, bannerHolder, playButton, hint).apply {
            alignment = Pos.CENTER
            maxWidth = Double.MAX_VALUE
            maxHeight = Double.MAX_VALUE
            VBox.setVgrow(bannerHolder, Priority.ALWAYS)
        }
    }

    private fun banner(theme: InstanceTheme): javafx.scene.Node {
        val instance = app.selectedInstance
        val logo = if (instance?.isStudio == true) Artwork.studioLogo() else null
        val photo = Artwork.heroFor(instance?.versionId, instance?.isStudio == true)

        val background: javafx.scene.Node = when {
            logo != null -> Region().apply {
                style = Artwork.containStyle(logo) + " -fx-background-radius: 16; -fx-background-color: rgba(255,255,255,0.02);"
            }
            photo != null -> Region().apply { style = Artwork.coverStyle(photo) + " -fx-background-radius: 16;" }
            else -> Region().apply { style = "-fx-background-color: rgba(255, 255, 255, 0.035); -fx-background-radius: 16;" }
        }

        val caption = VBox(
            2.0,
            Ui.label(if (instance?.isStudio == true) "TEMPORADA VI" else "INSTANCIA LOCAL", "hero-art-eyebrow"),
            Ui.label(instance?.name ?: "Sin instancia", "hero-art-caption")
        ).apply {
            maxHeight = Region.USE_PREF_SIZE
            StackPane.setAlignment(this, Pos.BOTTOM_LEFT)
            StackPane.setMargin(this, javafx.geometry.Insets(0.0, 0.0, 16.0, 18.0))
        }

        val layers = mutableListOf<javafx.scene.Node>(background)
        when {
            logo != null -> Unit
            photo == null -> layers.add(
                StackPane(Brand.cubeWire(180.0, theme.accent, theme.blobA)).apply { opacity = 0.8 }
            )
            else -> layers.add(
                Region().apply {
                    style = "-fx-background-color: linear-gradient(to top, rgba(6,6,10,0.85), rgba(6,6,10,0.10) 55%, rgba(6,6,10,0.22));"
                }
            )
        }
        layers.add(caption)

        val pane = StackPane().apply {
            styleClass.add("hero-art")
            maxWidth = Double.MAX_VALUE
            maxHeight = Double.MAX_VALUE
            children.addAll(layers)
        }

        val clip = javafx.scene.shape.Rectangle().apply {
            arcWidth = 32.0
            arcHeight = 32.0
        }
        clip.widthProperty().bind(pane.widthProperty())
        clip.heightProperty().bind(pane.heightProperty())
        pane.clip = clip
        return pane
    }

    private fun hintText(): String {
        val instance = app.selectedInstance
        val version = app.versionFor(instance)
        val java = app.javaRuntime
        return when {
            instance == null -> "No hay instancias. Creá una desde la izquierda."
            version == null -> "Falta descargar ${instance.versionId} (Instalar versión)"
            java == null -> "No encontré ningún Java instalado"
            java.major < version.javaMajor -> "Pide Java ${version.javaMajor}+ (tenés ${java.major})"
            app.gameProcess?.isAlive == true -> "Minecraft está corriendo"
            instance.isStudio && app.updateBlocked() ->
                "Actualizá NeonLauncher (${app.pendingUpdateVersion()}) para jugar en ${instance.name}"

            app.settings.microsoft != null -> "Sesión de Microsoft · ${app.currentPlayerName()}"
            else -> "Modo offline · ${app.currentPlayerName()}"
        }
    }
}
