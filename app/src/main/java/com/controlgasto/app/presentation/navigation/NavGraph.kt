package com.controlgasto.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navOptions
import com.controlgasto.app.presentation.auth.LoginScreen
import com.controlgasto.app.presentation.auth.RegisterScreen
import com.controlgasto.app.presentation.cards.CreditCardsScreen
import com.controlgasto.app.presentation.categories.CategoriesScreen
import com.controlgasto.app.presentation.expense.AddExpenseScreen
import com.controlgasto.app.presentation.expense.ExpenseListScreen
import com.controlgasto.app.presentation.home.HomeScreen
import com.controlgasto.app.presentation.profile.ProfileScreen
import com.controlgasto.app.presentation.settings.SettingsScreen
import com.controlgasto.app.presentation.splash.SplashScreen
import com.controlgasto.app.presentation.stats.StatsScreen
import com.controlgasto.app.presentation.upgrade.UpgradeProScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Splash.route) {

        composable(Screen.Splash.route) { SplashScreen(navController) }
        composable(
            route = Screen.Login.route,
            arguments = listOf(navArgument("fromRegister") { type = NavType.BoolType; defaultValue = false })
        ) { backStack ->
            LoginScreen(navController, fromRegister = backStack.arguments?.getBoolean("fromRegister") ?: false)
        }
        composable(Screen.Register.route) { RegisterScreen(navController) }
        composable(Screen.Home.route) { HomeScreen(navController) }
        composable(Screen.ExpenseList.route) { ExpenseListScreen(navController) }
        composable(Screen.AddExpense.route) { AddExpenseScreen(navController, expenseId = null) }
        composable(
            route = Screen.EditExpense.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStack ->
            AddExpenseScreen(navController, expenseId = backStack.arguments?.getString("id"))
        }
        composable(Screen.Categories.route) { CategoriesScreen(navController) }
        composable(Screen.CreditCards.route) { CreditCardsScreen(navController) }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
        composable(Screen.Stats.route) { StatsScreen(navController) }
        composable(Screen.UpgradePro.route) { UpgradeProScreen(navController) }
        composable(Screen.Profile.route) { ProfileScreen(navController) }
    }
}
