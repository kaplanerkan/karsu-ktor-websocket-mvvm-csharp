package com.panda.ktorwebsocketmvvm

import android.app.Application
import android.util.Log
import com.karsu.ServerStarter
import com.panda.ktorwebsocketmvvm.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class ChatApplication : Application() {

    companion object {
        private const val TAG = "KtorServer"
        const val SERVER_PORT = 8080
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initKoin()
        startKtorServer()
    }

    private fun initKoin() {
        startKoin {
            androidLogger()
            androidContext(this@ChatApplication)
            modules(appModule)
        }
    }

    private fun startKtorServer() {
        applicationScope.launch {
            try {
                Log.i(TAG, "Starting Ktor server on port $SERVER_PORT...")
                ServerStarter.start(port = SERVER_PORT)
            } catch (e: Exception) {
                Log.e(TAG, "Server failed: ${e.message}", e)
            }
        }
    }
}
