package com.moorixlabs.sagachat.viewmodel

import androidx.lifecycle.ViewModel
import com.moorixlabs.sagachat.data.ThemeController
import com.moorixlabs.sagachat.ui.theme.ColorPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SetupThemeViewModel @Inject constructor(
    private val themeController: ThemeController,
) : ViewModel() {

    val mode: StateFlow<ThemeController.Mode> = themeController.mode
    val palette: StateFlow<ColorPalette> = themeController.palette

    fun selectMode(value: ThemeController.Mode) = themeController.setMode(value)

    fun selectPalette(value: ColorPalette) = themeController.setPalette(value)
}
