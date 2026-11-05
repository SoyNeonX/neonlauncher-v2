package com.neonlauncher.ui.design

import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.effect.DropShadow
import javafx.scene.effect.GaussianBlur
import javafx.scene.image.WritableImage
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.paint.CycleMethod
import javafx.scene.paint.LinearGradient
import javafx.scene.paint.RadialGradient
import javafx.scene.paint.Stop
import javafx.scene.shape.Arc
import javafx.scene.shape.ArcTo
import javafx.scene.shape.ArcType
import javafx.scene.shape.Circle
import javafx.scene.shape.ClosePath
import javafx.scene.shape.LineTo
import javafx.scene.shape.MoveTo
import javafx.scene.shape.Path as FxPath
import javafx.scene.shape.Polygon
import javafx.scene.shape.Rectangle
import javafx.scene.shape.StrokeLineCap
import javafx.scene.shape.StrokeLineJoin
import javafx.scene.text.Font
import javafx.scene.text.FontPosture
import javafx.scene.text.FontWeight
import javafx.scene.text.Text
import javafx.stage.Stage
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO

/**
 * Laboratorio de diseño: renderiza mockups a PNG para elegir dirección visual.
 * `.\gradlew.bat mockups` → docs/mockup-*.png
 */
fun main(args: Array<String>) {
    Application.launch(Mockups::class.java, *(args.ifEmpty { arrayOf("docs") }))
}

class Mockups : Application() {

    override fun start(stage: Stage) {
        val out = Paths.get(parameters.raw.firstOrNull() ?: "docs")
        Files.createDirectories(out)

        val variants: List<Pair<String, Pane>> = listOf(
            "a-neon-hero" to hero(VIOLET, "NEON SMP", "Listo para jugar", "Minecraft 1.21.11 · Fabric 0.18.4 · Java 21"),
            "e-neonworld" to hero(NEONWORLD, "NEONWORLD VI", "Nos vemos del otro lado", "Minecraft 1.21.11 · abre en 12 días"),
            "d-logos" to logoSheet(),
            "b-claro" to light(),
            "c-compacto" to compact()
        )

        for ((name, root) in variants) {
            val scene = Scene(root, 1180.0, 740.0)
            stage.scene = scene
            stage.sizeToScene()
            stage.show()
            root.applyCss()
            root.layout()
            writePng(scene.snapshot(null), out.resolve("mockup-$name.png"))
            println("mockup -> mockup-$name.png")
        }
        Platform.exit()
    }

    private fun writePng(image: WritableImage, target: Path) {
        val w = image.width.toInt()
        val h = image.height.toInt()
        val buffered = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val reader = image.pixelReader
        for (y in 0 until h) for (x in 0 until w) buffered.setRGB(x, y, reader.getArgb(x, y))
        ImageIO.write(buffered, "png", target.toFile())
    }
}

// ---------------------------------------------------------------- temas

private class HeroTheme(
    val bg: String,
    val panel: String,
    val border: String,
    val ink: String,
    val muted: String,
    val dim: String,
    val value: String,
    val accentA: String,
    val accentB: String,
    val onAccent: String,
    val green: String,
    val blobA: String,
    val blobB: String,
    val glow: String
)

private val VIOLET = HeroTheme(
    bg = "#07070C", panel = "#101019", border = "rgba(255,255,255,0.07)",
    ink = "#FFFFFF", muted = "#8B8BA0", dim = "#66667E", value = "#D6D6E6",
    accentA = "#7C5CFF", accentB = "#22D3EE", onAccent = "#0B0B12", green = "#34D399",
    blobA = "#7C5CFF", blobB = "#22D3EE", glow = "rgba(34,211,238,0.45)"
)

