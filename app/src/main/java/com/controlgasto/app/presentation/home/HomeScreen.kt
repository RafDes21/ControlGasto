package com.controlgasto.app.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.controlgasto.app.core.UserPreferences
import com.controlgasto.app.data.util.BillingPeriodHelper
import com.controlgasto.app.domain.model.Category
import com.controlgasto.app.domain.model.Expense
import com.controlgasto.app.presentation.expense.CardExpenseGroup
import com.controlgasto.app.presentation.navigation.Screen
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddExpense.route) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar gasto", tint = Color.White)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item { TopBar(state, navController) }
            item {
                MonthSummaryCard(
                    state = state,
                    onPrev = { viewModel.previousMonth() },
                    onNext = { viewModel.nextMonth() },
                    onNavigateToIncomes = { navController.navigate(Screen.Incomes.route) },
                    onToggleIncomeVisibility = { viewModel.toggleIncomeVisibility() }
                )
            }
            if (!state.isLoggedIn) {
                item { CloudBackupBanner(onLogin = { navController.navigate(Screen.Login.route()) }) }
            }
            item { BottomNavBar(navController) }

            // Tarjetas a pagar este mes
            if (state.cardGroups.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💳 Tarjetas a pagar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { navController.navigate(Screen.ExpenseList.route) }) { Text("Ver todos") }
                    }
                }
                items(state.cardGroups) { group ->
                    HomeCardGroupRow(group = group, navController = navController)
                }
            }

            // Últimos gastos en efectivo/débito
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Últimos gastos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (state.cardGroups.isEmpty()) {
                        TextButton(onClick = { navController.navigate(Screen.ExpenseList.route) }) { Text("Ver todos") }
                    }
                }
            }
            if (state.recentExpenses.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📭", fontSize = 48.sp)
                            Text("Sin gastos este mes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(state.recentExpenses) { expense ->
                    val installNum = if (expense.isInstallment) {
                        val cal = Calendar.getInstance().apply { timeInMillis = expense.date }
                        val startTotal = cal.get(Calendar.YEAR) * 12 + (cal.get(Calendar.MONTH) + 1)
                        state.selectedYear * 12 + state.selectedMonth - startTotal + 1
                    } else null
                    ExpenseItem(expense, installmentNumber = installNum, onClick = { navController.navigate(Screen.EditExpense.createRoute(expense.id)) })
                }
            }
        }
    }
}

@Composable
private fun HomeCardGroupRow(group: CardExpenseGroup, navController: NavController) {
    val fmt = NumberFormat.getCurrencyInstance(Locale("es", "AR"))
    val cardColor = Color(group.card.colorHex)
    val dueSoon = BillingPeriodHelper.isDueSoon(group.period.dueDate)
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(44.dp)
                        .background(cardColor, RoundedCornerShape(2.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${group.card.name} ···${group.card.lastFourDigits}",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    val dueColor = if (dueSoon) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        "Vence ${BillingPeriodHelper.formatDateFull(group.period.dueDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = dueColor,
                        fontWeight = if (dueSoon) FontWeight.Bold else FontWeight.Normal
                    )
                    if (dueSoon) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                "¡Próximo!",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
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
                        ExpenseItem(
                            expense = expense,
                            installmentNumber = installNum,
                            onClick = { navController.navigate(Screen.EditExpense.createRoute(expense.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(state: HomeUiState, navController: NavController) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Hola! 👋", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("ControlGasto", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Perfil", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun MonthSummaryCard(
    state: HomeUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onNavigateToIncomes: () -> Unit,
    onToggleIncomeVisibility: () -> Unit
) {
    val fmt = NumberFormat.getCurrencyInstance(Locale("es", "AR"))
    val compromiso = state.monthlyTotal + state.cardTotal
    val income = state.monthlyIncome
    val isHidden = income?.isHidden == true

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.80f)
                        )
                    )
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {

                    // Navegación de mes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Mes anterior", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Text(
                            "${state.currentMonth} ${state.selectedYear}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = onNext, modifier = Modifier.size(32.dp), enabled = state.canGoNext) {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = "Mes siguiente",
                                tint = if (state.canGoNext) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Ingreso | Compromiso
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Columna izquierda: Ingreso del mes
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Ingreso del mes",
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (income != null) {
                                    IconButton(
                                        onClick = onToggleIncomeVisibility,
                                        modifier = Modifier.size(24.dp).padding(start = 4.dp)
                                    ) {
                                        Icon(
                                            if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isHidden) "Mostrar" else "Ocultar",
                                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            if (income == null) {
                                Text(
                                    "Agregar +",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                                    modifier = Modifier.clickable { onNavigateToIncomes() }
                                )
                            } else if (isHidden) {
                                Text(
                                    "••••••",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.clickable { onNavigateToIncomes() }
                                )
                            } else {
                                Text(
                                    fmt.format(income.amount),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.clickable { onNavigateToIncomes() }
                                )
                            }
                        }

                        // Separador vertical
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(52.dp)
                                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f))
                        )

                        // Columna derecha: Compromiso
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                "Compromiso",
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                fmt.format(compromiso),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (compromiso > 0)
                                    MaterialTheme.colorScheme.errorContainer
                                else
                                    MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    // Te queda disponible (solo si hay ingreso)
                    if (income != null) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f))
                        Spacer(Modifier.height(10.dp))

                        val disponible = income.amount - compromiso
                        val isPositive = disponible >= 0

                        Text(
                            "Te queda disponible",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.height(2.dp))

                        if (isHidden) {
                            Text(
                                "••••••",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                fmt.format(if (isPositive) disponible else -disponible),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (isPositive)
                                    "💰 Esto es lo que te queda después de tus gastos y tarjetas."
                                else
                                    "⚠️ Tus gastos superan tu ingreso del mes.",
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    } else {
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudBackupBanner(onLogin: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("☁️", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Tus datos son locales",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Creá una cuenta para guardarlos en la nube y no perderlos nunca",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            TextButton(onClick = onLogin) { Text("Ingresar") }
        }
    }
}

@Composable
private fun BottomNavBar(navController: NavController) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavPill(emoji = "🏠", label = "Inicio",   selected = true,  onClick = {})
            NavPill(emoji = "📋", label = "Gastos",   selected = false, onClick = { navController.navigate(Screen.ExpenseList.route) })
            NavPill(emoji = "📊", label = "Estadíst.", selected = false, onClick = { navController.navigate(Screen.Stats.route) })
            NavPill(emoji = "💳", label = "Tarjetas", selected = false, onClick = { navController.navigate(Screen.CreditCards.route) })
            NavPill(emoji = "⚙️", label = "Ajustes",  selected = false, onClick = { navController.navigate(Screen.Settings.route) })
        }
    }
}

@Composable
private fun NavPill(emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (selected) 14.dp else 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(emoji, fontSize = 18.sp)
            if (selected) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ExpenseItem(expense: Expense, installmentNumber: Int? = null, onClick: () -> Unit) {
    val fmt = NumberFormat.getCurrencyInstance(Locale("es", "AR"))
    val date = SimpleDateFormat("dd MMM", Locale("es")).format(Date(expense.date))
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(expense.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                    if (expense.isRecurring) {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) {
                            Text("🔁", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    } else if (expense.isInstallment) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                            val label = if (installmentNumber != null) "$installmentNumber/${expense.totalInstallments}" else "${expense.totalInstallments}c"
                            Text(label, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
                Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    fmt.format(expense.installmentAmount),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                when {
                    expense.isInstallment -> Text("por cuota", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    expense.isRecurring -> Text("mensual", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
