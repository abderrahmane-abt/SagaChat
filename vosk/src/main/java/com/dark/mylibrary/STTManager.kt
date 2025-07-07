package com.dark.mylibrary

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

class STTManager(private val activity: Context) : RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null


    private val _speechResults = MutableStateFlow("")
    val speechResults: StateFlow<String> get() = _speechResults

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> get() = _isListening

    fun isModelReady() = model != null


    init {
        checkPermissionAndInit()
    }

    private fun checkPermissionAndInit() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            initModel()
        }
    }

    fun initModel() {
        StorageService.unpack(activity, "vosk-model-small-en-in-0.4", "model",
            { unpackedModel ->
                model = unpackedModel
            },
            { exception ->
                _speechResults.value = "Model Load Error: ${exception?.message}"
            }
        )
    }

    fun startListening() {
        model?.let { model ->
            try {
                val recognizer = Recognizer(model, 16000.0f)
                speechService = SpeechService(recognizer, 16000.0f)
                speechService?.startListening(this)
                _isListening.value = true
            } catch (e: Exception) {
                _speechResults.value = "Recognizer Error: ${e.message}"
            }
        } ?: run {
            _speechResults.value = "Model not initialized!"
        }
    }


    fun stop() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        _isListening.value = false
    }

    override fun onResult(hypothesis: String?) {
        hypothesis?.let {
            _speechResults.value = it
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        hypothesis?.let {
            _speechResults.value = it
        }
        stop()
    }

    override fun onPartialResult(hypothesis: String?) {
        hypothesis?.let {
            _speechResults.value = it
        }
    }

    override fun onError(e: Exception?) {
        _speechResults.value = "Error: ${e?.message}"
        stop()
    }

    override fun onTimeout() {
        _speechResults.value = "Listening timed out"
        stop()
    }
}
