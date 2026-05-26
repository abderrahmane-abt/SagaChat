package com.moorixlabs.sagachat.model

data class Character(
    val id: String,
    val name: String,
    val chatName: String,
    val bio: String,
    val personality: String,
    val scenario: String,
    val initialMessage: String,
    val exampleDialogs: String,
    val tags: List<String> = emptyList(),
    val avatarUri: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val linkedChatId: String = "",
)
