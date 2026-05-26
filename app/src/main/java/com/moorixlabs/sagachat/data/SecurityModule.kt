package com.moorixlabs.sagachat.data

import com.moorixlabs.hxs_encryptor.HxsEncryptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideHxsEncryptor(): HxsEncryptor = HxsEncryptor()
}
