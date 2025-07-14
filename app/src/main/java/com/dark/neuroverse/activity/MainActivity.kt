package com.dark.neuroverse.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dark.neuroverse.model.HomeUiState
import com.dark.neuroverse.ui.screens.HomeScreen
import com.dark.neuroverse.ui.screens.IntroScreen
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var currentScreen by remember { mutableStateOf(HomeUiState.INTRO) }

            LaunchedEffect(Unit) {
                delay(4000)
                currentScreen = HomeUiState.MAIN
            }

            NeuroVerseTheme {
                Scaffold {
                    Crossfade(
                        modifier = Modifier.padding(it), targetState = currentScreen
                    ) { screen ->
                        when (screen) {
                            HomeUiState.INTRO -> IntroScreen()
                            HomeUiState.MAIN -> HomeScreen()
                        }
                    }

                }
            }
        }
    }
}


