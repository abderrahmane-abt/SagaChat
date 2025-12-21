package com.dark.tool_neuron.viewModel.home_screen

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.model.LocalModel
import com.dark.tool_neuron.model.Message
import com.dark.tool_neuron.new_workers.ChatWorker
import com.dark.tool_neuron.worker.UIStateManager
import com.mp.ai_engine.diffusion.IDiffusionCallback
import com.mp.ai_engine.gguf.IGGUFCallback
import com.mp.ai_engine.models.llm_models.ModelSearchResult
import com.mp.ai_engine.models.llm_models.ModelType
import com.mp.ai_engine.workers.installer.ModelInstaller
import com.mp.ai_engine.workers.model.ModelManager
import com.mp.user_data.models.ChatMessage
import com.mp.user_data.models.ChatMessageContent
import com.mp.user_data.models.TaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.lang.StringBuilder
import java.util.UUID

class HomeScreenViewModel : ViewModel() {

    companion object {
        private const val TAG = "HomeScreenViewModel"
    }

    private val _selectedModel = MutableStateFlow(LocalModel("", "", ModelType.NONE))
    val selectedModel: StateFlow<LocalModel> = _selectedModel.asStateFlow()

    private val _installedModels = MutableStateFlow<List<LocalModel>>(emptyList())
    val installedModels: StateFlow<List<LocalModel>> = _installedModels.asStateFlow()

    private val _isDialogSelected = MutableStateFlow(false)
    val isDialogSelected: StateFlow<Boolean> = _isDialogSelected.asStateFlow()

    var loadedChats = ChatWorker.chatList
    var loadedMessages = ChatWorker.loadedMessages


    init {
        viewModelScope.launch(Dispatchers.IO) {
            observeModels()
            observeChatList()
        }
    }

    suspend fun observeModels() {
        val ggufModel = ModelInstaller.getInstalledGGUFModels()
        val diffusionModels = ModelInstaller.getInstalledDiffusionModels()

        ggufModel.forEach {
            _installedModels.value += LocalModel(it.id, it.modelName, ModelType.TEXT)
        }
        diffusionModels.forEach {
            _installedModels.value += LocalModel(it.id, it.name, ModelType.IMAGE_GEN)
        }
    }

    fun observeChatList(){
        viewModelScope.launch(Dispatchers.IO) {
            ChatWorker.loadChats()
        }
    }

    fun loadModel(modelID: String) {
        if (UIStateManager.isGenerating()) {
            Log.w(TAG, "Cannot change model during generation")
            return
        }

        // Toggle off if same model selected
        if (selectedModel.value.modelId == modelID) {
            Log.d(TAG, "Unselecting model")
            when(_selectedModel.value.modelType){
                ModelType.TEXT -> ModelManager.gguf().unloadModel()
                else -> ModelManager.diffusion().unloadModel()
            }
            _selectedModel.value = LocalModel()
            return
        }
        UIStateManager.toggleStateModelLoading(true)

        viewModelScope.launch(Dispatchers.IO) {

            when(_selectedModel.value.modelType){
                ModelType.TEXT -> ModelManager.gguf().unloadModel()
                else -> ModelManager.diffusion().unloadModel()
            }

            val decodedModel = ModelInstaller.findModel(modelID) ?: return@launch
            decodedModel.ggufModel.let {
                if (it == null) return@let
                val result = ModelManager.gguf().loadTextModel(it.toJson())
                if (result) {
                    _selectedModel.value = LocalModel(it.id, it.modelName, ModelType.TEXT)
                }
            }
            decodedModel.diffusionModel.let {
                if (it == null) return@let
                val result = ModelManager.diffusion().loadModel(it.toJson())
                if (result) {
                    _selectedModel.value = LocalModel(it.id, it.name, ModelType.IMAGE_GEN)
                }
            }
            UIStateManager.toggleStateModelLoading(false)
        }
    }
    fun setIsDialogOpen(boolean: Boolean){
        _isDialogSelected.value = boolean
    }

