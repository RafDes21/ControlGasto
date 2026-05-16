package com.controlgasto.app.domain.repository

import com.controlgasto.app.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<User?>
    suspend fun login(email: String, password: String): Result<User>
    suspend fun register(email: String, password: String): Result<User>
    suspend fun logout()
    fun isLoggedIn(): Boolean
    suspend fun updateProStatus(isPro: Boolean)
    suspend fun updateProExpiry(expiresAtMs: Long)
}
