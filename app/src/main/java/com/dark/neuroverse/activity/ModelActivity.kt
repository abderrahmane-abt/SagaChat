package com.dark.neuroverse.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.dark.neuroverse.ui.screens.ModelsScreen
import com.dark.neuroverse.ui.theme.NeuroVerseTheme

class ModelActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NeuroVerseTheme {
                Scaffold(topBar = {

                }) {
                    Column(Modifier.padding(it)) {
                        ModelsScreen()
                    }
                }
            }
        }
    }

}