/** NeonWorld VI: atardecer sobre violeta profundo. Ni rosa ni palmeras de GTA VI. */
private val NEONWORLD = HeroTheme(
    bg = "#0B0716", panel = "#150E24", border = "rgba(255,255,255,0.08)",
    ink = "#FFF4E6", muted = "#A08FB8", dim = "#7A6A90", value = "#F3E7DA",
    accentA = "#FF7A45", accentB = "#FF3C8E", onAccent = "#140610", green = "#52E5A3",
    blobA = "#FF3C8E", blobB = "#7B5CFF", glow = "rgba(255,60,142,0.55)"
)

// ---------------------------------------------------------------- variante hero

private fun hero(t: HeroTheme, eyebrow: String, headline: String, sub: String): Pane {
    val root = StackPane()
    root.style = "-fx-background-color: ${t.bg};"

    val art = Pane().apply {
        children.addAll(
            blob(430.0, Color.web(t.blobA), 820.0, -220.0, 0.55),
            blob(380.0, Color.web(t.blobB), 1020.0, 430.0, 0.30),
            blob(320.0, Color.web(t.accentA), -140.0, 560.0, 0.18)
        )
        clip = Rectangle(1180.0, 740.0)
    }

    val brand = StackPane(scaleMark(brandCube(t.accentA, t.accentB), 30.0)).apply {
        prefWidth = 30.0
        prefHeight = 30.0
        minWidth = 30.0
        minHeight = 30.0
        maxWidth = 30.0
        maxHeight = 30.0
    }

    val top = HBox(
        11.0,
        brand,
        VBox(0.0, text("NEONWORLD", 14.5, t.ink, true), text("LAUNCHER", 9.0, t.muted, true)),
        grow(),
        HBox(26.0, text("Inicio", 12.5, t.ink, true), text("Instancias", 12.5, t.muted), text("Ajustes", 12.5, t.muted)),
        avatar("P", "#1C1526", t.value)
    ).apply {
        alignment = Pos.CENTER_LEFT
        padding = Insets(24.0, 40.0, 0.0, 40.0)
    }

    val left = VBox(16.0).apply {
        maxWidth = 540.0
        minWidth = 520.0
        children.addAll(
            HBox(
                8.0,
                pill("SERVIDOR ONLINE", t.green, "rgba(82,229,163,0.12)", t.green),
                pill("12 / 50", t.value, "rgba(255,255,255,0.06)")
            ).apply { alignment = Pos.CENTER_LEFT },
            VBox(
                2.0,
                text(eyebrow, 11.5, t.muted, true),
                text(headline, 42.0, t.ink, true).apply { maxWidth = 520.0 },
                text(sub, 13.5, t.muted)
            ),
            vgrow(),
            HBox(12.0, playButton(292.0, t.accentA, t.accentB, t.onAccent, t.glow), outlineButton("INSTANCIAS", t.value)),
            text("Sesión anterior: hace 2 días · 3 GB de RAM", 11.0, t.dim)
        )
    }

    val statusRow = HBox(
        8.0,
        dot(t.green),
        text("Todo listo para lanzar", 11.0, t.muted),
        grow(),
        text("NeonWorld Launcher v2.1.0", 10.5, t.dim)
    ).apply {
        alignment = Pos.CENTER_LEFT
        padding = Insets(10.0, 40.0, 12.0, 40.0)
    }

    val serverCard = card(t.panel, t.border, 18.0, 20.0) {
        children.addAll(
            section("AHORA EN NEONWORLD"),
            text("Survival · Temporada VI", 15.0, t.ink, true),
            text("El mundo abre en 12 días. Mientras tanto, dejá tu skin lista.", 12.0, t.muted),
            progressBar(0.24, t.green),
            HBox(8.0, text("12 de 50 lugares", 11.0, t.muted), grow(), text("24 ms", 11.0, t.green, true))
        )
    }

    val contentCard = card(t.panel, t.border, 18.0, 20.0) {
        children.addAll(
            section("TU INSTANCIA"),
            infoRow("Versión", "1.21.11", t.muted, t.value),
            infoRow("Loader", "Fabric 0.18.4", t.muted, t.value),
            infoRow("Java", "21 · Temurin", t.muted, t.value),
            infoRow("Sincronizado", "hace 5 min", t.muted, t.value)
        )
    }

    val newsCard = card(t.panel, t.border, 18.0, 20.0) {
        children.addAll(
            section("NOVEDADES"),
            newsRow("Temporada VI: fecha confirmada", "hace 2 días", t.value, t.dim),
            newsRow("Se suman 4 mods nuevos", "hace 1 semana", t.value, t.dim),
            newsRow("Nuevo spawn de invierno", "hace 2 semanas", t.value, t.dim)
        )
    }

    val right = VBox(14.0, serverCard, contentCard, newsCard).apply {
        prefWidth = 380.0
        minWidth = 360.0
        HBox.setHgrow(this, Priority.ALWAYS)
    }

    val body = HBox(36.0, left, right).apply { padding = Insets(42.0, 40.0, 16.0, 40.0) }

    val column = VBox(0.0, top, body, statusRow).apply { maxHeight = Double.MAX_VALUE }
    VBox.setVgrow(body, Priority.ALWAYS)

    root.children.addAll(art, column)
    return root
}

