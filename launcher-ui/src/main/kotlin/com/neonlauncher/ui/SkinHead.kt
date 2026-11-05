package com.neonlauncher.ui

import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Cabeza del jugador. Intenta la skin real por nombre (api pública) y si no existe dibuja una
 * cara pixelada estilo Steve, así el perfil nunca queda vacío.
 */
internal object SkinHead {

    private val cache = ConcurrentHashMap<String, Image>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun node(name: String, size: Double): Node {
        val frame = StackPane().apply {
            styleClass.add("skin-frame")
            prefWidth = size
            prefHeight = size
            minWidth = size
            minHeight = size
            maxWidth = size
            maxHeight = size
            clip = Rectangle(size, size).apply {
                arcWidth = size
                arcHeight = size
            }
        }

        frame.children.add(pixelFace(size))

        cache[name]?.let { image ->
            frame.children.setAll(imageView(image, size))
            return frame
        }

        if (name in failed) return frame

        load(name) { image ->
            if (image != null) frame.children.setAll(imageView(image, size))
        }
        return frame
    }

    private fun imageView(image: Image, size: Double): Node = ImageView(image).apply {
        fitWidth = size
        fitHeight = size
        isPreserveRatio = true
        isSmooth = false
    }

    private fun load(name: String, onReady: (Image?) -> Unit) {
        runCatching {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://mc-heads.net/avatar/${name.lowercase()}/128"))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build()

            CompletableFuture.supplyAsync {
                runCatching {
                    val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
                    if (response.statusCode() == 200) {
                        Image(response.body().inputStream())
                    } else {
                        null
                    }
                }.getOrNull()
            }.thenAccept { image ->
                if (image == null || image.isError) failed.add(name) else cache[name] = image
                Platform.runLater { onReady(image) }
            }
        }.onFailure { failed.add(name) }
    }

    /** Cara 8x8 estilo Steve: sirve de fallback y da el aire retro. */
    private fun pixelFace(size: Double): Node {
        val rows = listOf(
            "HHHHHHHH",
            "HHHHHHHH",
            "HSSSSSSH",
            "SSSSSSSS",
            "SSPSSPSS",
            "SSSSSSSS",
            "SSMMMMSS",
            "SSSSSSSS"
        )
        val colors = mapOf(
            'H' to "#3B2A1A",
            'S' to "#C68C5A",
            'P' to "#4A3B6B",
            'M' to "#8B5A3C"
        )
        val cell = size / 8.0

        return Pane().apply {
            prefWidth = size
            prefHeight = size
            minWidth = size
            minHeight = size
            maxWidth = size
            maxHeight = size
            for (row in rows.indices) {
                for (col in rows[row].indices) {
                    val color = colors[rows[row][col]] ?: "#C68C5A"
                    children.add(
                        Rectangle(cell, cell).apply {
                            fill = Color.web(color)
                            x = col * cell
                            y = row * cell
                        }
                    )
                }
            }
        }
    }
}
