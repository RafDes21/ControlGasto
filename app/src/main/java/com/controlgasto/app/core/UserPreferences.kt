package com.controlgasto.app.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val IS_PRO = booleanPreferencesKey("is_pro")
    private val PRO_EXPIRES_AT = longPreferencesKey("pro_expires_at")
    private val AI_REQUESTS = intPreferencesKey("ai_requests_count")
    private val AI_RESET_DATE = longPreferencesKey("ai_reset_date")
    private val USER_EMAIL = stringPreferencesKey("user_email")
    private val USER_UID = stringPreferencesKey("user_uid")
    private val USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")

    // Derivado de proExpiresAt: si la fecha de vencimiento es futura, el usuario es PRO
    val isProMode: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val expiresAt = prefs[PRO_EXPIRES_AT] ?: 0L
            if (expiresAt > 0L) expiresAt > System.currentTimeMillis()
            else prefs[IS_PRO] ?: false
        }

    val proExpiresAt: Flow<Long> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PRO_EXPIRES_AT] ?: 0L }

    val aiRequestsCount: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[AI_REQUESTS] ?: 0 }

    val userEmail: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[USER_EMAIL] ?: "" }

    val userDisplayName: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[USER_DISPLAY_NAME] ?: "" }

    suspend fun setProMode(isPro: Boolean) {
        context.dataStore.edit { it[IS_PRO] = isPro }
    }

    suspend fun setProExpiresAt(timestampMs: Long) {
        context.dataStore.edit {
            it[PRO_EXPIRES_AT] = timestampMs
            it[IS_PRO] = timestampMs > System.currentTimeMillis()
        }
    }

    suspend fun saveUserSession(uid: String, email: String, displayName: String = "") {
        context.dataStore.edit {
            it[USER_UID] = uid
            it[USER_EMAIL] = email
            it[USER_DISPLAY_NAME] = displayName
        }
    }

    suspend fun clearUserSession() {
        context.dataStore.edit {
            it.remove(USER_UID)
            it.remove(USER_EMAIL)
            it.remove(USER_DISPLAY_NAME)
        }
    }

    suspend fun incrementAiRequests() {
        context.dataStore.edit { it[AI_REQUESTS] = (it[AI_REQUESTS] ?: 0) + 1 }
    }

    suspend fun isProModeSync(): Boolean {
        val prefs = context.dataStore.data.first()
        val expiresAt = prefs[PRO_EXPIRES_AT] ?: 0L
        return if (expiresAt > 0L) expiresAt > System.currentTimeMillis()
        else prefs[IS_PRO] ?: false
    }

    suspend fun resetAiRequestsIfNeeded() {
        val prefs = context.dataStore.data.first()
        val lastReset = prefs[AI_RESET_DATE] ?: 0L
        val oneMonth = 30L * 24 * 60 * 60 * 1000
        if (System.currentTimeMillis() - lastReset > oneMonth) {
            context.dataStore.edit {
                it[AI_REQUESTS] = 0
                it[AI_RESET_DATE] = System.currentTimeMillis()
            }
        }
    }

    companion object {
        const val FREE_AI_LIMIT = 5
        const val FREE_CARD_LIMIT = 1
    }
}
