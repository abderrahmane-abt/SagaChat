package com.dark.tool_neuron.repo

import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.repo.ums.UmsConfigRepository
import com.dark.tool_neuron.repo.ums.UmsModelRepository
import kotlinx.coroutines.flow.Flow

class ModelRepository(
    private val modelRepo: UmsModelRepository,
    private val configRepo: UmsConfigRepository
) {

    fun getAllModels(): Flow<List<Model>> = modelRepo.getAll()

    fun getActiveModels(): Flow<List<Model>> = modelRepo.getAllActive()

    fun getModelsByProvider(providerType: ProviderType): Flow<List<Model>> =
        modelRepo.getByProvider(providerType)

    suspend fun getModelById(id: String): Model? = modelRepo.getById(id)

    suspend fun getModelByName(name: String): Model? = modelRepo.getByName(name)

    suspend fun insertModel(model: Model) = modelRepo.insert(model)

    suspend fun updateModel(model: Model) = modelRepo.update(model)

    suspend fun deleteModel(model: Model) = modelRepo.delete(model)

    suspend fun setModelActive(id: String, isActive: Boolean) =
        modelRepo.updateActiveStatus(id, isActive)

    suspend fun getConfigByModelId(modelId: String): ModelConfig? = configRepo.getByModelId(modelId)

    suspend fun insertConfig(config: ModelConfig) = configRepo.insert(config)

    suspend fun updateConfig(config: ModelConfig) = configRepo.update(config)

    suspend fun deleteConfig(config: ModelConfig) = configRepo.delete(config)
}
