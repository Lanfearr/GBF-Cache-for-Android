package com.sagisawa.gbfaccelerator.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sagisawa.gbfaccelerator.R

class SettingsActivity : BasePageActivity() {

    private lateinit var tvNotificationStatus: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var tvVpnStatus: TextView
    private lateinit var btnNotification: Button
    private lateinit var btnVpn: Button

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateNotificationUi() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupPageHeader(R.string.title_settings)
        initViews()
    }

    override fun onResume() {
        super.onResume()
        updateNotificationUi()
        updateBatteryUi()
        updateVpnUi()
    }

    private fun initViews() {
        tvNotificationStatus = findViewById(R.id.tv_notification_status)
        tvBatteryStatus = findViewById(R.id.tv_battery_status)
        tvVpnStatus = findViewById(R.id.tv_vpn_status)
        btnNotification = findViewById(R.id.btn_notification)
        btnVpn = findViewById(R.id.btn_vpn)

        btnNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings()
            }
        }

        findViewById<Button>(R.id.btn_battery).setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        btnVpn.setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, R.string.settings_vpn_ready, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btn_app_settings).setOnClickListener {
            openAppSettings()
        }
    }

    private fun updateNotificationUi() {
        val enabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        tvNotificationStatus.setText(
            if (enabled) R.string.settings_granted else R.string.settings_notification_denied
        )
        btnNotification.setText(
            if (enabled) R.string.settings_open else R.string.settings_grant
        )
    }

    private fun updateBatteryUi() {
        val ignoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(packageName) == true
        } else {
            true
        }
        tvBatteryStatus.setText(
            if (ignoring) R.string.settings_battery_ok else R.string.settings_battery_pending
        )
    }

    private fun updateVpnUi() {
        val ready = VpnService.prepare(this) == null
        tvVpnStatus.setText(
            if (ready) R.string.settings_vpn_ready else R.string.settings_vpn_not_ready
        )
        btnVpn.setText(
            if (ready) R.string.settings_granted else R.string.settings_grant
        )
    }

    private fun openNotificationSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            }
            startActivity(intent)
        } catch (e: Throwable) {
            Toast.makeText(this, "鏃犳硶鎵撳紑閫氱煡璁剧疆: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Throwable) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (fallbackError: Throwable) {
                Toast.makeText(
                    this,
                    "鏃犳硶鎵撳紑鐢垫睜浼樺寲璁剧疆: ${fallbackError.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Throwable) {
            Toast.makeText(this, "鏃犳硶鎵撳紑搴旂敤璁剧疆: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

