package com.lifeos.feature.brick.nfc

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.lifeos.core.designsystem.theme.LifeOsTheme
import com.lifeos.feature.brick.data.BrickRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The tap target (§Module Brick): scanning a paired tag opens this, flips the
 * matching mode, shows a toast and closes — the whole interaction is one tap,
 * like Foqos on iOS. Works from the launcher or over another app.
 */
@AndroidEntryPoint
class BrickNfcActivity : FragmentActivity() {

    @Inject
    lateinit var brickRepository: BrickRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LifeOsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "Reading Brick tag…",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val tagId = intent?.tagUid()
        if (tagId == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            brickRepository.refresh()
            val message = brickRepository.onTagScanned(tagId)
            Toast.makeText(this@BrickNfcActivity, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }
}

