package com.dark.neuroverse.model



data class Message(
    val role: ROLE,
    var content: String,
    val timeStamp: String,
    val document: DOC? = null
)

data class DOC(
    val path: String,
    val name: String,
    val content: String,
    val type: String
)

enum class ROLE {
    SYSTEM, USER
}