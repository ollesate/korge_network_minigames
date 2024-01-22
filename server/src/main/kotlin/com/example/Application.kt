package com.example

import com.example.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

val port = runCatching { System.getenv("PORT").toIntOrNull() }.getOrNull() ?: 8080

fun main() {
    embeddedServer(
        Netty,
        port = port,
        host = "0.0.0.0",
        module = Application::module
    )
        .start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureSockets()
    configureRouting()
}
