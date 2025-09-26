package com.dark.neuroverse.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.dark.neuroverse.ui.screens.UserDataViewerScreen
import com.dark.neuroverse.ui.theme.NeuroVerseTheme

class UserDataActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            NeuroVerseTheme {
                UserDataViewerScreen()
            }
        }
    }
}

