package com.neonlauncher.core.update

/** Sonda del auto-update: `gradlew :launcher-core:updateProbe` */
fun main() {
    println("repo: ${UpdateChecker.REPOSITORY}")
    val result = runCatching { UpdateChecker.check("2.1.1") }
    println("check(2.1.1) -> $result")
}
