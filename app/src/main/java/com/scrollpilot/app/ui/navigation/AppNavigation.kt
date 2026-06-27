package com.scrollpilot.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.scrollpilot.app.ui.screens.AppSelectionScreen
import com.scrollpilot.app.ui.screens.HomeScreen
import com.scrollpilot.app.ui.screens.SettingsScreen

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    Scaffold(
        bottomBar = {
            val backStack by nav.currentBackStackEntryAsState()
            val current = backStack?.destination?.route
            NavigationBar {
                NavigationBarItem(
                    selected = current == "home",
                    onClick  = { nav.navigate("home") { launchSingleTop = true } },
                    icon     = { Icon(Icons.Default.Home, null) },
                    label    = { Text("Home") }
                )
                NavigationBarItem(
                    selected = current == "apps",
                    onClick  = { nav.navigate("apps") { launchSingleTop = true } },
                    icon     = { Icon(Icons.Default.Apps, null) },
                    label    = { Text("Apps") }
                )
                NavigationBarItem(
                    selected = current == "settings",
                    onClick  = { nav.navigate("settings") { launchSingleTop = true } },
                    icon     = { Icon(Icons.Default.Settings, null) },
                    label    = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = "home") {
            composable("home")     { HomeScreen(padding = padding) }
            composable("apps")     { AppSelectionScreen(padding = padding) }
            composable("settings") { SettingsScreen(padding = padding) }
        }
    }
}
