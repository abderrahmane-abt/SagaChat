package com.dark.neuroverse.compose.screens.models

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.dark.neuroverse.compose.components.CollapsableButton
import com.dark.neuroverse.compose.components.systemui.StandardBottomBar
import com.dark.neuroverse.data.model.ModelsData
import com.dark.neuroverse.data.repo.ModelsList.modelList

@Composable
fun ModelsScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn {
            items(modelList) {
                ModelCard(it)
            }
        }

        StandardBottomBar(Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 14.dp)) {
            CollapsableButton(text = "Finish", icon = Icons.AutoMirrored.Default.ArrowForward) {

            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModelCard(modelsData: ModelsData) {

    var isDownloading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                modelsData.modeName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                modelsData.modelDescription,
                style = MaterialTheme.typography.bodyLarge
            )

            AnimatedVisibility(isDownloading) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = { 0.7f }, )
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    isDownloading = !isDownloading
                }) {
                    Crossfade(isDownloading) {
                        when (it) {
                            true -> {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "",
                                )
                            }

                            false -> {
                                Icon(Icons.Default.ArrowCircleDown, contentDescription = "")
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, modelsData.modelPageLink.toUri())
                    context.startActivity(intent)
                }) {
                    Icon(Icons.AutoMirrored.Default.OpenInNew, contentDescription = "")
                }
            }
        }
    }
}