package com.controlgasto.app.presentation.expense

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.controlgasto.app.data.util.BillingPeriodHelper
import com.controlgasto.app.domain.model.Category
import com.controlgasto.app.domain.model.Expense
import com.controlgasto.app.presentation.navigation.Screen
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    navController: NavController,
    viewModel: ExpenseViewModel = hiltViewModel()
) {
    val state by viewModel.listState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gastos") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.AddExpense.route) }) {
                Icon(Icons.Default.Add, contentDescription = "Agregar")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            MonthSelector(
                label = state.currentMonthLabel,
                onPrev = { viewModel.previousMonth() },
                onNext = { viewModel.nextMonth() },
                canGoNext = state.canGoNext
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.isEmpty) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📭", fontSize = 48.sp)
                        Text("Sin gastos en ${state.currentMonthLabel}")
                        Button(onClick = { navController.navigate(Screen.AddExpense.route) }) { Text("Agregar el primero") }
                    }
                }
            } else {
                val catMap = state.categories.associateBy { it.id }
                val fmt = NumberFormat.getCurrencyInstance(Locale("es", "AR"))

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Resumen total
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Total ${state.currentMonthLabel}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(fmt.format(state.totalAmount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Sección tarjetas
                    if (state.cardGroups.isNotEmpty()) {
                        item {
                            Text(
                                "💳 Tarjetas a pagar",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(state.cardGroups, key = { "${it.card.id}_${it.period.month}" }) { group ->
                            CardGroupRow(
                                group = group,
                                catMap = catMap,
                                onExpenseClick = { navController.navigate(Screen.EditExpense.createRoute(it.id)) },
                                onExpenseDelete = { viewModel.deleteExpense(it) }
                            )
                        }
                    }

                    // Sección efectivo
                    if (state.cashExpenses.isNotEmpty()) {
                        item {
                            Text(
                                "💵 Efectivo y otros",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                        }
                        items(state.cashExpenses, key = { it.id }) { expense ->
                            val installNum = if (expense.isInstallment) {
                                val cal = Calendar.getInstance().apply { timeInMillis = expense.date }
                                val startTotal = cal.get(Calendar.YEAR) * 12 + (cal.get(Calendar.MONTH) + 1)
                                state.selectedYear * 12 + state.selectedMonth - startTotal + 1
                            } else null
                            ExpenseRow(
                                expense = expense,
                                categoryName = catMap[expense.categoryId]?.name ?: "Otros",
                                categoryIcon = catMap[expense.categoryId]?.icon ?: "📦",
                                installmentNumber = installNum,
                                onClick = { navController.navigate(Screen.EditExpense.createRoute(expense.id)) },
                                onDelete = { viewModel.deleteExpense(expense) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardGroupRow(
    group: CardExpenseGroup,
    catMap: Map<String, Category>,
    onExpenseClick: (Expense) -> Unit,
    onExpenseDelete: (Expense) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val fmt = NumberFormat.getCurrencyInstance(Locale("es", "AR"))
    val dueSoon = BillingPeriodHelper.isDueSoon(group.period.dueDate)
    val cardColor = Color(group.card.colorHex)

    Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column {
            // Header — siempre visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Franja de color de la tarjeta
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(48.dp)
                        .background(cardColor, RoundedCornerShape(2.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${group.card.name} ···${group.card.lastFourDigits}",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Consumo ${BillingPeriodHelper.formatDateShort(group.period.periodStart)} → ${BillingPeriodHelper.formatDateShort(group.period.periodEnd)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val dueColor = if (dueSoon) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        Text(
                            "Vence ${BillingPeriodHelper.formatDateFull(group.period.dueDate)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = dueColor,
                            fontWeight = if (dueSoon) FontWeight.Bold else FontWeight.Normal
                        )
                        if (dueSoon) {
                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(4.dp)) {
                                Text("¡Próximo!", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmt.format(group.total), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text("${group.expenses.size} gastos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Lista expandible
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    group.expenses.forEach { expense ->
                        val installNum = if (expense.isInstallment && expense.billingPeriod != null) {
                            val pParts = group.period.month.split("-")
                            val pTotal = (pParts[0].toIntOrNull() ?: 0) * 12 + (pParts[1].toIntOrNull() ?: 0)
                            val bParts = expense.billingPeriod!!.split("-")
                            val bTotal = (bParts[0].toIntOrNull() ?: 0) * 12 + (bParts[1].toIntOrNull() ?: 0)
                            pTotal - bTotal + 1
                        } else null
                        ExpenseRow(
                            expense = expense,
                            categoryName = catMap[expense.categoryId]?.name ?: "Otros",
                            categoryIcon = catMap[expense.categoryId]?.icon ?: "📦",
                            installmentNumber = installNum,
                            onClick = { onExpenseClick(expense) },
                            onDelete = { onExpenseDelete(expense) }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(label: String, onPrev: () -> Unit, onNext: () -> Unit, canGoNext: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Mes anterior")
        }
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp))
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Mes siguiente", tint = if (canGoNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, categoryName: String, categoryIcon: String, installmentNumber: Int? = null, onClick: () -> Unit, onDelete: () -> Unit) {
    val fmt = NumberFormat.getCurrencyInstance(Locale("es", "AR"))
    val date = SimpleDateFormat("dd MMM yyyy", Locale("es")).format(Date(expense.date))
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar gasto") },
            text = { Text("¿Eliminar \"${expense.title}\"?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(categoryIcon, style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(expense.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                    if (expense.isRecurring) {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) {
                            Text("🔁 Fijo", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    } else if (expense.isInstallment) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                            val label = if (installmentNumber != null) "cuota $installmentNumber/${expense.totalInstallments}" else "${expense.totalInstallments} cuotas"
                            Text(label, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
                Text("$categoryName · $date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (expense.note.isNotBlank()) Text(expense.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fmt.format(expense.installmentAmount), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                when {
                    expense.isInstallment -> Text("/ ${fmt.format(expense.amount)} total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    expense.isRecurring -> Text("mensual", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
