package com.dark.tool_neuron.ui.screen.home_screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

@Composable
fun HomeScreen() {
    Scaffold(
        topBar = { HomeScreenTopBar() }, modifier = Modifier
            .fillMaxSize()
    ) {
        Column(Modifier.padding(it)) {

        }
    }
}