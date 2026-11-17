package com.sarim.trinity.widget

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sarim.trinity.R
import com.sarim.trinity.data.ScreeningPrefs

/**
 * Quick settings tile for the "screen unknown callers" setting owned by [ScreeningPrefs].
 * ACTIVE when screening is on, INACTIVE otherwise; a tap flips it. TileService is API 24,
 * below this app's minSdk (26), so no guarded path is needed.
 *
 * TileService has no onLongClick hook. Android implements tile long-press by launching
 * the activity that declares [TileService.ACTION_QS_TILE_PREFERENCES]; that is
 * [TrinityTilePreferencesActivity] below, which opens the same app-details Settings page
 * a long-press is meant to reach.
 */
class TrinityTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        runCatching {
            val settings = ScreeningPrefs.load(this)
            ScreeningPrefs.save(this, settings.copy(screenUnknown = !settings.screenUnknown))
            syncTile()
        }
    }

    private fun syncTile() {
        runCatching {
            val tile = qsTile ?: return@runCatching
            val on = ScreeningPrefs.load(this).screenUnknown
            tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = getString(if (on) R.string.tile_screening_on else R.string.tile_screening_off)
            tile.updateTile()
        }
    }
}

/**
 * Long-press target for the quick tile. A TileService cannot handle long-press itself:
 * SystemUI starts the app activity that declares the QS_TILE_PREFERENCES action.
 *
 * The destination is Android's app-details page for Trinity rather than the in-app
 * Settings screen. The in-app screen is a Compose Route reached through MainActivity,
 * which is outside this milestone's scope; the system page also exposes the
 * call-screening role this tile controls. Theme.NoDisplay keeps the hand-off invisible
 * and the activity finishes as soon as it has launched Settings.
 */
class TrinityTilePreferencesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        finish()
    }
}
