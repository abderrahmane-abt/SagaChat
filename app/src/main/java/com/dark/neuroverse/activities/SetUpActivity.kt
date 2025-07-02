package com.dark.neuroverse.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dark.neuroverse.compose.components.systemui.TopBar
import com.dark.neuroverse.compose.screens.models.ModelsScreen
import com.dark.neuroverse.compose.screens.setup.data.SetupState
import com.dark.neuroverse.compose.screens.setup.terms.TermsAndConditionScreen
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.neuroverse.utils.UserPrefs
import com.dark.neuroverse.worker.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SetUpActivity : ComponentActivity() {
    @OptIn(
        ExperimentalMaterial3Api::class,
        ExperimentalMaterial3ExpressiveApi::class,
        ExperimentalAnimationApi::class
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            enableEdgeToEdge()
            NeuroVerseTheme {
                val context = LocalContext.current
                var setupState by remember { mutableStateOf(SetupState.CHOOSE_MODELS) }
                val scope = rememberCoroutineScope()
                var message by remember { mutableStateOf("") }

                LaunchedEffect(Unit) {

                    if (ModelManager.isAnyModelInstalled()) {
                        val termsAccepted = UserPrefs.isTermsAccepted(context).first()
                        setupState = if (!termsAccepted) SetupState.TERMS else SetupState.COMPLETED
                    }else{
                        setupState = SetupState.CHOOSE_MODELS
                    }

                }

                LaunchedEffect(setupState) {
                    when (setupState) {
                        SetupState.CHOOSE_MODELS -> {
                            message = "Let's Quickly \nChoose A Model"
                        }
                        SetupState.TERMS -> {
                            message = "Let's Go Through \nSome Terms....!"
                        }
                        else -> {}
                    }
                }

                Scaffold { padding ->
                    Column(Modifier.padding(padding)) {
                        TopBar(
                            title = "Quick Setup..!",
                            subtitle = message
                        )

                        AnimatedContent(
                            targetState = setupState,
                            transitionSpec = { fadeIn() togetherWith fadeOut() }
                        ) { state ->
                            when (state) {
                                SetupState.TERMS -> {
                                    TermsAndConditionScreen {
                                        scope.launch(Dispatchers.IO) {
                                            UserPrefs.setTermsAccepted(context, true)
                                            setupState = SetupState.COMPLETED
                                        }
                                    }
                                }

                                SetupState.CHOOSE_MODELS -> {
                                    ModelsScreen{
                                        setupState = SetupState.TERMS
                                    }
                                }
                                SetupState.COMPLETED -> launchMain()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

