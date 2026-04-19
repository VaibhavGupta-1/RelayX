package com.example.relayx.ui.navigation

/**
 * Sealed class defining all navigation destinations in the app.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Transfers : Screen("transfers")
}
