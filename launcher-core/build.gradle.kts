plugins {
    `java-library`
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":launcher-common"))

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.25.3")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

/** Sonda del flujo de Microsoft: `gradlew :launcher-core:authProbe` */
tasks.register<JavaExec>("authProbe") {
    group = "verification"
    description = "Pide un device code a Microsoft para validar el client_id"
    mainClass.set("com.neonlauncher.core.auth.AuthProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

/** Sonda del descargador de versiones: `gradlew :launcher-core:downloadProbe` */
tasks.register<JavaExec>("downloadProbe") {
    group = "verification"
    description = "Lee el catálogo de Mojang y reinstala una versión existente"
    mainClass.set("com.neonlauncher.core.download.DownloadProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

/** Sonda del auto-update: `gradlew :launcher-core:updateProbe` */
tasks.register<JavaExec>("updateProbe") {
    group = "verification"
    description = "Consulta GitHub Releases como lo hace el launcher"
    mainClass.set("com.neonlauncher.core.update.UpdateProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}
