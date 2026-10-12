plugins {
    kotlin("jvm") version "1.9.24" apply false
}

group = "com.neonlauncher"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "kotlin")

    group = "com.neonlauncher"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.11.0"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.assertj:assertj-core:3.25.3")
    }

    tasks {
        withType<Test> {
            useJUnitPlatform()
        }
        withType<JavaCompile> {
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation", "-parameters"))
        }
    }
}