
val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

/* plugins {
    kotlin("jvm") version "1.9.22"
    id("io.ktor.plugin") version "2.3.7"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
} */

group = "com.example"
version = "0.0.1"

plugins {
    application
    id("io.ktor.plugin") version "2.3.7"
}

apply(plugin = "kotlin")

application {
    mainClass.set("com.example.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    add("implementation", project(":shared"))
    add("implementation", "io.ktor:ktor-server-core-jvm")
    add("implementation", "io.ktor:ktor-server-content-negotiation-jvm")
    add("implementation", "io.ktor:ktor-serialization-kotlinx-json-jvm")
    add("implementation", "io.ktor:ktor-server-websockets-jvm")
    add("implementation", "io.ktor:ktor-server-netty-jvm")
    add("implementation", "ch.qos.logback:logback-classic:$logback_version")
}

tasks {
    create("stage").dependsOn("installDist")
}
