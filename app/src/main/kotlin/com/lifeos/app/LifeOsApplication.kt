package com.lifeos.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lifeos.core.database.LifeDatabase
import com.lifeos.core.places.PlaceEngine
import com.lifeos.core.recall.RecallIndex
import com.lifeos.feature.dhl.work.PackagePollWorker
import com.lifeos.feature.sync.data.BackupService
import com.lifeos.feature.triggers.data.TriggerEngine
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LifeOsApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var triggerEngine: TriggerEngine

    @Inject
    lateinit var placeEngine: PlaceEngine

    @Inject
    lateinit var recallIndex: RecallIndex

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        // A staged restore has to be applied before anything opens the database,
        // which is why this is the very first thing the process does.
        BackupService.applyStagedRestore(this, LifeDatabase.NAME)
        super.onCreate()
        PackagePollWorker.schedule(this)
        // Rules and place matching are the two things that must run whether or
        // not their screens were ever opened.
        placeEngine.start()
        triggerEngine.start()
        startupScope.launch {
            // Poll the state-ish triggers (Wi-Fi, battery) and keep the semantic
            // index warm, both well after start-up so nothing competes with it.
            delay(20_000)
            while (true) {
                runCatching { triggerEngine.poll() }
                runCatching { recallIndex.reindex() }
                delay(15 * 60_000L)
            }
        }
    }
}
