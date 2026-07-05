package com.lifeos.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lifeos.app.ui.screen.HomeScreen
import com.lifeos.app.ui.screen.InboxTabScreen
import com.lifeos.app.ui.screen.TasksTabScreen
import com.lifeos.app.ui.settings.SettingsRoute
import com.lifeos.core.ui.navigation.LifeDestination
import com.lifeos.core.ui.navigation.TopLevelDestination
import com.lifeos.feature.calendar.CalendarRoute
import com.lifeos.feature.capture.LoggerRoute
import com.lifeos.feature.capture.QuickCaptureSheet
import com.lifeos.feature.chat.ChatRoute
import com.lifeos.feature.dhl.PackagesRoute
import com.lifeos.feature.finance.FinanceRoute
import com.lifeos.feature.imagereasoning.ScanRoute
import com.lifeos.feature.books.BooksRoute
import com.lifeos.feature.nas.NasRoute
import com.lifeos.feature.notes.NotesRoute
import com.lifeos.feature.planner.PlannerRoute
import com.lifeos.feature.route.RouteRoute
import com.lifeos.feature.smarthome.SmartHomeRoute

/**
 * Single-activity app shell (§1.3): adaptive scaffold, short M3E bottom bar,
 * type-safe NavHost, and the global quick-capture affordance (§7.8).
 */
@Composable
fun LifeOsApp(captureRequests: Int = 0) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    var showQuickCapture by remember { mutableStateOf(false) }

    // Assistant gesture (long-press home) lands here (§Module 10).
    androidx.compose.runtime.LaunchedEffect(captureRequests) {
        if (captureRequests > 0) showQuickCapture = true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            // Quick capture lives on Home only; feature screens own their create FABs.
            if (currentDestination?.hasRoute(LifeDestination.Home::class) == true) {
                FloatingActionButton(onClick = { showQuickCapture = true }) {
                    Icon(Icons.Filled.Bolt, contentDescription = "Quick capture")
                }
            }
        },
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
                HomeScreen(onNavigate = { navController.navigate(it) })
            }
            composable<LifeDestination.Calendar> {
                CalendarRoute()
            }
            composable<LifeDestination.Tasks> {
                TasksTabScreen()
            }
            composable<LifeDestination.Inbox> {
                InboxTabScreen()
            }
            composable<LifeDestination.Assistant> {
                ChatRoute()
            }
            composable<LifeDestination.Notes> {
                NotesRoute()
            }
            composable<LifeDestination.Logger> {
                LoggerRoute()
            }
            composable<LifeDestination.Packages> {
                PackagesRoute()
            }
            composable<LifeDestination.Scan> {
                ScanRoute()
            }
            composable<LifeDestination.Finance> {
                FinanceRoute()
            }
            composable<LifeDestination.Nas> { NasRoute() }
            composable<LifeDestination.Books> { BooksRoute() }
            composable<LifeDestination.Routes> { RouteRoute() }
            composable<LifeDestination.SmartHome> { SmartHomeRoute() }
            composable<LifeDestination.Planner> { PlannerRoute() }
            composable<LifeDestination.Settings> {
                SettingsRoute(onBack = { navController.popBackStack() })
            }
        }
    }

    if (showQuickCapture) {
        QuickCaptureSheet(onDismiss = { showQuickCapture = false })
    }
}
