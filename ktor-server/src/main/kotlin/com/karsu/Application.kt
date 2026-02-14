package com.karsu

import com.karsu.di.serverModule
import com.karsu.plugins.configureRouting
import com.karsu.plugins.configureSerialization
import com.karsu.plugins.configureSockets
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.koin.ktor.plugin.Koin

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(Koin) {
        modules(serverModule)
    }
    configureSerialization()
    configureSockets()
    configureRouting()
}
