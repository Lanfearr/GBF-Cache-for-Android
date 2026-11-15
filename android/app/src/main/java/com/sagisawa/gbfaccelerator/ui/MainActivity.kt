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
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sagisawa.gbfaccelerator.R
import com.sagisawa.gbfaccelerator.core.CacheStats
import com.sagisawa.gbfaccelerator.core.CoreManager
import com.sagisawa.gbfaccelerator.core.CoreService
import com.sagisawa.gbfaccelerator.core.VpnController
import com.sagisawa.gbfaccelerator.patch.BrowserCatalog

class MainActivity : AppCompatActivity() {

    private lateinit var cardCore: LinearLayout
    private lateinit var ivCoreIcon: ImageView
    private lateinit var tvCoreTitle: TextView
    private lateinit var tvCoreSubtitle: TextView
    private lateinit var tvCoreDetail: TextView

    private lateinit var cardMetrics: LinearLayout
    private lateinit var tvMetricPid: TextView
    private lateinit var tvMetricRamCache: TextView
    private lateinit var tvMetricHits: TextView
    private lateinit var tvMetricRequests: TextView
    private lateinit var tvMetricReuse: TextView

    private lateinit var tvBrowserSummary: TextView
    private lateinit var tvCacheSummary: TextView

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            CoreService.start(this)
        } else {
            Toast.makeText(this, "需要 VPN 权限才能启动代理", Toast.LENGTH_LONG).show()
        }
    }

    private val stateListener: (CoreManager.State) -> Unit = { state ->
        runOnUiThread { updateStateUi(state) }
    }

    private val vpnListener: (VpnController.State, String?) -> Unit = { _, _ ->
        runOnUiThread { updateVpnUi() }
    }

    private val metricsListener: (CoreManager.CoreMetrics) -> Unit = { metrics ->
        runOnUiThread { updateMetricsUi(metrics) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        setContentView(R.layout.activity_main)

        initViews()

        CoreManager.addStateListener(stateListener)
        CoreManager.addMetricsListener(metricsListener)
        VpnController.addListener(vpnListener)

        updateCacheSummary()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        updateBrowserSummary()
        updateCacheSummary()
        updateStateUi(CoreManager.currentState)
        updateVpnUi()
        updateMetricsUi(CoreManager.currentMetrics)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun initViews() {
        cardCore = findViewById(R.id.card_core)
        ivCoreIcon = findViewById(R.id.iv_core_icon)
        tvCoreTitle = findViewById(R.id.tv_core_title)
        tvCoreSubtitle = findViewById(R.id.tv_core_subtitle)
        tvCoreDetail = findViewById(R.id.tv_core_detail)

        cardMetrics = findViewById(R.id.card_metrics)
        tvMetricPid = findViewById(R.id.tv_metric_pid)
        tvMetricRamCache = findViewById(R.id.tv_metric_ram_cache)
        tvMetricHits = findViewById(R.id.tv_metric_hits)
        tvMetricRequests = findViewById(R.id.tv_metric_requests)
        tvMetricReuse = findViewById(R.id.tv_metric_reuse)

        tvBrowserSummary = findViewById(R.id.tv_browser_summary)
        tvCacheSummary = findViewById(R.id.tv_cache_summary)

        cardCore.setOnClickListener { toggleCore() }
        findViewById<View>(R.id.card_browser).setOnClickListener {
            startActivity(Intent(this, BrowserScopeActivity::class.java))
        }
        findViewById<View>(R.id.card_cache).setOnClickListener {
            startActivity(Intent(this, CacheManagementActivity::class.java))
        }
        findViewById<View>(R.id.btn_logs).setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }
        findViewById<View>(R.id.btn_certificate).setOnClickListener {
            startActivity(Intent(this, CertificateActivity::class.java))
        }
        findViewById<View>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btn_help).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
        findViewById<View>(R.id.btn_about).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun toggleCore() {
        when (CoreManager.currentState) {
            CoreManager.State.RUNNING, CoreManager.State.STARTING -> CoreService.stop(this)
            CoreManager.State.STOPPED, CoreManager.State.CRASHED -> requestStartVpn()
            CoreManager.State.STOPPING -> Unit
        }
    }

    private fun requestStartVpn() {
        val installedBrowsers = BrowserCatalog.discover(this)
        if (installedBrowsers.isEmpty()) {
            Toast.makeText(this, R.string.browser_none_installed, Toast.LENGTH_LONG).show()
            return
        }

        val installedPackages = installedBrowsers.mapTo(mutableSetOf()) { it.packageName }
        val selectedPackages = BrowserCatalog.resolveSelectedPackages(this, installedBrowsers)
        if (selectedPackages.none { it in installedPackages }) {
            Toast.makeText(this, R.string.browser_none_selected, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, BrowserScopeActivity::class.java))
            return
        }

        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            CoreService.start(this)
        }
    }

    private fun updateBrowserSummary() {
        val browsers = BrowserCatalog.discover(this)
        if (browsers.isEmpty()) {
            tvBrowserSummary.setText(R.string.browser_none_installed)
            return
        }
        val installedPackages = browsers.mapTo(mutableSetOf()) { it.packageName }
        val selectedCount = BrowserCatalog.resolveSelectedPackages(this, browsers)
            .count { it in installedPackages }
        tvBrowserSummary.text = getString(
            R.string.browser_selected_count,
            selectedCount,
            browsers.size
        )
    }

    private fun updateCacheSummary() {
        Thread {
            val cacheDir = CacheStats.cacheDir(this)
            val (count, sizeBytes) = CacheStats.getStats(cacheDir)
            val sizeStr = CacheStats.formatSize(sizeBytes)
            runOnUiThread {
                tvCacheSummary.text = getString(R.string.cache_summary_format, count, sizeStr)
            }
        }.start()
    }

    private fun updateStateUi(state: CoreManager.State) {
        val titleRes = when (state) {
            CoreManager.State.STOPPED -> R.string.core_state_stopped
            CoreManager.State.STARTING -> R.string.core_state_starting
            CoreManager.State.RUNNING -> R.string.core_state_running
            CoreManager.State.CRASHED -> R.string.core_state_crashed
            CoreManager.State.STOPPING -> R.string.core_state_stopping
        }
        val hintRes = when (state) {
            CoreManager.State.STOPPED -> R.string.core_hint_start
            CoreManager.State.STARTING -> R.string.core_hint_starting
            CoreManager.State.RUNNING -> R.string.core_hint_stop
            CoreManager.State.CRASHED -> R.string.core_hint_retry
            CoreManager.State.STOPPING -> R.string.core_hint_stopping
        }
        val tintRes = when (state) {
            CoreManager.State.RUNNING -> R.color.color_success
            CoreManager.State.STARTING, CoreManager.State.STOPPING -> R.color.color_warning
            CoreManager.State.CRASHED -> R.color.color_error
            CoreManager.State.STOPPED -> R.color.text_secondary
        }

        tvCoreTitle.setText(titleRes)
        tvCoreSubtitle.setText(hintRes)
        ivCoreIcon.setColorFilter(ContextCompat.getColor(this, tintRes))
        cardCore.isEnabled = state != CoreManager.State.STOPPING
        cardCore.alpha = if (cardCore.isEnabled) 1f else 0.6f
        cardMetrics.visibility = if (state == CoreManager.State.RUNNING) {
            View.VISIBLE
        } else {
            View.GONE
        }

        when (state) {
            CoreManager.State.CRASHED -> tvCoreDetail.setText(R.string.home_core_fail_closed)
            CoreManager.State.RUNNING -> updateVpnUi()
            else -> tvCoreDetail.setText(R.string.home_core_idle_detail)
        }

        updateCacheSummary()
    }

    private fun updateVpnUi() {
        if (CoreManager.currentState != CoreManager.State.RUNNING) return

        when (VpnController.state) {
            VpnController.State.RUNNING -> {
                tvCoreSubtitle.setText(R.string.home_core_vpn_active)
                tvCoreDetail.setText(R.string.home_core_vpn_detail)
            }
            VpnController.State.STARTING -> {
                tvCoreSubtitle.setText(R.string.home_core_vpn_starting)
                tvCoreDetail.setText(R.string.home_core_vpn_detail)
            }
            VpnController.State.STOPPED -> {
                tvCoreSubtitle.setText(R.string.home_core_vpn_stopped)
                tvCoreDetail.setText(R.string.home_core_idle_detail)
            }
            VpnController.State.ERROR -> {
                val error = VpnController.lastError ?: "未知错误"
                tvCoreSubtitle.text = getString(R.string.home_core_vpn_error, error)
                tvCoreDetail.text = getString(R.string.home_core_vpn_error_detail, error)
            }
        }
    }

    private fun updateMetricsUi(metrics: CoreManager.CoreMetrics) {
        tvMetricPid.text = if (metrics.isRunning) {
            getString(R.string.metrics_pid_format, metrics.pid, metrics.uptimeSeconds)
        } else {
            getString(R.string.metrics_pid_placeholder)
        }
        tvMetricRamCache.text = getString(
            R.string.metric_ram_format,
            metrics.ramItems,
            metrics.ramMb
        )
        tvMetricHits.text = getString(
            R.string.metric_hits_format,
            metrics.ramHits,
            metrics.diskHits
        )
        tvMetricRequests.text = getString(
            R.string.metric_requests_format,
            metrics.totalAssets,
            metrics.totalApis
        )
        tvMetricReuse.text = getString(
            R.string.metric_reuse_format,
            metrics.reusedConnections,
            metrics.reuseRatePercent
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        checkBatteryOptimizations()
    }

    private fun checkBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Throwable) {
                    Log.w("MainActivity", "Cannot launch battery optimization settings: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        CoreManager.removeStateListener(stateListener)
        CoreManager.removeMetricsListener(metricsListener)
        VpnController.removeListener(vpnListener)
        super.onDestroy()
    }
}