// ---------------------------------------------------------------- planilla de logos

private fun logoSheet(): Pane {
    val root = VBox(22.0).apply {
        style = "-fx-background-color: #0B0716; -fx-padding: 34;"
    }

    val header = VBox(
        2.0,
        text("Logos candidatos", 22.0, "#FFF4E6", true),
        text("Marcas de 180 px, sobre placa; abajo la versión chica de 36 px para ver si aguanta.", 11.5, "#A08FB8")
    )

    val grid = GridPane().apply {
        hgap = 18.0
        vgap = 18.0
    }

    val concepts = listOf<Pair<String, () -> Group>>(
        "Cubo neón" to { brandCube("#FF7A45", "#FF3C8E") },
        "Portal" to { brandPortal("#FF7A45", "#FF3C8E") },
        "Sol partido" to { brandSun("#FFB347", "#FF3C8E") },
        "N de neón" to { brandNeonN("#FF7A45", "#FF3C8E") },
        "Hexágono" to { brandHexN("#FF7A45", "#7B5CFF") },
        "Monograma NW" to { brandMonogram("#FF7A45", "#FF3C8E") }
    )

    concepts.forEachIndexed { index, (name, factory) ->
        val tile = VBox(
            10.0,
            StackPane(factory()).apply {
                style = "-fx-background-color: #120C1E; -fx-background-radius: 24; -fx-border-color: rgba(255,255,255,0.07); -fx-border-radius: 24; -fx-border-width: 1;"
                prefWidth = 200.0
                prefHeight = 200.0
                minWidth = 200.0
                minHeight = 200.0
            },
            text(name, 11.5, "#A08FB8", true)
        ).apply { alignment = Pos.CENTER }

        grid.add(tile, index % 3, index / 3)
    }

    val small = HBox(14.0).apply {
        alignment = Pos.CENTER_LEFT
        concepts.forEach { (_, factory) ->
            children.add(
                StackPane(scaleMark(factory(), 36.0)).apply {
                    style = "-fx-background-color: #120C1E; -fx-background-radius: 8;"
                    prefWidth = 36.0
                    prefHeight = 36.0
                    minWidth = 36.0
                    minHeight = 36.0
                    maxWidth = 36.0
                    maxHeight = 36.0
                }
            )
        }
    }

    root.children.addAll(header, grid, text("A 36 px", 10.0, "#7A6A90", true), small)
    return root
}

private fun scaleMark(mark: Group, target: Double): Group {
    val scale = target / 180.0
    mark.scaleX = scale
    mark.scaleY = scale
    return mark
}

// ---------------------------------------------------------------- marcas

