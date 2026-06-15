package com.controlgasto.app.presentation.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("auth/login?fromRegister={fromRegister}") {
        fun route(fromRegister: Boolean = false) = "auth/login?fromRegister=$fromRegister"
    }
    object Register : Screen("auth/register")
    object Home : Screen("home")
    object ExpenseList : Screen("expenses")
    object AddExpense : Screen("expense/add")
    object EditExpense : Screen("expense/edit/{id}") {
        fun createRoute(id: String) = "expense/edit/$id"
    }
    object Categories : Screen("categories")
    object CreditCards : Screen("cards")
    object Settings : Screen("settings")
    object Stats : Screen("stats")
    object UpgradePro : Screen("settings/upgrade")
    object Profile : Screen("settings/profile")
    object Incomes : Screen("incomes")
}
