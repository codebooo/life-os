package com.lifeos.core.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Everything that can sit on the bottom bar (§1.3). Home is pinned first; the
 * rest are opt-in and reorderable from Settings, so any module can be a tab.
 */
enum class TopLevelDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: LifeDestination,
) {
    HOME(
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        route = LifeDestination.Home,
    ),
    CALENDAR(
        label = "Calendar",
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
        route = LifeDestination.Calendar,
    ),
    TASKS(
        label = "Tasks",
        selectedIcon = Icons.Filled.Checklist,
        unselectedIcon = Icons.Outlined.Checklist,
        route = LifeDestination.Tasks,
    ),
    INBOX(
        label = "Inbox",
        selectedIcon = Icons.Filled.Inbox,
        unselectedIcon = Icons.Outlined.Inbox,
        route = LifeDestination.Inbox,
    ),
    ASSISTANT(
        label = "Jarvis",
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        route = LifeDestination.Assistant,
    ),

    // Every other module, so the bar is fully user-composed.
    NOTES("Notes", Icons.AutoMirrored.Filled.Note, Icons.AutoMirrored.Outlined.Note, LifeDestination.Notes),
    LOGGER("Logger", Icons.Filled.Insights, Icons.Outlined.Insights, LifeDestination.Logger),
    PACKAGES("Packages", Icons.Filled.LocalShipping, Icons.Outlined.LocalShipping, LifeDestination.Packages),
    SCAN("Scan", Icons.Filled.DocumentScanner, Icons.Outlined.DocumentScanner, LifeDestination.Scan),
    FINANCE("Finance", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, LifeDestination.Finance),
    NAS("NAS", Icons.Filled.Storage, Icons.Outlined.Storage, LifeDestination.Nas),
    BOOKS("Books", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook, LifeDestination.Books),
    ROUTES("Routes", Icons.Filled.Navigation, Icons.Outlined.Navigation, LifeDestination.Routes),
    SMART_HOME("Smart home", Icons.Filled.Lightbulb, Icons.Outlined.Lightbulb, LifeDestination.SmartHome),
    PLANNER("Planner", Icons.Filled.AutoAwesomeMosaic, Icons.Outlined.AutoAwesomeMosaic, LifeDestination.Planner),
    CLOCK("Clock", Icons.Filled.Schedule, Icons.Outlined.Schedule, LifeDestination.Clock),
    FOCUS("Focus", Icons.Filled.Bolt, Icons.Outlined.Bolt, LifeDestination.Focus),
    MEMEX("Memex", Icons.Filled.Archive, Icons.Outlined.Archive, LifeDestination.Memex),
    MACROS("Macros", Icons.Filled.SmartToy, Icons.Outlined.SmartToy, LifeDestination.Macros),
    EVOLUTION("Evolution", Icons.Filled.Timeline, Icons.Outlined.Timeline, LifeDestination.Evolution),
    DOWNLOADER("Downloader", Icons.Filled.Download, Icons.Outlined.Download, LifeDestination.Downloader),
    PLANTS("Plants", Icons.Filled.LocalFlorist, Icons.Outlined.LocalFlorist, LifeDestination.Plants),
    NEWS("News", Icons.Filled.Newspaper, Icons.Outlined.Newspaper, LifeDestination.News),
    BRICK("Brick", Icons.Filled.Shield, Icons.Outlined.Shield, LifeDestination.Brick),
    SCREEN_TIME("Screen Time", Icons.Filled.Timelapse, Icons.Outlined.Timelapse, LifeDestination.ScreenTime),
    PASTEBIN("Pastebin", Icons.Filled.ContentPaste, Icons.Outlined.ContentPaste, LifeDestination.Pastebin),
    SKY("Clear Sky", Icons.Filled.NightsStay, Icons.Outlined.NightsStay, LifeDestination.ClearSky),
}
