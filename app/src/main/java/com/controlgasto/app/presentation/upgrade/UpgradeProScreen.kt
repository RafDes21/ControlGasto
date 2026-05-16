package com.controlgasto.app.presentation.upgrade

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.controlgasto.app.presentation.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeProScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modo Pro ✨") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("💎", style = MaterialTheme.typography.displayLarge)
            Text("ControlGasto Pro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Todo lo que necesitás para tomar el control total de tus finanzas", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))

            // Comparación
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureColumn(title = "Free", isPro = false, modifier = Modifier.weight(1f))
                FeatureColumn(title = "Pro ✨", isPro = true, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { navController.navigate(Screen.Login.route()) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Activar Pro", style = MaterialTheme.typography.titleMedium)
            }

            TextButton(onClick = { navController.popBackStack() }) {
                Text("Seguir en Free")
            }
        }
    }
}

@Composable
private fun FeatureColumn(title: String, isPro: Boolean, modifier: Modifier = Modifier) {
    val features = listOf(
        "Gastos ilimitados" to true,
        "5 categorías" to !isPro,
        "Categorías ilimitadas" to isPro,
        "Historial completo" to isPro,
        "Backup en la nube" to isPro,
        "Sincronización" to isPro,
        "5 análisis IA/mes" to !isPro,
        "IA ilimitada" to isPro,
        "Exportar CSV" to isPro,
        "Modo oscuro" to isPro,
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isPro) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Divider()
            features.forEach { (feature, available) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(if (available) "✅" else "❌", style = MaterialTheme.typography.bodySmall)
                    Text(feature, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
