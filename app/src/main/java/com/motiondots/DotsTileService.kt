package com.motiondots

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile to toggle the dots without opening the app. */
class DotsTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        when {
            DotsService.running -> DotsService.stop(this)
            !DotsService.hasOverlayPermission(this) -> {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            else -> DotsService.start(this)
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.state = if (DotsService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
