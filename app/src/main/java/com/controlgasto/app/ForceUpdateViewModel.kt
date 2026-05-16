package com.controlgasto.app

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ForceUpdateState(
    val showDialog: Boolean = false,
    val isForced: Boolean = false,
    val updateMessage: String = "Hay una nueva versión disponible.",
    val playStoreUrl: String = ""
)

@HiltViewModel
class ForceUpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfig: FirebaseRemoteConfig
) : ViewModel() {

    private val _state = MutableStateFlow(ForceUpdateState())
    val state: StateFlow<ForceUpdateState> = _state.asStateFlow()

    init {
        checkForUpdate()
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            try {
                remoteConfig.fetchAndActivate().await()

                val latestVersion = remoteConfig.getString("latest_version")
                    .takeIf { it.isNotBlank() } ?: return@launch

                val currentVersion = getInstalledVersion() ?: return@launch
                if (!needsUpdate(currentVersion, latestVersion)) return@launch

                _state.value = ForceUpdateState(
                    showDialog = true,
                    isForced = remoteConfig.getBoolean("force_update"),
                    updateMessage = remoteConfig.getString("update_message")
                        .takeIf { it.isNotBlank() }
                        ?: "Hay una nueva versión disponible.",
                    playStoreUrl = remoteConfig.getString("play_store_url")
                )
            } catch (e: Exception) {
                // Sin conexión: no bloquear al usuario
            }
        }
    }

    fun dismiss() {
        if (!_state.value.isForced) {
            _state.value = _state.value.copy(showDialog = false)
        }
    }

    private fun getInstalledVersion(): String? {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
    }

    private fun needsUpdate(current: String, latest: String): Boolean {
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(c.size, l.size)
        for (i in 0 until len) {
            val cv = c.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (cv < lv) return true
            if (cv > lv) return false
        }
        return false
    }
}
