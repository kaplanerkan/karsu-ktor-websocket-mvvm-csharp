package com.panda.ktorwebsocketmvvm.di

import com.panda.ktorwebsocketmvvm.data.remote.WebSocketDataSource
import com.panda.ktorwebsocketmvvm.data.repository.ChatRepository
import com.panda.ktorwebsocketmvvm.ui.chat.ChatViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    singleOf(::WebSocketDataSource)
    singleOf(::ChatRepository)
    viewModelOf(::ChatViewModel)
}
