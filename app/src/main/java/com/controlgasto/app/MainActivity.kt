package com.controlgasto.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.rememberNavController
import com.controlgasto.app.core.UserPreferences
import com.controlgasto.app.presentation.navigation.NavGraph
import com.controlgasto.app.ui.theme.ControlGastoTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.net.toUri
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var userPreferences: UserPreferences
    private val forceUpdateViewModel: ForceUpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkMode by userPreferences.isDarkMode.collectAsState(initial = false)
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    val controller = WindowInsetsControllerCompat(window, view)
                    controller.isAppearanceLightStatusBars = !isDarkMode
                    controller.isAppearanceLightNavigationBars = !isDarkMode
                }
            }
            ControlGastoTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val updateState by forceUpdateViewModel.state.collectAsState()

                    NavGraph(navController = navController)

                    if (updateState.showDialog) {
                        // Si es forzado: bloquea el botón Atrás
                        BackHandler(enabled = updateState.isForced) { /* no-op */ }

                        AlertDialog(
                            onDismissRequest = {
                                if (!updateState.isForced) forceUpdateViewModel.dismiss()
                            },
                            title = { Text("Actualización disponible") },
                            text = { Text(updateState.updateMessage) },
                            confirmButton = {
                                Button(onClick = { openUrl(updateState.playStoreUrl) }) {
                                    Text("Actualizar ahora")
                                }
                            },
                            dismissButton = if (!updateState.isForced) {
                                { TextButton(onClick = { forceUpdateViewModel.dismiss() }) { Text("Ahora no") } }
                            } else null
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        forceUpdateViewModel.checkForUpdate()
    }

    private fun openUrl(url: String) {
        if (url.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) { }
    }
}
