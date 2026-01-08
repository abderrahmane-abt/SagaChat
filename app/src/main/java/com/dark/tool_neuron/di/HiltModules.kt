package com.dark.tool_neuron.di

import android.content.Context
import com.dark.tool_neuron.database.AppDatabase
import com.dark.tool_neuron.repo.ChatRepository
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.worker.ChatManager
import com.dark.tool_neuron.worker.GenerationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideModelRepository(database: AppDatabase): ModelRepository {
        return ModelRepository(
            modelDao = database.modelDao(),
            configDao = database.modelConfigDao()
        )
    }

    @Provides
    @Singleton
    fun provideChatRepository(): ChatRepository {
        return ChatRepository()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {

    @Provides
    @Singleton
    fun provideChatManager(): ChatManager {
        return ChatManager()
    }

    @Provides
    @Singleton
    fun provideGenerationManager(): GenerationManager {
        return GenerationManager()
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
}