    fun loadChat(chatID: String) {
        ChatWorker.loadChat(chatID)
    }

    fun sendMessage(taskType: TaskType, message: ChatMessage, context: Context){
        //Add User Message
        ChatWorker.newMessage(message)
        viewModelScope.launch(Dispatchers.IO) {
            val model = ModelInstaller.findModel(_selectedModel.value.modelId) ?: return@launch
            //Get Model Type
            when(taskType){
                TaskType.TEXT -> {
                    val tok = StringBuilder("")
                    ModelManager.gguf().generateText(
                        message.input,
                        model.ggufModel?.maxTokens ?: 100,
                        "",
                        object : IGGUFCallback.Stub() {
                            override fun onNewToken(token: String) {
                                tok.append(token)
                                ChatWorker.updateMessage(
                                    message.copy(
                                        chatMessageContent = ChatMessageContent.TextMessage(
                                            tok.toString()
                                        )
                                    )
                                )
                            }

                            override fun onToolCall(
                                toolName: String?,
                                toolArgs: String?
                            ) {
                                TODO("Not yet implemented")
                            }

                            override fun onComplete(finalResult: String) {
                                tok.clear()
                                ChatWorker.updateMessage(
                                    message.copy(
                                        chatMessageContent = ChatMessageContent.TextMessage(
                                            finalResult
                                        )
                                    )
                                )
                            }

                            override fun onError(error: String) {
                                tok.clear()
                                ChatWorker.updateMessage(
                                    message.copy(
                                        chatMessageContent = ChatMessageContent.TextMessage(
                                            error
                                        )
                                    )
                                )
                            }

                        }
                    )
                }
                TaskType.IMAGE -> {
                    val diffusionModel = model.diffusionModel ?: return@launch
                    val imageID = UUID.randomUUID().toString()
                    ModelManager.diffusion().generateImage(
                        message.input,
                        diffusionModel.negativePrompt,
                        diffusionModel.steps,
                        diffusionModel.cfg,
                        diffusionModel.width,
                        diffusionModel.height,
                        diffusionModel.denoiseStrength,
                        diffusionModel.useOpenCL,
                        diffusionModel.scheduler,
                        diffusionModel.seed,
                        object : IDiffusionCallback.Stub() {
                            override fun onProgress(
                                progress: Float,
                                step: Int,
                                totalSteps: Int
                            ) {
                                TODO("Not yet implemented")
                            }

                            override fun onPreview(
                                previewImage: Bitmap,
                                step: Int,
                                totalSteps: Int
                            ) {
                                val imageFile = imageSaver(previewImage, imageID, context)
                                ChatWorker.updateMessage(
                                    message.copy(
                                        chatMessageContent = ChatMessageContent.ImageMessage(
                                            imageFile,
                                            step,
                                            totalSteps
                                        )
                                    )
                                )
                            }

                            override fun onComplete(
                                finalImage: Bitmap,
                                seed: Long
                            ) {
                                val imageFile = imageSaver(finalImage, imageID, context)
                                ChatWorker.updateMessage(
                                    message.copy(
                                        chatMessageContent = ChatMessageContent.ImageMessage(
                                            imageFile,
                                            0,
                                            0
                                        )
                                    )
                                )
                            }

                            override fun onError(error: String?) {
                                TODO("Not yet implemented")
                            }
                        }
                    )
                }
                else -> {}
            }
        }
    }

    fun imageSaver(bitmap: Bitmap, imageID: String, context: Context): String {
        val imagesDir = File(context.filesDir, "generated-images")
        if (!imagesDir.exists()) imagesDir.mkdirs()

        val file = File(imagesDir, "img_$imageID.png")

        if (file.exists()) file.delete()

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }

        return file.absolutePath
    }

    fun deleteMessage(id: String) {
        ChatWorker.deleteMessage(id)
    }

}