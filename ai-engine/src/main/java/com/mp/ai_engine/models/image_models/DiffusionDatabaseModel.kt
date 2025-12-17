package com.mp.ai_engine.models.image_models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "diffusion_db")
data class DiffusionDatabaseModel(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val modelFolder: String,
    val generationSize: Int = 512,
    val textEmbeddingSize: Int = 768,
    val defaultPrompt: String = "",
    val defaultNegativePrompt: String = "",
    val useCpuClip: Boolean = false,

    //GenerationParameters
    val steps: Int = 20,
    val cfg: Float = 7f,
    val seed: Long = -1,
    val prompt: String = "",
    val negativePrompt: String = "",
    val width: Int = 512,
    val height: Int = 512,
    val runOnCpu: Boolean = true,
    val denoiseStrength: Float = 0.6f,
    val useOpenCL: Boolean = false,
    val scheduler: String = "dpm"
)