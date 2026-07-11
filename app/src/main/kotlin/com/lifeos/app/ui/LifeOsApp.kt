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
import com.lifeos.feature.adhd.FocusRoute
import com.lifeos.feature.agentic.MacrosRoute
import com.lifeos.feature.calendar.CalendarRoute
import com.lifeos.feature.capture.LoggerRoute
import com.lifeos.feature.capture.QuickCaptureSheet
import com.lifeos.feature.chat.ChatRoute
import com.lifeos.feature.clock.ClockRoute
import com.lifeos.feature.dhl.PackagesRoute
import com.lifeos.feature.evolution.EvolutionRoute
import com.lifeos.feature.finance.FinanceRoute
import com.lifeos.feature.imagereasoning.ScanRoute
import com.lifeos.feature.books.BooksRoute
import com.lifeos.feature.memex.MemexRoute
import com.lifeos.feature.nas.NasRoute
import com.lifeos.feature.notes.NotesRoute
import com.lifeos.feature.planner.PlannerRoute
import com.lifeos.feature.route.RouteRoute
import com.lifeos.feature.smarthome.SmartHomeRoute
import com.lifeos.feature.downloader.DownloaderRoute
import com.lifeos.feature.plants.PlantsRoute
import com.lifeos.feature.news.NewsRoute
import com.lifeos.feature.vault.VaultRoute
import com.lifeos.feature.screentime.ScreenTimeRoute

/**
 * Single-activity app shell (§1.3): adaptive scaffold, short M3E bottom bar,
 * type-safe NavHost, and the global quick-capture affordance (§7.8).
 */
@Composable
fun LifeOsApp(captureRequests: Int = 0, navBarIds: List<String> = emptyList()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    var showQuickCapture by remember { mutableStateOf(false) }

    // Customizable bottom bar (§Settings): Home pinned first, then the user's
    // tabs in their chosen ORDER (the stored list is ordered).
    val barItems = remember(navBarIds) {
        if (navBarIds.isEmpty()) {
            TopLevelDestination.entries.toList()
        } else {
            listOf(TopLevelDestination.HOME) +
                navBarIds.mapNotNull { id ->
                    TopLevelDestination.entries.firstOrNull { it.name == id && it != TopLevelDestination.HOME }
                }
        }
    }

    // Assistant gesture (long-press home) lands here (§Module 10).
    androidx.compose.runtime.LaunchedEffect(captureRequests) {
        if (captureRequests > 0) showQuickCapture = true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            // Quick capture lives on Home only; feature screens own their create FABs.
            androidx.compose.animation.AnimatedVisibility(
                visible = currentDestination?.hasRoute(LifeDestination.Home::class) == true,
                enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
            ) {
                FloatingActionButton(onClick = { showQuickCapture = true }) {
                    Icon(Icons.Filled.Bolt, contentDescription = "Quick capture")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                barItems.forEach { destination ->
                    val selected = currentDestination?.hasRoute(destination.route::class) == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            // No saveState/restoreState here: modules are pushed on top of
                            // Home, and a save+restore round-trip would put the module
                            // straight back — making the Home tab a no-op.
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
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
            // Gentle shared-axis feel (§7 motion): quick fade + small vertical lift.
            enterTransition = {
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) +
                    androidx.compose.animation.slideInVertically(
                        animationSpec = androidx.compose.animation.core.tween(220),
                        initialOffsetY = { it / 24 },
                    )
            },
            exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)) },
            popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) },
            popExitTransition = {
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)) +
                    androidx.compose.animation.slideOutVertically(
                        animationSpec = androidx.compose.animation.core.tween(140),
                        targetOffsetY = { it / 24 },
                    )
            },
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
            composable<LifeDestination.Clock> { ClockRoute() }
            composable<LifeDestination.Focus> { FocusRoute() }
            composable<LifeDestination.Memex> { MemexRoute() }
            composable<LifeDestination.Macros> { MacrosRoute() }
            composable<LifeDestination.Evolution> { EvolutionRoute() }
            composable<LifeDestination.Downloader> { DownloaderRoute() }
            composable<LifeDestination.Plants> { PlantsRoute() }
            composable<LifeDestination.News> { NewsRoute() }
            composable<LifeDestination.Vault> { VaultRoute() }
            composable<LifeDestination.ScreenTime> { ScreenTimeRoute() }
        }
    }

    if (showQuickCapture) {
        QuickCaptureSheet(onDismiss = { showQuickCapture = false })
    }
}