/** Cubo isométrico: el más "Minecraft" sin ser la tierra de Mojang. */
private fun brandCube(from: String, to: String): Group {
    val top = Polygon(90.0, 22.0, 158.0, 60.0, 90.0, 98.0, 22.0, 60.0).apply {
        fill = LinearGradient(0.0, 0.0, 1.0, 1.0, true, CycleMethod.NO_CYCLE, Stop(0.0, Color.web(from)), Stop(1.0, Color.web(to)))
    }
    val left = Polygon(22.0, 60.0, 90.0, 98.0, 90.0, 166.0, 22.0, 128.0).apply { fill = Color.web(from, 0.42) }
    val right = Polygon(158.0, 60.0, 90.0, 98.0, 90.0, 166.0, 158.0, 128.0).apply { fill = Color.web(to, 0.42) }
    return Group(top, left, right).apply {
        effect = DropShadow(0.0, 0.0, 30.0, Color.web(from, 0.55))
    }
}

/** Portal: la puerta al mundo. */
private fun brandPortal(from: String, to: String): Group {
    val arch = FxPath().apply {
        fill = LinearGradient(0.0, 0.0, 0.0, 1.0, true, CycleMethod.NO_CYCLE, Stop(0.0, Color.web(from)), Stop(1.0, Color.web(to)))
        elements.addAll(
            MoveTo(40.0, 172.0),
            LineTo(40.0, 94.0),
            ArcTo(58.0, 58.0, 0.0, 140.0, 94.0, false, true),
            LineTo(140.0, 172.0),
            ClosePath()
        )
    }
    return Group(arch).apply { effect = DropShadow(0.0, 0.0, 34.0, Color.web(to, 0.55)) }
}

/** Sol partido por barras: atardecer, sin palmeras. */
private fun brandSun(from: String, to: String): Group {
    val sun = Arc(0.0, 0.0, 62.0, 62.0, 0.0, 180.0).apply {
        type = ArcType.CHORD
        fill = LinearGradient(0.0, 0.0, 0.0, 1.0, true, CycleMethod.NO_CYCLE, Stop(0.0, Color.web(from)), Stop(1.0, Color.web(to)))
        layoutX = 28.0
        layoutY = 40.0
    }
    val bars = Group(
        Rectangle(130.0, 9.0).apply { fill = Color.web("#0B0716"); layoutX = 25.0; layoutY = 74.0 },
        Rectangle(130.0, 7.0).apply { fill = Color.web("#0B0716"); layoutX = 25.0; layoutY = 92.0 },
        Rectangle(130.0, 5.0).apply { fill = Color.web("#0B0716"); layoutX = 25.0; layoutY = 108.0 },
        Rectangle(130.0, 3.0).apply { fill = Color.web("#0B0716"); layoutX = 25.0; layoutY = 122.0 }
    )
    return Group(sun, bars).apply { effect = DropShadow(0.0, 0.0, 30.0, Color.web(to, 0.45)) }
}

/** N de tubo de neón. */
private fun brandNeonN(from: String, to: String): Group {
    val path = FxPath().apply {
        elements.addAll(
            MoveTo(54.0, 148.0),
            LineTo(54.0, 48.0),
            LineTo(126.0, 148.0),
            LineTo(126.0, 48.0)
        )
        fill = null
        stroke = LinearGradient(0.0, 0.0, 0.0, 1.0, true, CycleMethod.NO_CYCLE, Stop(0.0, Color.web(from)), Stop(1.0, Color.web(to)))
        strokeWidth = 21.0
        strokeLineCap = StrokeLineCap.ROUND
        strokeLineJoin = StrokeLineJoin.ROUND
    }
    return Group(path).apply { effect = DropShadow(0.0, 0.0, 28.0, Color.web(to, 0.65)) }
}

