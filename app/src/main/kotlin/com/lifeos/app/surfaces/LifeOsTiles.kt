package com.lifeos.app.surfaces

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.lifeos.app.MainActivity
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionDispatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Quick-settings tiles (§Module Surfaces): the two things worth doing without
 * opening anything - capture a thought, and start a focus block.
 */
@AndroidEntryPoint
class QuickCaptureTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "LifeOS capture"
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_QUICK_CAPTURE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
    }

    companion object {
        const val EXTRA_QUICK_CAPTURE = "lifeos.quick_capture"
    }
}

/** Starts a 25-minute focus block straight from the shade. */
@AndroidEntryPoint
class FocusTileService : TileService() {

    @Inject
    lateinit var dispatcher: LifeActionDispatcher

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Focus 25 min"
            updateTile()
        }
    }

    override fun onClick() {
        scope.launch {
            dispatcher.dispatch(LifeAction.StartFocusTimer(25, SourceRef(LifeModule.ADHD, "tile")))
        }
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }
}
