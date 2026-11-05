package com.neonlauncher.common.model

import java.util.UUID

/** Identidad de juego. En modo offline el UUID se deriva del nombre, igual que hace Minecraft. */
data class PlayerProfile(val name: String, val uuid: UUID) {

    companion object {
        private val VALID_NAME = Regex("^[A-Za-z0-9_]{3,16}$")

        fun isValidName(name: String): Boolean = VALID_NAME.matches(name)

        /** UUID v3 de `OfflinePlayer:<nombre>`: el mismo que usa el juego en modo offline. */
        fun offline(name: String): PlayerProfile =
            PlayerProfile(name, UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8)))
    }
}