/** Hexágono con la N tallada. */
private fun brandHexN(from: String, to: String): Group {
    val hex = Polygon(90.0, 16.0, 154.0, 53.0, 154.0, 127.0, 90.0, 164.0, 26.0, 127.0, 26.0, 53.0).apply {
        fill = LinearGradient(0.0, 0.0, 1.0, 1.0, true, CycleMethod.NO_CYCLE, Stop(0.0, Color.web(from)), Stop(1.0, Color.web(to)))
    }
    val letter = Text("N").apply {
        font = Font.font(FONT, FontWeight.BLACK, 76.0)
        x = 63.0
        y = 118.0
        fill = Color.web("#0B0716")
    }
    return Group(hex, letter).apply { effect = DropShadow(0.0, 0.0, 30.0, Color.web(from, 0.5)) }
}

/** Monograma inclinado. */
private fun brandMonogram(from: String, to: String): Group {
    val letters = Text("NW").apply {
        font = Font.font(FONT, FontWeight.BLACK, FontPosture.ITALIC, 68.0)
        x = 22.0
        y = 116.0
        fill = LinearGradient(0.0, 0.0, 1.0, 1.0, true, CycleMethod.NO_CYCLE, Stop(0.0, Color.web(from)), Stop(1.0, Color.web(to)))
    }
    return Group(letters).apply { effect = DropShadow(0.0, 0.0, 26.0, Color.web(to, 0.5)) }
}

// ---------------------------------------------------------------- variante B (clara)

private fun light(): Pane {
    val root = StackPane()
    root.style = "-fx-background-color: #FAFAFC;"

    val sidebar = VBox(24.0).apply {
        style = "-fx-background-color: #F3F3F8; -fx-border-color: #E6E6EF; -fx-border-width: 0 1 0 0;"
        padding = Insets(26.0, 16.0, 16.0, 16.0)
        prefWidth = 236.0
        minWidth = 236.0
        children.addAll(
            HBox(
                10.0,
                brandMark("#7C5CFF", "#4C6FFF"),
                VBox(0.0, text("NEON", 14.5, "#16161D", true), text("LAUNCHER", 9.0, "#8A8A9B", true))
            ).apply { alignment = Pos.CENTER_LEFT },
            VBox(3.0, navItem("Inicio", true), navItem("Instancias", false), navItem("Ajustes", false)),
            vgrow(),
            card("#FFFFFF", "#E6E6EF", 14.0, 12.0) {
                children.addAll(
                    HBox(9.0, avatar("P", "#EDEBFF", "#5B45D6"), VBox(1.0, text("Player", 12.5, "#16161D", true), text("Offline · Java 21", 10.5, "#8A8A9B"))),
                    text("Iniciar sesión con Microsoft", 11.0, "#5B45D6", true)
                )
            }
        )
    }

    val header = VBox(
        3.0,
        text("Buenas tardes,", 14.0, "#8A8A9B"),
        text("Player.", 40.0, "#16161D", true),
        text("Elegí una versión y entrá al mundo.", 13.5, "#8A8A9B")
    )

    val heroCard = card("#FFFFFF", "#E9E9F1", 20.0, 26.0) {
        style += "-fx-effect: dropshadow(gaussian, rgba(22,22,29,0.07), 26, 0.1, 0, 10);"
        HBox.setHgrow(this, Priority.ALWAYS)
        children.addAll(
            section("INSTANCIA"),
            HBox(
                12.0,
                text("1.21.11", 32.0, "#16161D", true),
                pill("Fabric 0.18.4", "#5B45D6", "#EFECFF"),
                pill("Java 21", "#3F7A54", "#E7F5EC")
            ).apply { alignment = Pos.CENTER_LEFT },
            text("20 mods activos · 3 GB de RAM", 12.0, "#8A8A9B"),
            vgrow(),
            darkButton("JUGAR")
        )
    }

    val quickCard = card("#FFFFFF", "#E9E9F1", 20.0, 26.0) {
        style += "-fx-effect: dropshadow(gaussian, rgba(22,22,29,0.07), 26, 0.1, 0, 10);"
        prefWidth = 300.0
        minWidth = 280.0
        children.addAll(
            section("AJUSTES RÁPIDOS"),
            infoRow("Memoria", "3072 MB", "#8A8A9B", "#16161D"),
            infoRow("Resolución", "1280 × 720", "#8A8A9B", "#16161D"),
            infoRow("Carpeta", ".minecraft", "#8A8A9B", "#16161D"),
            vgrow(),
            text("Editar ajustes →", 11.5, "#5B45D6", true)
        )
    }

    val row = HBox(20.0, heroCard, quickCard).apply { VBox.setVgrow(this, Priority.ALWAYS) }

    val footer = HBox(
        8.0,
        dot("#34D399"),
        text("Listo · 1.21.11 · Java 21 · Temurin", 11.0, "#8A8A9B"),
        grow(),
        text("v2.0.0", 10.5, "#B4B4C2")
    ).apply { alignment = Pos.CENTER_LEFT }

    val main = VBox(26.0, header, row, footer).apply {
        padding = Insets(44.0, 44.0, 26.0, 44.0)
        HBox.setHgrow(this, Priority.ALWAYS)
    }

    root.children.addAll(HBox(0.0, sidebar, main))
    return root
}

