package com.panda.ktorwebsocketmvvm.di

import com.panda.ktorwebsocketmvvm.data.audio.AudioPlayer
import com.panda.ktorwebsocketmvvm.data.audio.AudioRecorder
import com.panda.ktorwebsocketmvvm.data.remote.WebSocketDataSource
import com.panda.ktorwebsocketmvvm.data.repository.ChatRepository
import com.panda.ktorwebsocketmvvm.ui.chat.ChatViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    singleOf(::WebSocketDataSource)
    singleOf(::ChatRepository)
    single { AudioRecorder(androidContext()) }
    single { AudioPlayer(androidContext()) }
    viewModelOf(::ChatViewModel)
}
