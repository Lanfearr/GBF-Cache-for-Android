package com.sagisawa.gbfaccelerator.ui

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.sagisawa.gbfaccelerator.R
import com.sagisawa.gbfaccelerator.core.CoreManager
import com.sagisawa.gbfaccelerator.core.CoreService
import com.sagisawa.gbfaccelerator.core.VpnController
import com.sagisawa.gbfaccelerator.patch.BrowserCatalog

class ProxyTileService : TileService() {

    private var isListening = false

    private val stateListener: (CoreManager.State) -> Unit = { updateTile() }
    private val vpnListener: (VpnController.State, String?) -> Unit = { _, _ -> updateTile() }

    override fun onStartListening() {
        super.onStartListening()
        if (!isListening) {
            isListening = true
            CoreManager.addStateListener(stateListener)
            VpnController.addListener(vpnListener)
        }
        updateTile()
    }

    override fun onStopListening() {
        if (isListening) {
            isListening = false
            CoreManager.removeStateListener(stateListener)
            VpnController.removeListener(vpnListener)
        }
        super.onStopListening()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        when (CoreManager.currentState) {
            CoreManager.State.RUNNING, CoreManager.State.STARTING -> {
                CoreService.stop(this)
                updateTile()
            }
            CoreManager.State.STOPPING -> Unit
            CoreManager.State.STOPPED, CoreManager.State.CRASHED -> startFromTile()
        }
    }

    private fun startFromTile() {
        val installed = BrowserCatalog.discover(this)
        if (installed.isEmpty()) {
            showToast(R.string.browser_none_installed)
            openApp()
            return
        }

        val selected = BrowserCatalog.resolveSelectedPackages(this, installed)
        if (selected.isEmpty()) {
            showToast(R.string.browser_none_selected)
            openApp()
            return
        }

        if (VpnService.prepare(this) != null) {
            showToast(R.string.qs_tile_vpn_permission)
            openApp()
            return
        }

        CoreService.start(this)
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_gbf)

        val coreState = CoreManager.currentState
        val vpnState = VpnController.state
        val active = coreState == CoreManager.State.RUNNING && vpnState == VpnController.State.RUNNING

        val subtitleRes = when {
            active -> R.string.qs_tile_subtitle_running
            coreState == CoreManager.State.STARTING ||
                (coreState == CoreManager.State.RUNNING && vpnState == VpnController.State.STARTING) ->
                R.string.qs_tile_subtitle_starting
            coreState == CoreManager.State.STOPPING -> R.string.qs_tile_subtitle_stopping
            coreState == CoreManager.State.CRASHED || vpnState == VpnController.State.ERROR ->
                R.string.qs_tile_subtitle_error
            else -> R.string.qs_tile_subtitle_stopped
        }

        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = getString(R.string.qs_tile_label)
            tile.subtitle = getString(subtitleRes)
        }
        tile.updateTile()
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
