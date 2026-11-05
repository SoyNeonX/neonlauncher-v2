package com.neonlauncher.core

import com.neonlauncher.common.model.PlayerProfile
import com.neonlauncher.common.util.OsFamily
import com.neonlauncher.core.game.OsRule
import com.neonlauncher.core.game.Rule
import com.neonlauncher.core.game.Rules
import com.neonlauncher.core.game.VersionStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class LauncherCoreTest {

    @Test
    fun `interpreta el releaseTime de Mojang`() {
        assertThat(VersionStore.parseInstant("2025-12-09T12:23:30+00:00"))
            .isEqualTo(Instant.parse("2025-12-09T12:23:30Z"))
    }

    @Test
    fun `interpreta el releaseTime de Fabric sin dos puntos en el offset`() {
        assertThat(VersionStore.parseInstant("2026-03-08T10:09:55+0000"))
            .isEqualTo(Instant.parse("2026-03-08T10:09:55Z"))
    }

    @Test
    fun `un releaseTime invalido no rompe`() {
        assertThat(VersionStore.parseInstant("vaya uno a saber")).isNull()
        assertThat(VersionStore.parseInstant(null)).isNull()
    }

    @Test
    fun `una libreria sin rules se permite`() {
        assertThat(Rules.allows(emptyList(), emptyMap())).isTrue()
    }

    @Test
    fun `una rule de otro sistema operativo se rechaza`() {
        val rules = listOf(Rule(action = "allow", os = OsRule(name = "plan9")))
        assertThat(Rules.allows(rules, emptyMap())).isFalse()
    }

    @Test
    fun `una rule del sistema actual se permite`() {
        val rules = listOf(Rule(action = "allow", os = OsRule(name = OsFamily.current.id)))
        assertThat(Rules.allows(rules, emptyMap())).isTrue()
    }

    @Test
    fun `una rule que depende de un feature apagado no aplica`() {
        val rules = listOf(Rule(action = "allow", features = mapOf("is_demo_user" to true)))
        assertThat(Rules.allows(rules, emptyMap())).isFalse()
        assertThat(Rules.allows(rules, mapOf("is_demo_user" to true))).isTrue()
    }

    @Test
    fun `la rule de arquitectura x86 no aplica en 64 bits`() {
        val rules = listOf(Rule(action = "allow", os = OsRule(arch = "x86")))
        assertThat(Rules.allows(rules, emptyMap())).isFalse()
    }

    @Test
    fun `el uuid offline es estable y de version 3`() {
        val first = PlayerProfile.offline("Steve")
        assertThat(first.uuid).isEqualTo(PlayerProfile.offline("Steve").uuid)
        assertThat(first.uuid.version()).isEqualTo(3)
    }
}
