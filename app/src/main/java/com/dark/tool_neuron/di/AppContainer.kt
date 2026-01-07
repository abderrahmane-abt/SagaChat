package com.dark.tool_neuron.di

import android.content.Context
import com.dark.tool_neuron.database.AppDatabase
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.viewmodel.factory.LLMModelViewModelFactory

object AppContainer {

    private lateinit var database: AppDatabase
    private lateinit var modelRepository: ModelRepository
    private lateinit var llmModelViewModelFactory: LLMModelViewModelFactory

    fun init(context: Context) {
        database = AppDatabase.getDatabase(context)

        modelRepository = ModelRepository(
            modelDao = database.modelDao(),
            configDao = database.modelConfigDao()
        )

        llmModelViewModelFactory =
            LLMModelViewModelFactory(modelRepository)
    }

    fun getDatabase(): AppDatabase = database

    fun getModelRepository(): ModelRepository = modelRepository

    fun getLLMModelViewModelFactory(): LLMModelViewModelFactory =
        llmModelViewModelFactory
}
