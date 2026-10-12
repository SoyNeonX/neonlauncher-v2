plugins {
    id("application")
    kotlin("jvm")
}

dependencies {
    implementation(project(":launcher-core"))

    implementation("org.openjfx:javafx-controls:21.0.6")
    implementation("org.openjfx:javafx-fxml:21.0.6")
    implementation("org.openjfx:javafx-web:21.0.6")
    implementation("org.openjfx:javafx-graphics:21.0.6")

    implementation("org.kordamp.ikonli:ikonli-javafx:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-materialdesign2:12.3.1")
    implementation("com.dlsc.formsfx:formsfx-core:11.2.1")

    implementation("com.google.guava:guava:33.0.0-jre")
}

application {
    mainClass.set("com.neonlauncher.LauncherApp")
}

tasks.named<JavaExec>("run") {
    jvmArgs(
        "--add-exports=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
        "--add-exports=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED",
        "--add-opens=javafx.graphics/com.sun.javafx.stage=ALL-UNNAMED"
    )
}