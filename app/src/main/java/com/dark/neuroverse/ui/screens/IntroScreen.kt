package com.dark.neuroverse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.dark.neuroverse.ui.components.IntroComposable

@Composable
fun IntroScreen() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        IntroComposable()
        Text(
            "Welcome to NeuroV..!",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif)
        )
    }
}