package com.moorixlabs.sagachat.service.island

data class IslandPosition(
    val offsetYDp: Float = 0f,
)

enum class IslandMode { ASSISTANT, CONTROL }
