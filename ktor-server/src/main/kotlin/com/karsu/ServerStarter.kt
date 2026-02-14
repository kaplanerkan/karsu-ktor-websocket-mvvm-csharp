package com.karsu

import com.karsu.di.serverModule
import com.karsu.plugins.configureRouting
import com.karsu.plugins.configureSerialization
import com.karsu.plugins.configureSockets
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.koin.ktor.plugin.Koin

object ServerStarter {

    @Volatile
    var isRunning = false
        private set

    fun start(port: Int = 8080, host: String = "0.0.0.0") {
        isRunning = true
        embeddedServer(Netty, port = port, host = host) {
            install(Koin) {
                modules(serverModule)
            }
            configureSerialization()
            configureSockets()
            configureRouting()
        }.start(wait = true)
    }
}
