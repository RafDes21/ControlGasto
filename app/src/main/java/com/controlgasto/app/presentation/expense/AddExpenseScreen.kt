package com.controlgasto.app.presentation.expense

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.controlgasto.app.data.util.BillingPeriodHelper
import com.controlgasto.app.domain.model.CreditCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    navController: NavController,
    expenseId: String?,
    viewModel: ExpenseViewModel = hiltViewModel()
) {
    val state by viewModel.addState.collectAsState()
    val listState by viewModel.listState.collectAsState()
    val availableCards by viewModel.availableCards.collectAsState()
    val isEdit = expenseId != null
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDeleteDialog && expenseId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar gasto") },
            text = { Text("¿Querés eliminar \"${state.title}\"? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAndNavigateBack(expenseId); showDeleteDialog = false }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.onDateChange(it) }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    LaunchedEffect(expenseId) {
        if (expenseId != null) viewModel.loadExpense(expenseId)
        else viewModel.resetAddState()
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Editar gasto" else "Nuevo gasto") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (isEdit) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = state.title, onValueChange = viewModel::onTitleChange,
                label = { Text("Título *") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )

            OutlinedTextField(
                value = state.amount, onValueChange = viewModel::onAmountChange,
                label = { Text("Monto total *") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, prefix = { Text("$") }
            )

            // Fecha — solo para gastos en efectivo/débito; tarjeta siempre toma la fecha de hoy
//            if (state.cardId == null) {
//                OutlinedTextField(
//                    value = BillingPeriodHelper.formatDateFull(state.date),
//                    onValueChange = {},
//                    label = { Text("Fecha") },
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .clickable { showDatePicker = true },
//                    readOnly = true,
//                    enabled = false,
//                    trailingIcon = {
//                        Icon(Icons.Default.DateRange, contentDescription = "Elegir fecha",
//                            modifier = Modifier.clickable { showDatePicker = true })
//                    }
//                )
//            }

            // Gasto fijo
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isRecurring) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🔁 Gasto fijo", fontWeight = FontWeight.Medium)
                            Text(
                                if (state.isRecurring) "Se suma automáticamente cada mes"
                                else "Se repite el mismo monto cada mes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = state.isRecurring, onCheckedChange = viewModel::onIsRecurringChange)
                    }
                    if (state.isRecurring) {
                        OutlinedTextField(
                            value = state.durationMonths,
                            onValueChange = viewModel::onDurationMonthsChange,
                            label = { Text("Duración (meses)") },
                            placeholder = { Text("Ej: 12 — dejá vacío si es indefinido") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            suffix = { Text("meses") }
                        )
                    }
                }
            }

            // Tipo de pago
            Text("Tipo de pago", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.cardId == null,
                    onClick = { viewModel.onCardChange(null) },
                    label = { Text("💵 Efectivo") }
                )
                FilterChip(
                    selected = state.cardId != null,
                    onClick = { if (availableCards.isNotEmpty()) viewModel.onCardChange(availableCards.first().id) },
                    label = { Text("💳 Tarjeta") },
                    enabled = availableCards.isNotEmpty()
                )
            }
            if (availableCards.isEmpty()) {
                Text("Sin tarjetas agregadas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Selector de tarjeta + cuotas (no aplica a gastos fijos)
            if (state.cardId != null && availableCards.isNotEmpty()) {
                CardSelector(
                    cards = availableCards,
                    selectedCardId = state.cardId,
                    onCardSelected = { viewModel.onCardChange(it) }
                )

                // Info de período de facturación
                state.billingInfo?.let { info ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "💳 Período: ${BillingPeriodHelper.formatDateShort(info.period.periodStart)} → ${BillingPeriodHelper.formatDateShort(info.period.periodEnd)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "📅 Vence: ${BillingPeriodHelper.formatDateFull(info.dueDateMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = if (BillingPeriodHelper.isDueSoon(info.dueDateMillis)) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                if (state.billingInfo == null && state.noPeriodForMonth) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "⚠ Sin cierre configurado para este mes. Configurá el período en Tarjetas.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                if (!state.isRecurring) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("¿Tiene cuotas?", fontWeight = FontWeight.Medium)
                                    Text("Se distribuye el monto entre los meses", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = state.hasInstallments, onCheckedChange = viewModel::onHasInstallmentsChange)
                            }
                            if (state.hasInstallments) {
                                OutlinedTextField(
                                    value = state.installmentCount,
                                    onValueChange = viewModel::onInstallmentCountChange,
                                    label = { Text("Cantidad de cuotas") },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    suffix = { Text("cuotas") }
                                )
                                if (state.amount.isNotBlank() && state.installmentCount.toIntOrNull() != null) {
                                    val total = state.amount.replace(",", ".").toDoubleOrNull() ?: 0.0
                                    val cuotas = state.installmentCount.toIntOrNull() ?: 1
                                    if (cuotas > 0) {
                                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                                            Text(
                                                "💡 $cuotas cuotas de $${"%.2f".format(total / cuotas)} por mes",
                                                modifier = Modifier.padding(8.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Categorías — LazyRow para evitar compresión por nombres largos
            val availableCategories = if (listState.isProMode) listState.categories
                                       else listState.categories.filter { it.isDefault }
            Text("Categoría *", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(availableCategories) { cat ->
                    FilterChip(
                        selected = state.categoryId == cat.id,
                        onClick = { viewModel.onCategoryChange(cat.id) },
                        label = { Text("${cat.icon} ${cat.name}") }
                    )
                }
            }

            OutlinedTextField(
                value = state.note, onValueChange = viewModel::onNoteChange,
                label = { Text("Nota (opcional)") }, modifier = Modifier.fillMaxWidth(),
                minLines = 2, maxLines = 4
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { viewModel.saveExpense(expenseId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !state.isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (isEdit) "Guardar cambios" else "Agregar gasto", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CardSelector(cards: List<CreditCard>, selectedCardId: String?, onCardSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Tarjeta", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        cards.forEach { card ->
            FilterChip(
                selected = selectedCardId == card.id,
                onClick = { onCardSelected(card.id) },
                label = { Text("${card.name} ···${card.lastFourDigits}") }
            )
        }
    }
}
