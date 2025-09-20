package com.mp.data_hub_lib.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mp.data_hub_lib.model.DataHubModel
import kotlinx.coroutines.flow.Flow

@Dao
interface DataHubDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: DataHubModel)

    @Update
    suspend fun updateModel(model: DataHubModel)

    @Delete
    suspend fun deleteModel(model: DataHubModel)

    @Query("SELECT * FROM data_hub_models WHERE modelName = :modelName LIMIT 1")
    suspend fun getModelByName(modelName: String): DataHubModel?

    @Query("SELECT * FROM data_hub_models")
    fun getAllModels(): Flow<List<DataHubModel>>

    @Query("SELECT * FROM data_hub_models WHERE modelName LIKE '%' || :query || '%' OR modelDescription LIKE '%' || :query || '%'")
    fun searchModels(query: String): Flow<List<DataHubModel>>
}
