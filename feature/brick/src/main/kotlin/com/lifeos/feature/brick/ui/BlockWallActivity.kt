package com.lifeos.feature.brick.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.lifeos.core.designsystem.theme.LifeOsTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The wall shown instead of a blocked app (§Module Brick). Deliberately a dead
 * end: no way back into the app, only "Home". Back is swallowed so hammering it
 * can't slip past the block.
 */
@AndroidEntryPoint
class BlockWallActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val profileName = intent.getStringExtra(EXTRA_PROFILE).orEmpty()
        val hint = intent.getStringExtra(EXTRA_HINT).orEmpty()

        // Swallow Back: leaving the wall must go Home, not back to the app.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        setContent {
            LifeOsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(72.dp),
                        )
                        Text(
                            "Blocked",
                            style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Text(
                            profileName.ifBlank { "A Brick mode is running" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            hint,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                        Button(
                            onClick = { goHome() },
                            modifier = Modifier.padding(top = 32.dp),
                        ) { Text("Home") }
                    }
                }
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    companion object {
        private const val EXTRA_PROFILE = "profile"
        private const val EXTRA_HINT = "hint"

        fun intent(context: Context, profileName: String, hint: String): Intent =
            Intent(context, BlockWallActivity::class.java)
                .putExtra(EXTRA_PROFILE, profileName)
                .putExtra(EXTRA_HINT, hint)
    }
}
