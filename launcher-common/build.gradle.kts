plugins {
    id("java-library")
    kotlin("jvm")
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-annotations:2.17.1")
    api("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")

    implementation("org.slf4j:slf4j-api:2.0.13")
}