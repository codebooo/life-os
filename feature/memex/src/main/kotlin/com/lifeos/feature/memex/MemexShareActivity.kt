package com.lifeos.feature.memex

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.lifeos.feature.memex.data.MemexRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Invisible share target: archives shared text/URLs and finishes (§Module 22). */
@AndroidEntryPoint
class MemexShareActivity : ComponentActivity() {

    @Inject
    lateinit var repository: MemexRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
        if (shared.isNullOrBlank()) {
            finish()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            repository.clip(shared, source = "SHARE")
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "Clipped to Memex", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
