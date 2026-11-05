plugins {
    application
    kotlin("jvm")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

kotlin {
    jvmToolchain(21)
}

javafx {
    version = "21.0.6"
    modules = listOf("javafx.controls")
}

dependencies {
    implementation(project(":launcher-core"))
}

application {
    mainClass.set("com.neonlauncher.ui.MainKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

/** Laboratorio de diseño: renderiza los mockups a docs/mockup-*.png */
tasks.register<JavaExec>("mockups") {
    group = "design"
    description = "Renderiza los mockups de diseño a docs/mockup-*.png"
    mainClass.set("com.neonlauncher.ui.design.MockupsKt")
    classpath = sourceSets["main"].runtimeClasspath
    args(rootProject.projectDir.resolve("docs").absolutePath)
}
