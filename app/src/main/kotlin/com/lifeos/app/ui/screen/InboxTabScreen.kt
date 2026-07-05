package com.lifeos.app.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lifeos.feature.email.EmailRoute
import com.lifeos.feature.messagecenter.InboxRoute

/** Inbox tab: unified notifications + email (§1.3). */
@Composable
fun InboxTabScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Messages") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Email") })
        }
        if (selectedTab == 0) InboxRoute() else EmailRoute()
    }
}
