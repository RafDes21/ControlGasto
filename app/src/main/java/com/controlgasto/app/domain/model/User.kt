package com.controlgasto.app.domain.model

data class User(
    val uid: String,
    val email: String,
    val displayName: String = "",
    val photoUrl: String = "",
    val isPro: Boolean = false
)