// ---------------------------------------------------------------- variante C

private fun compact(): Pane {
    val root = StackPane()
    root.style = "-fx-background-color: #0C0C12;"

    val rail = VBox(10.0).apply {
        style = "-fx-background-color: #0F0F17; -fx-border-color: rgba(255,255,255,0.06); -fx-border-width: 0 1 0 0;"
        padding = Insets(20.0, 14.0, 18.0, 14.0)
        prefWidth = 76.0
        minWidth = 76.0
        children.addAll(brandMark("#7C5CFF", "#22D3EE"), vgrow(), railButton(true), railButton(false), railButton(false), vgrow(), avatar("P", "#171722", "#B9B9D6"))
    }

    val header = HBox(
        12.0,
        VBox(2.0, text("Inicio", 20.0, "#F5F5FA", true), text("1.21.11 · Fabric 0.18.4 · Java 21", 11.5, "#7C7C96")),
        grow(),
        pill("SERVIDOR ONLINE", "#34D399", "rgba(52,211,153,0.12)", "#34D399"),
        pill("12 / 50", "#C9C9D9", "rgba(255,255,255,0.06)")
    ).apply { alignment = Pos.CENTER_LEFT }

    val playCard = card("#111119", "rgba(255,255,255,0.07)", 16.0, 22.0) {
        HBox.setHgrow(this, Priority.ALWAYS)
        children.addAll(
            HBox(
                24.0,
                VBox(4.0, section("PERFIL ACTIVO"), text("Player", 22.0, "#FFFFFF", true), text("Cuenta offline · UUID local", 11.0, "#7C7C96")),
                grow(),
                VBox(6.0, text("3 GB", 18.0, "#F5F5FA", true), text("RAM", 10.0, "#7C7C96")).apply { alignment = Pos.CENTER_RIGHT }
            ).apply { alignment = Pos.CENTER_LEFT },
            vgrow(),
            playButton(180.0, "#7C5CFF", "#22D3EE", "#0B0B12", "rgba(34,211,238,0.45)")
        )
    }

    val modsCard = card("#111119", "rgba(255,255,255,0.07)", 16.0, 20.0) {
        prefWidth = 290.0
        minWidth = 270.0
        children.addAll(
            section("INSTANCIAS"),
            HBox(8.0, text("3", 26.0, "#7C5CFF", true), text("guardadas", 11.0, "#7C7C96")).apply { alignment = Pos.BASELINE_LEFT },
            text("NeonWorld VI · 1.21.11", 11.0, "#7C7C96"),
            vgrow(),
            text("Administrar →", 11.5, "#9E8CFF", true)
        )
    }

    val topRow = HBox(14.0, playCard, modsCard).apply { VBox.setVgrow(this, Priority.ALWAYS) }

    val console = card("#08080D", "rgba(255,255,255,0.07)", 14.0, 14.0) {
        children.addAll(
            HBox(8.0, section("CONSOLA"), grow(), text("limpiar", 10.0, "#66667E")),
            text("[13:45:22] [Datafixer Bootstrap/INFO]: 287 optimizations took 560 ms", 10.5, "#6FA8FF"),
            text("[13:45:24] [Render thread/INFO]: Setting user: Player", 10.5, "#8B8BA0"),
            text("[13:45:25] [Render thread/INFO]: Backend library: LWJGL 3.3.3", 10.5, "#8B8BA0")
        )
        prefHeight = 132.0
        minHeight = 132.0
    }

    val main = VBox(14.0, header, topRow, console).apply {
        padding = Insets(24.0, 24.0, 20.0, 24.0)
        HBox.setHgrow(this, Priority.ALWAYS)
    }

    root.children.addAll(HBox(0.0, rail, main))
    return root
}

