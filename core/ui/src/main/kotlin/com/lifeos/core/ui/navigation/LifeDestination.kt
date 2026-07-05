package com.lifeos.core.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes (§1.3). Top-level destinations sit on the bottom
 * bar; everything else lives in the Home app-grid and is deep-linkable under
 * the `lifeos://` scheme.
 */
sealed interface LifeDestination {

    @Serializable
    data object Home : LifeDestination

    @Serializable
    data object Calendar : LifeDestination

    @Serializable
    data object Tasks : LifeDestination

    @Serializable
    data object Inbox : LifeDestination

    @Serializable
    data object Assistant : LifeDestination

    // App-grid destinations (reachable from Home, deep-linkable)
    @Serializable
    data object Notes : LifeDestination

    @Serializable
    data object Logger : LifeDestination

    @Serializable
    data object Packages : LifeDestination

    @Serializable
    data object Scan : LifeDestination

    @Serializable
    data object Finance : LifeDestination

    @Serializable
    data object Nas : LifeDestination

    @Serializable
    data object Books : LifeDestination

    @Serializable
    data object Routes : LifeDestination

    @Serializable
    data object SmartHome : LifeDestination

    @Serializable
    data object Planner : LifeDestination
}

const val DEEP_LINK_SCHEME = "lifeos"
