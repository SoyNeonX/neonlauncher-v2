plugins {
    id("java-library")
    kotlin("jvm")
}

dependencies {
    implementation(project(":launcher-common"))

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")

    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
}