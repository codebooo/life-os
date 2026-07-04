package com.lifeos.app.ui.screen

import androidx.compose.runtime.Composable
import com.lifeos.core.designsystem.component.EmptyState

@Composable
fun PlaceholderScreen(
    title: String,
    description: String,
) {
    EmptyState(title = title, description = description)
}
