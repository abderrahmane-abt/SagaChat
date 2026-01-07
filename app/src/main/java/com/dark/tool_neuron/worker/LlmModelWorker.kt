package com.dark.tool_neuron.worker

import com.dark.tool_neuron.engine.GGUFEngine
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig

object LlmModelWorker {

    val ggufEngine: GGUFEngine = GGUFEngine()


    suspend fun loadGgufModel(model: Model, modelConfig: ModelConfig){
        ggufEngine.load(model, modelConfig)
    }

}