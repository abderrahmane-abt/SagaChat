package com.dark.tool_neuron.di

import android.content.Context
import com.dark.tool_neuron.database.AppDatabase
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.viewmodel.ThemeViewModel

object AppContainer {

    private lateinit var themeViewModel: ThemeViewModel
    private lateinit var database: AppDatabase
    private lateinit var modelRepository: ModelRepository

    fun init(context: Context){
        themeViewModel = ThemeViewModel()
        database = AppDatabase.getDatabase(context)
        modelRepository = ModelRepository(
            modelDao = database.modelDao(),
            configDao = database.modelConfigDao()
        )
    }

    fun getThemeViewModel() = themeViewModel

    fun getDatabase() = database

    fun getModelRepository() = modelRepository
}