// ---------------------------------------------------------------- piezas

private const val FONT = "Segoe UI Variable Display"

private fun text(value: String, size: Double, color: String, bold: Boolean = false): Label = Label(value).apply {
    style = "-fx-font-family: \"$FONT\"; -fx-font-size: ${size}px; -fx-text-fill: $color;" +
        if (bold) " -fx-font-weight: bold;" else ""
}

private fun section(value: String): Label = Label(value).apply {
    style = "-fx-font-family: \"$FONT\"; -fx-font-size: 9.5px; -fx-font-weight: bold; -fx-text-fill: #6E6E88;"
}

private fun grow(): Region = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }

private fun vgrow(): Region = Region().apply { VBox.setVgrow(this, Priority.ALWAYS) }

private fun dot(color: String): Circle = Circle(3.0).apply { fill = Color.web(color) }

private fun brandMark(from: String, to: String): StackPane = StackPane(
    Label("N").apply {
        style = "-fx-font-family: \"$FONT\"; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0A0A12;"
    }
).apply {
    style = "-fx-background-color: linear-gradient(to bottom right, $from, $to);" +
        " -fx-background-radius: 10; -fx-min-width: 34; -fx-min-height: 34; -fx-max-width: 34; -fx-max-height: 34;"
}

private fun avatar(letter: String, bg: String, fg: String): StackPane = StackPane(
    Label(letter).apply {
        style = "-fx-font-family: \"$FONT\"; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: $fg;"
    }
).apply {
    style = "-fx-background-color: $bg; -fx-background-radius: 999;" +
        " -fx-min-width: 30; -fx-min-height: 30; -fx-max-width: 30; -fx-max-height: 30;"
}

private fun pill(value: String, fg: String, bg: String, dotColor: String? = null): Node {
    val content = HBox(6.0).apply {
        alignment = Pos.CENTER_LEFT
        dotColor?.let { children.add(dot(it)) }
        children.add(
            Label(value).apply {
                style = "-fx-font-family: \"$FONT\"; -fx-font-size: 9.5px; -fx-font-weight: bold; -fx-text-fill: $fg;"
            }
        )
    }
    return content.apply {
        style = "-fx-background-color: $bg; -fx-background-radius: 999; -fx-padding: 5 10 5 10;"
    }
}

private fun navItem(value: String, active: Boolean): Node = Label(value).apply {
    style = "-fx-font-family: \"$FONT\"; -fx-font-size: 12.5px; -fx-text-fill: ${if (active) "#16161D" else "#6B6B7B"};" +
        "-fx-font-weight: ${if (active) "bold" else "normal"};" +
        "-fx-background-color: ${if (active) "#FFFFFF" else "transparent"};" +
        "-fx-background-radius: 9; -fx-padding: 9 12 9 12;" +
        if (active) " -fx-border-color: #E6E6EF; -fx-border-radius: 9; -fx-border-width: 1;" else ""
    maxWidth = Double.MAX_VALUE
    alignment = Pos.CENTER_LEFT
}

