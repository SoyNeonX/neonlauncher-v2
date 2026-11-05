package com.neonlauncher.common

import com.neonlauncher.common.model.LauncherSettings
import com.neonlauncher.common.model.PlayerProfile
import com.neonlauncher.common.util.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class LauncherCommonTest {

    @Test
    fun `valida nombres de jugador como Minecraft`() {
        assertThat(PlayerProfile.isValidName("Steve")).isTrue()
        assertThat(PlayerProfile.isValidName("Steve_01")).isTrue()
        assertThat(PlayerProfile.isValidName("ab")).isFalse()
        assertThat(PlayerProfile.isValidName("steve!")).isFalse()
    }

    @Test
    fun `el uuid offline no depende del orden de llamadas`() {
        val a = PlayerProfile.offline("Alex")
        val b = PlayerProfile.offline("Steve")
        assertThat(a.uuid).isNotEqualTo(b.uuid)
        assertThat(a.uuid.version()).isEqualTo(3)
    }

    @Test
    fun `los settings sobreviven un ida y vuelta a json`(@TempDir dir: Path) {
        val file = dir.resolve("settings.json")
        val original = LauncherSettings(username = "Neon", memoryMb = 6144)
        Json.write(file, original)

        val loaded = Json.read(file, LauncherSettings::class.java)
        assertThat(loaded).isEqualTo(original)
    }

    @Test
    fun `un settings incompleto usa los defaults`(@TempDir dir: Path) {
        val file = dir.resolve("settings.json")
        java.nio.file.Files.writeString(file, """{"username":"Tester"}""")

        val loaded = Json.read(file, LauncherSettings::class.java)
        assertThat(loaded.username).isEqualTo("Tester")
        assertThat(loaded.memoryMb).isEqualTo(3072)
    }
}
