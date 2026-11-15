package com.sagisawa.gbfaccelerator.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.sagisawa.gbfaccelerator.R
import com.sagisawa.gbfaccelerator.core.CoreManager
import com.sagisawa.gbfaccelerator.core.VpnController
import com.sagisawa.gbfaccelerator.patch.BrowserCatalog
import com.sagisawa.gbfaccelerator.patch.BrowserLauncher
import com.sagisawa.gbfaccelerator.patch.BrowserTarget
import com.sagisawa.gbfaccelerator.patch.DefaultBrowserLauncher
import com.sagisawa.gbfaccelerator.patch.InstalledBrowser
import com.sagisawa.gbfaccelerator.patch.LaunchResult
import java.util.Locale

class BrowserScopeActivity : BasePageActivity() {

    private lateinit var tvBrowserStatus: TextView
    private lateinit var tvRestartWarning: TextView
    private lateinit var llBrowserList: LinearLayout

    private val browserLauncher: BrowserLauncher = DefaultBrowserLauncher()
    private var availableBrowsers: List<InstalledBrowser> = emptyList()
    private val selectedBrowserPackages = linkedSetOf<String>()

    private val coreStateListener: (CoreManager.State) -> Unit = {
        runOnUiThread { updateRestartWarning() }
    }

    private val vpnListener: (VpnController.State, String?) -> Unit = { _, _ ->
        runOnUiThread { updateRestartWarning() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser_scope)
        setupPageHeader(R.string.title_browser_scope)
        initViews()

        CoreManager.addStateListener(coreStateListener)
        VpnController.addListener(vpnListener)
    }

    override fun onResume() {
        super.onResume()
        refreshBrowserList()
    }

    private fun initViews() {
        tvBrowserStatus = findViewById(R.id.tv_browser_status)
        tvRestartWarning = findViewById(R.id.tv_restart_warning)
        llBrowserList = findViewById(R.id.ll_browser_list)

        findViewById<Button>(R.id.btn_select_all).setOnClickListener {
            selectedBrowserPackages.clear()
            selectedBrowserPackages.addAll(availableBrowsers.map { it.packageName })
            BrowserCatalog.saveSelectedPackages(this, selectedBrowserPackages)
            renderBrowserList()
        }

        findViewById<Button>(R.id.btn_clear_selection).setOnClickListener {
            selectedBrowserPackages.clear()
            BrowserCatalog.saveSelectedPackages(this, selectedBrowserPackages)
            renderBrowserList()
        }

        findViewById<Button>(R.id.btn_refresh_browsers).setOnClickListener {
            refreshBrowserList(showToast = true)
        }

        findViewById<View>(R.id.card_ca_hint).setOnClickListener {
            startActivity(Intent(this, CertificateActivity::class.java))
        }
    }

    private fun refreshBrowserList(showToast: Boolean = false) {
        availableBrowsers = BrowserCatalog.discover(this)
        selectedBrowserPackages.clear()
        selectedBrowserPackages.addAll(
            BrowserCatalog.resolveSelectedPackages(this, availableBrowsers)
        )
        renderBrowserList()
        if (showToast) {
            Toast.makeText(this, R.string.browser_refreshed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderBrowserList() {
        llBrowserList.removeAllViews()
        if (availableBrowsers.isEmpty()) {
            tvBrowserStatus.setText(R.string.browser_none_installed)
            tvRestartWarning.visibility = View.GONE
            return
        }
        availableBrowsers.forEach { browser ->
            llBrowserList.addView(createBrowserRow(browser))
        }
        updateBrowserSummary()
    }

    private fun createBrowserRow(browser: InstalledBrowser): View {
        val row = layoutInflater.inflate(R.layout.item_browser_row, llBrowserList, false)
        val checkBox = row.findViewById<CheckBox>(R.id.cb_browser)
        val icon = row.findViewById<ImageView>(R.id.iv_browser_icon)
        val label = row.findViewById<TextView>(R.id.tv_browser_label)
        val packageName = row.findViewById<TextView>(R.id.tv_browser_package)
        val openButton = row.findViewById<Button>(R.id.btn_open_browser)

        checkBox.isChecked = browser.packageName in selectedBrowserPackages
        checkBox.contentDescription = browser.label
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedBrowserPackages.add(browser.packageName)
            } else {
                selectedBrowserPackages.remove(browser.packageName)
            }
            BrowserCatalog.saveSelectedPackages(this, selectedBrowserPackages)
            updateBrowserSummary()
        }

        icon.setImageDrawable(
            runCatching { packageManager.getApplicationIcon(browser.packageName) }.getOrNull()
        )
        label.text = browser.label
        packageName.text = browser.packageName
        openButton.setOnClickListener { launchBrowser(browser) }
        row.setOnClickListener { checkBox.toggle() }
        return row
    }

    private fun updateBrowserSummary() {
        if (availableBrowsers.isEmpty()) {
            tvBrowserStatus.setText(R.string.browser_none_installed)
            tvRestartWarning.visibility = View.GONE
            return
        }
        val installedPackages = availableBrowsers.mapTo(mutableSetOf()) { it.packageName }
        val selectedCount = selectedBrowserPackages.count { it in installedPackages }
        tvBrowserStatus.text = getString(
            R.string.browser_selected_count,
            selectedCount,
            availableBrowsers.size
        )
        updateRestartWarning()
    }

    private fun updateRestartWarning() {
        if (!::tvRestartWarning.isInitialized) return
        val running = CoreManager.currentState == CoreManager.State.RUNNING ||
            VpnController.state == VpnController.State.RUNNING ||
            VpnController.state == VpnController.State.STARTING
        tvRestartWarning.visibility = if (running) View.VISIBLE else View.GONE
    }

    private fun launchBrowser(browser: InstalledBrowser) {
        launchTargetBrowser(
            BrowserTarget(
                id = browser.packageName,
                name = browser.label,
                targetPackages = setOf(browser.packageName)
            )
        )
    }

    private fun launchTargetBrowser(target: BrowserTarget) {
        when (val result = browserLauncher.launch(this, target)) {
            is LaunchResult.Success -> Unit
            is LaunchResult.NotInstalled -> {
                Toast.makeText(
                    this,
                    String.format(Locale.US, "%s (%s) 未安装", target.name, result.packageName),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is LaunchResult.Failure -> {
                Toast.makeText(
                    this,
                    String.format(Locale.US, "启动失败: %s", result.reason),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        CoreManager.removeStateListener(coreStateListener)
        VpnController.removeListener(vpnListener)
        super.onDestroy()
    }
}
