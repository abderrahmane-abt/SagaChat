package com.dark.tool_neuron.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dark.tool_neuron.models.table_schema.ModelConfig

@Dao
interface ModelConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: ModelConfig)

    @Update
    suspend fun update(config: ModelConfig)

    @Delete
    suspend fun delete(config: ModelConfig)

    @Query("SELECT * FROM model_config WHERE model_id = :modelId")
    suspend fun getByModelId(modelId: String): ModelConfig?

    @Query("SELECT * FROM model_config WHERE id = :id")
    suspend fun getById(id: String): ModelConfig?
}