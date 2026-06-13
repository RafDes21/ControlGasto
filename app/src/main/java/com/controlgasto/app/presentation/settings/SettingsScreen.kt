package com.controlgasto.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.controlgasto.app.core.UserPreferences
import com.controlgasto.app.presentation.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var selectedCardId by remember { mutableStateOf<String?>(null) }

    if (state.showDowngradeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDowngrade() },
            title = { Text("Volver a modo Free") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tenés ${state.cardCount} tarjetas. En modo Free solo podés conservar 1. ¿Cuál querés mantener?")
                    state.cards.forEach { card ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = selectedCardId == card.id,
                                onClick = { selectedCardId = card.id }
                            )
                            Column {
                                Text(card.name, fontWeight = FontWeight.Medium)
                                Text("···· ${card.lastFourDigits}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(
                        "Las demás tarjetas serán eliminadas. Los gastos asociados quedarán sin tarjeta asignada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val card = state.cards.find { it.id == selectedCardId } ?: state.cards.first()
                        viewModel.confirmDowngrade(card)
                        selectedCardId = null
                    },
                    enabled = state.cards.isNotEmpty()
                ) { Text("Confirmar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDowngrade(); selectedCardId = null }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cuenta
            if (state.isLoggedIn) {
                SettingsCard(onClick = { navController.navigate(Screen.Profile.route) }) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("👤", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(state.userDisplayName.ifBlank { "Mi perfil" }, fontWeight = FontWeight.Medium)
                            Text(state.userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                SettingsCard(onClick = { navController.navigate(Screen.Login.route()) }) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("🔐", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Iniciar sesión", fontWeight = FontWeight.Medium)
                            Text("Guardá tus datos en la nube y no los pierdas nunca", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            /* FASE 2 — Plan actual PRO/FREE
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isProMode) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (state.isProMode) "✨ Plan Pro" else "Plan Free", fontWeight = FontWeight.Bold)
                        if (!state.isProMode) Text("${UserPreferences.FREE_AI_LIMIT - state.aiRequestsUsed} análisis IA restantes este mes", style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.isProMode) {
                        TextButton(onClick = { viewModel.cancelPro() }) {
                            Text("Cancelar Pro", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Button(onClick = { navController.navigate(Screen.UpgradePro.route) }, shape = RoundedCornerShape(8.dp)) {
                            Text("Mejorar")
                        }
                    }
                }
            }
            */

            // Tarjetas
            SettingsCard(onClick = { navController.navigate(Screen.CreditCards.route) }) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("💳", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tarjetas de crédito", fontWeight = FontWeight.Medium)
                        Text("${state.cardCount} tarjeta${if (state.cardCount != 1) "s" else ""} guardada${if (state.cardCount != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Apariencia
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (state.isDarkMode) "🌙" else "☀️", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Modo oscuro", fontWeight = FontWeight.Medium)
                            Text(
                                if (state.isDarkMode) "Activado" else "Desactivado",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(checked = state.isDarkMode, onCheckedChange = { viewModel.toggleDarkMode() })
                }
            }

            // Categorías
            SettingsCard(onClick = { navController.navigate(Screen.Categories.route) }) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("🏷️", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Categorías", fontWeight = FontWeight.Medium)
                        Text("Personalizá tus gastos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            /* FASE 2 — IA Progress
            if (!state.isProMode) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("🤖 Análisis IA", fontWeight = FontWeight.Medium)
                            Text("${state.aiRequestsUsed} / ${UserPreferences.FREE_AI_LIMIT}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { state.aiRequestsUsed.toFloat() / UserPreferences.FREE_AI_LIMIT },
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("usados este mes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            */

if (state.isLoggedIn) {
                OutlinedButton(
                    onClick = { viewModel.logout() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cerrar sesión")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Box(modifier = Modifier.padding(16.dp)) { content() }
    }
}
