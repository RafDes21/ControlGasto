package com.controlgasto.app.presentation.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.controlgasto.app.data.util.BillingPeriodHelper
import com.controlgasto.app.domain.model.CardClosingPeriod
import com.controlgasto.app.domain.model.CreditCard

private val cardPalette = listOf(
    0xFF1565C0L, 0xFF6A1B9AL, 0xFF00695CL,
    0xFFE65100L, 0xFFB71C1CL, 0xFF37474FL
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditCardsScreen(
    navController: NavController,
    viewModel: CreditCardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddCardDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, lastFour, dueDay, closingDay, colorHex ->
                viewModel.addCard(name, lastFour, dueDay, closingDay, colorHex)
                showAddDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tarjetas de crédito") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.canAddCard) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar tarjeta")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.cards.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("💳", fontSize = 48.sp)
                            Text("Sin tarjetas agregadas")
                            Text("Agregá tu primera tarjeta de crédito", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(state.cards, key = { it.id }) { card ->
                    val periods by viewModel.periodsForCard(card.id).collectAsState(initial = emptyList())
                    CreditCardItem(
                        card = card,
                        periods = periods,
                        onDelete = { viewModel.deleteCard(card) },
                        onEdit = { name, lastFour, dueDay, closingDay, colorHex ->
                            viewModel.updateCard(card, name, lastFour, dueDay, closingDay, colorHex)
                        },
                        onUpdatePeriodClosingDay = { period, newDay, fromForward ->
                            viewModel.updatePeriodClosingDay(period, newDay, fromForward)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditCardItem(
    card: CreditCard,
    periods: List<CardClosingPeriod>,
    onDelete: () -> Unit,
    onEdit: (String, String, Int, Int, Long) -> Unit,
    onUpdatePeriodClosingDay: (CardClosingPeriod, Int, Boolean) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var periodToEdit by remember { mutableStateOf<CardClosingPeriod?>(null) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar tarjeta") },
            text = { Text("¿Eliminar la tarjeta ${card.name}?\n\nTodos los gastos registrados con esta tarjeta (fijos, cuotas y únicos) también serán eliminados. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showEditDialog) {
        EditCardDialog(
            card = card,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, lastFour, dueDay, closingDay, colorHex ->
                onEdit(name, lastFour, dueDay, closingDay, colorHex)
                showEditDialog = false
            }
        )
    }

    periodToEdit?.let { period ->
        EditPeriodDialog(
            period = period,
            onDismiss = { periodToEdit = null },
            onConfirm = { newDay, fromForward ->
                onUpdatePeriodClosingDay(period, newDay, fromForward)
                periodToEdit = null
            }
        )
    }

    Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().background(Color(card.colorHex)).padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Text("💳", fontSize = 28.sp)
                        Row {
                            IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Text(card.name, fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text("···· ···· ···· ${card.lastFourDigits}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column {
                            Text("Vencimiento", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                            Text("Día ${card.dueDay}", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                        Column {
                            Text("Cierre", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                            Text("Día ${card.closingDay}", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (expanded) "Ocultar períodos" else "Ver períodos",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (expanded && periods.isNotEmpty()) {
                val year = periods.first().month.split("-")[0]
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(
                        "Períodos $year",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    periods.forEach { period ->
                        PeriodRow(period = period, onEdit = { periodToEdit = period })
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodRow(period: CardClosingPeriod, onEdit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(BillingPeriodHelper.monthLabel(period.month), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "Cierra día ${period.closingDay}  •  Vence ${BillingPeriodHelper.formatDateShort(period.dueDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Editar período", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun EditPeriodDialog(
    period: CardClosingPeriod,
    onDismiss: () -> Unit,
    onConfirm: (Int, Boolean) -> Unit
) {
    var closingDayText by remember { mutableStateOf(period.closingDay.toString()) }
    var fromForward by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar cierre — ${BillingPeriodHelper.monthLabel(period.month)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = closingDayText,
                    onValueChange = { closingDayText = it },
                    label = { Text("Día de cierre (1–28)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Text("Aplicar a:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { fromForward = false },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = !fromForward, onClick = { fromForward = false })
                    Text("Solo este mes", modifier = Modifier.padding(start = 4.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { fromForward = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = fromForward, onClick = { fromForward = true })
                    Text("Desde este mes en adelante", modifier = Modifier.padding(start = 4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val day = closingDayText.toIntOrNull()?.coerceIn(1, 28) ?: return@TextButton
                onConfirm(day, fromForward)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun AddCardDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Int, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var lastFour by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("") }
    var closingDay by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(cardPalette.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva tarjeta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nombre (ej: Visa Banco Nación)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = lastFour, onValueChange = { if (it.length <= 4) lastFour = it },
                    label = { Text("Últimos 4 dígitos") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dueDay, onValueChange = { dueDay = it },
                        label = { Text("Vence día") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                    )
                    OutlinedTextField(
                        value = closingDay, onValueChange = { closingDay = it },
                        label = { Text("Cierra día") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                    )
                }
                Text("Color de tarjeta", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cardPalette.forEach { hex ->
                        val isSelected = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(hex))
                                .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)) else Modifier)
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val due = dueDay.toIntOrNull() ?: 1
                    val closing = closingDay.toIntOrNull() ?: 28
                    if (name.isNotBlank() && lastFour.isNotBlank()) {
                        onConfirm(name, lastFour, due, closing, selectedColor)
                    }
                }
            ) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun EditCardDialog(
    card: CreditCard,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Int, Long) -> Unit
) {
    var name by remember { mutableStateOf(card.name) }
    var lastFour by remember { mutableStateOf(card.lastFourDigits) }
    var dueDay by remember { mutableStateOf(card.dueDay.toString()) }
    var closingDay by remember { mutableStateOf(card.closingDay.toString()) }
    var selectedColor by remember { mutableStateOf(card.colorHex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar tarjeta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = lastFour, onValueChange = { if (it.length <= 4) lastFour = it },
                    label = { Text("Últimos 4 dígitos") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dueDay, onValueChange = { dueDay = it },
                        label = { Text("Vence día") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                    )
                    OutlinedTextField(
                        value = closingDay, onValueChange = { closingDay = it },
                        label = { Text("Cierra día") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                    )
                }
                Text("Color de tarjeta", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cardPalette.forEach { hex ->
                        val isSelected = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(hex))
                                .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)) else Modifier)
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val due = dueDay.toIntOrNull() ?: card.dueDay
                    val closing = closingDay.toIntOrNull() ?: card.closingDay
                    if (name.isNotBlank() && lastFour.isNotBlank()) {
                        onConfirm(name, lastFour, due, closing, selectedColor)
                    }
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
