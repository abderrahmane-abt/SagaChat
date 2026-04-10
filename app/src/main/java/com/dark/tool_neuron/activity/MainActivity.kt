package com.dark.tool_neuron.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.ui.screens.system_ui.AppScaffold
import com.dark.tool_neuron.ui.theme.ToolNeuronTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InferenceClient.bind(applicationContext)
        enableEdgeToEdge()
        setContent {
            ToolNeuronTheme {
                AppScaffold()
            }
        }
    }

    override fun onDestroy() {
        InferenceClient.unbind()
        super.onDestroy()
    }
}
