package com.lifeos.feature.pastebin.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.lifeos.core.database.capture.CaptureDao
import com.lifeos.core.database.capture.CaptureEntity
import com.lifeos.core.designsystem.theme.LifeOsTheme
import com.lifeos.feature.pastebin.data.PastebinRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Share target (§Module Pastebin): a small floating chooser shown over whatever
 * app the text came from. Nothing else in LifeOS opens — pick "Log it" or
 * "Pastebin link" and the sheet disappears again.
 *
 * The transparent, non-fullscreen theme is what keeps the source app visible
 * behind the card.
 */
@AndroidEntryPoint
class ShareRouterActivity : FragmentActivity() {

    @Inject
    lateinit var captureDao: CaptureDao

    @Inject
    lateinit var pastebinRepository: PastebinRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = intent?.let { sharedText(it) }
        if (shared.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            LifeOsTheme {
                var busy by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        tonalElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(
                                "Shared to LifeOS",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                            Text(
                                shared.trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 2.dp, bottom = 8.dp),
                            )
                            if (busy) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                            }
                            ListItem(
                                modifier = Modifier.then(
                                    if (busy) Modifier else Modifier.clickableRow { busy = true; logIt(shared) },
                                ),
                                headlineContent = { Text("Log it") },
                                supportingContent = { Text("Save to the LifeOS capture inbox") },
                                leadingContent = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                            )
                            ListItem(
                                modifier = Modifier.then(
                                    if (busy) Modifier else Modifier.clickableRow { busy = true; toPastebin(shared) },
                                ),
                                headlineContent = { Text("Pastebin link") },
                                supportingContent = { Text("Create a paste with your share defaults and copy the link") },
                                leadingContent = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
                            )
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TextButton(onClick = { finish() }) { Text("Cancel") }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun logIt(text: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                captureDao.insertCapture(
                    CaptureEntity(
                        kind = "TEXT",
                        text = text,
                        blobVaultRef = null,
                        routedTo = null,
                        routedEntityId = null,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
            toastAndClose("Logged to LifeOS")
        }
    }

    private fun toPastebin(text: String) {
        lifecycleScope.launch {
            val title = text.lineSequence().firstOrNull()?.take(60)?.ifBlank { null } ?: "Shared to LifeOS"
            val result = pastebinRepository.createFromShare(title, text)
            result.onSuccess { url ->
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("pastebin", url))
                toastAndClose("Link copied: $url")
            }.onFailure {
                toastAndClose(it.message ?: "Could not create the paste")
            }
        }
    }

    private fun toastAndClose(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun sharedText(intent: Intent): String? = when (intent.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        else -> null
    }
}

/** Row-wide click without pulling in the experimental Surface overloads. */
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
