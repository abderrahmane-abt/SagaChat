package com.moorixlabs.sagachat.plugin

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PluginModule {

    @Provides
    @Singleton
    fun provideRegistry(): PluginRegistry = PluginRegistry(
        plugins = emptyList(),
    )
}
