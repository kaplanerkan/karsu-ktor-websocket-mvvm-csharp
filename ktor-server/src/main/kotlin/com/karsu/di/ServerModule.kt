package com.karsu.di

import com.karsu.session.ConnectionManager
import org.koin.dsl.module

val serverModule = module {
    single { ConnectionManager() }
}
