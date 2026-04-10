package com.dark.tool_neuron.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.SecurityManager
import com.dark.tool_neuron.model.NavScreens
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ScaffoldViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val security: SecurityManager
) : ViewModel() {

    fun resolveStartDestination(): String {
        val onboarded = prefs.onboardingComplete
        val mode = prefs.securityMode
        Log.d("ScaffoldVM", "resolveStart: onboarded=$onboarded, securityMode=$mode")
        if (!onboarded) return NavScreens.DevNotes.route
        if (security.isAppPassword) return NavScreens.PasswordScreen.route
        return NavScreens.HomeScreen.route
    }

    fun markOnboardingComplete() {
        prefs.onboardingComplete = true
    }
}
