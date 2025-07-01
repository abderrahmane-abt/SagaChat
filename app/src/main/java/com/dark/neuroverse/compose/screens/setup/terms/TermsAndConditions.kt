package com.dark.neuroverse.compose.screens.setup.terms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.compose.components.RichText
import com.dark.neuroverse.data.other.fullTermsText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsAndConditionScreen(onAgree: () -> Unit = {}) {

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {

        RichText(
            style = MaterialTheme.typography.bodyLarge,
            text = fullTermsText()
        )

        Button(
            onClick = onAgree,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I Agree")
        }
    }
}