private fun railButton(active: Boolean): Node = Region().apply {
    style = "-fx-background-color: ${if (active) "#1B1B2A" else "rgba(255,255,255,0.04)"};" +
        " -fx-background-radius: 10; -fx-min-width: 44; -fx-min-height: 40; -fx-max-width: 44; -fx-max-height: 40;"
    if (active) style += "-fx-border-color: rgba(124,92,255,0.6); -fx-border-radius: 10; -fx-border-width: 1;"
}

private fun card(bg: String, border: String, radius: Double, pad: Double, build: VBox.() -> Unit): VBox =
    VBox(11.0).apply {
        style = "-fx-background-color: $bg; -fx-background-radius: $radius; -fx-border-color: $border; -fx-border-radius: $radius; -fx-border-width: 1; -fx-padding: $pad;"
        build()
    }

private fun infoRow(name: String, value: String, nameColor: String, valueColor: String): Node = HBox(
    8.0,
    text(name, 11.0, nameColor),
    grow(),
    text(value, 11.0, valueColor, true)
).apply { alignment = Pos.CENTER_LEFT }

private fun newsRow(title: String, whenLabel: String, titleColor: String, whenColor: String): Node = VBox(
    1.0,
    text(title, 11.5, titleColor),
    text(whenLabel, 10.0, whenColor)
)

private fun outlineButton(value: String, fg: String): Node = Label(value).apply {
    style = "-fx-font-family: \"$FONT\"; -fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: $fg;" +
        " -fx-background-color: rgba(255,255,255,0.04); -fx-background-radius: 15;" +
        " -fx-border-color: rgba(255,255,255,0.14); -fx-border-radius: 15; -fx-border-width: 1; -fx-padding: 0 22 0 22;"
    minHeight = 54.0
}

private fun progressBar(fraction: Double, color: String): Node {
    val track = Region().apply {
        style = "-fx-background-color: rgba(255,255,255,0.08); -fx-background-radius: 999;"
        prefHeight = 6.0
        minHeight = 6.0
        maxWidth = Double.MAX_VALUE
    }
    val fill = Region().apply {
        style = "-fx-background-color: $color; -fx-background-radius: 999;"
        prefHeight = 6.0
        minHeight = 6.0
        prefWidth = 300.0 * fraction
        maxWidth = 300.0 * fraction
    }
    return StackPane(track, fill).apply {
        alignment = Pos.CENTER_LEFT
        maxWidth = Double.MAX_VALUE
    }
}

private fun playButton(width: Double, from: String, to: String, onAccent: String, glow: String): Node {
    val triangle = Polygon(0.0, 0.0, 13.0, 8.0, 0.0, 16.0).apply { fill = Color.web(onAccent) }
    return HBox(12.0, triangle, text("JUGAR", 15.0, onAccent, true)).apply {
        alignment = Pos.CENTER
        minWidth = width
        maxWidth = width
        style = "-fx-background-color: linear-gradient(to right, $from, $to);" +
            " -fx-background-radius: 15; -fx-padding: 18 30 18 30;" +
            " -fx-effect: dropshadow(gaussian, $glow, 26, 0.25, 0, 8);"
    }
}

private fun darkButton(value: String): Node = HBox(
    12.0,
    Polygon(0.0, 0.0, 12.0, 7.0, 0.0, 14.0).apply { fill = Color.WHITE },
    text(value, 14.0, "#FFFFFF", true)
).apply {
    alignment = Pos.CENTER
    style = "-fx-background-color: #16161D; -fx-background-radius: 14; -fx-padding: 18 26 18 26;"
    maxWidth = Double.MAX_VALUE
}

private fun blob(radius: Double, color: Color, x: Double, y: Double, alpha: Double): Circle = Circle(radius).apply {
    fill = RadialGradient(
        0.0, 0.0, 0.5, 0.5, 1.0, true, CycleMethod.NO_CYCLE,
        Stop(0.0, color.deriveColor(0.0, 1.0, 1.0, alpha)),
        Stop(1.0, Color.TRANSPARENT)
    )
    effect = GaussianBlur(radius * 0.30)
    layoutX = x
    layoutY = y
}
