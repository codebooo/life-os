package com.lifeos.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lifeos.app.ui.screen.PlaceholderScreen
import com.lifeos.core.ui.navigation.LifeDestination
import com.lifeos.core.ui.navigation.TopLevelDestination
import com.lifeos.feature.chat.ChatRoute

/**
 * Single-activity app shell (§1.3): adaptive scaffold, short M3E bottom bar,
 * type-safe NavHost. Placeholder screens are replaced as feature phases land.
 */
@Composable
fun LifeOsApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hasRoute(destination.route::class) == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = LifeDestination.Home,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<LifeDestination.Home> {
                PlaceholderScreen(
                    title = "Home",
                    description = "The ranked card feed and app grid arrive with the dashboard module.",
                )
            }
            composable<LifeDestination.Calendar> {
                PlaceholderScreen(
                    title = "Calendar",
                    description = "Month, week, day and agenda views arrive in Phase 3.",
                )
            }
            composable<LifeDestination.Tasks> {
                PlaceholderScreen(
                    title = "Tasks",
                    description = "To-dos and reminders arrive in Phase 3.",
                )
            }
            composable<LifeDestination.Inbox> {
                PlaceholderScreen(
                    title = "Inbox",
                    description = "Unified messages and sorted email arrive in Phases 4 and 8.",
                )
            }
            composable<LifeDestination.Assistant> {
                ChatRoute()
            }
        }
    }
}
