package com.controlgasto.app.domain.model

import java.util.UUID

data class Category(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String,
    val color: Long,
    val isDefault: Boolean = false
)
