package com.karsu

import com.karsu.plugins.configureRouting
import com.karsu.plugins.configureSerialization
import com.karsu.plugins.configureSockets
import io.ktor.server.engine.*
import io.ktor.server.netty.*

/**
 * Embedded server starter used by the Android app.
 * Does NOT install Koin — relies on the global Koin context
 * already initialized by ChatApplication to avoid ClosedScopeException.
 */
object ServerStarter {

    @Volatile
    var isRunning = false
        private set

    fun start(port: Int = 8080, host: String = "0.0.0.0") {
        isRunning = true
        embeddedServer(Netty, port = port, host = host) {
            configureSerialization()
            configureSockets()
            configureRouting()
        }.start(wait = true)
    }
}
