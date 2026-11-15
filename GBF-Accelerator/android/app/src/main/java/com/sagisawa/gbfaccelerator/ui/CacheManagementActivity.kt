package com.sagisawa.gbfaccelerator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import com.sagisawa.gbfaccelerator.R
import com.sagisawa.gbfaccelerator.core.AppPreferences
import com.sagisawa.gbfaccelerator.core.CacheStats
import com.sagisawa.gbfaccelerator.core.CoreManager
import org.json.JSONObject
import java.io.File

class CacheManagementActivity : BasePageActivity() {

    private lateinit var tvCacheSummary: TextView
    private lateinit var tvCachePath: TextView
    private lateinit var switchRamCache: SwitchCompat
    private lateinit var switchPrefetch: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cache_management)
        setupPageHeader(R.string.title_cache_management)
        initViews()
    }

    override fun onResume() {
        super.onResume()
        updateCacheSummary()
        updatePreferencesUi()
    }

    private fun initViews() {
        tvCacheSummary = findViewById(R.id.tv_cache_summary)
        tvCachePath = findViewById(R.id.tv_cache_path)
        switchRamCache = findViewById(R.id.switch_ram_cache)
        switchPrefetch = findViewById(R.id.switch_prefetch)

        findViewById<Button>(R.id.btn_refresh_cache).setOnClickListener {
            updateCacheSummary()
        }

        setupCacheActions()
        setupPreferences()
    }

    private fun updateCacheSummary() {
        Thread {
            val cacheDir = CacheStats.cacheDir(this)
            val (count, sizeBytes) = CacheStats.getStats(cacheDir)
            val sizeStr = CacheStats.formatSize(sizeBytes)
            runOnUiThread {
                tvCacheSummary.text = getString(R.string.cache_summary_format, count, sizeStr)
                tvCachePath.text = cacheDir.absolutePath
            }
        }.start()
    }

    private fun setupCacheActions() {
        findViewById<Button>(R.id.btn_open_cache_dir).setOnClickListener {
            val cacheDir = CacheStats.cacheDir(this)
            Thread {
                val (count, sizeBytes) = CacheStats.getStats(cacheDir)
                val sizeStr = CacheStats.formatSize(sizeBytes)
                val path = cacheDir.absolutePath

                runOnUiThread {
                    val summaryText = getString(R.string.cache_dir_summary, path, count, sizeStr)
                    AlertDialog.Builder(this)
                        .setTitle(R.string.cache_dir_dialog_title)
                        .setMessage(summaryText)
                        .setPositiveButton(R.string.cache_copy_path) { _, _ ->
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("GBF Cache Path", path)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(this, "缓存路径已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }
                        .setNeutralButton(R.string.cache_export_download) { _, _ ->
                            exportCacheToDownload(cacheDir)
                        }
                        .setNegativeButton("关闭", null)
                        .show()
                }
            }.start()
        }

        findViewById<Button>(R.id.btn_slim_cache).setOnClickListener {
            if (CoreManager.currentState != CoreManager.State.RUNNING) {
                Toast.makeText(this, "请先启动代理核心后再执行瘦身", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle(R.string.cache_slim_dialog_title)
                .setMessage(R.string.cache_slim_dialog_msg)
                .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                    CoreManager.slimCache { success, msg ->
                        if (success) {
                            Toast.makeText(this, "瘦身任务已在后台执行", Toast.LENGTH_SHORT).show()
                            tvCacheSummary.postDelayed({ updateCacheSummary() }, 3000)
                        } else {
                            Toast.makeText(this, "启动瘦身失败: $msg", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }

        findViewById<Button>(R.id.btn_clear_cache).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.cache_clear_dialog_title)
                .setMessage(R.string.cache_clear_dialog_msg)
                .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                    if (CoreManager.currentState == CoreManager.State.RUNNING) {
                        CoreManager.clearCache { success, deleted, freed ->
                            if (success) {
                                val freedStr = CacheStats.formatSize(freed)
                                Toast.makeText(
                                    this,
                                    "清空完成: 已删除 $deleted 个文件，释放 $freedStr",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(this, "清空缓存失败，请检查核心日志", Toast.LENGTH_SHORT).show()
                            }
                            updateCacheSummary()
                        }
                    } else {
                        clearCacheOffline()
                    }
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
    }

    private fun clearCacheOffline() {
        Thread {
            val cacheDir = CacheStats.cacheDir(this)
            var deleted = 0
            var freed = 0L
            if (cacheDir.exists()) {
                cacheDir.walkBottomUp().forEach { f ->
                    if (f.isFile) {
                        deleted++
                        freed += f.length()
                        f.delete()
                    } else if (f != cacheDir) {
                        f.delete()
                    }
                }
            }
            val freedStr = CacheStats.formatSize(freed)
            runOnUiThread {
                Toast.makeText(
                    this,
                    "清空完成: 已删除 $deleted 个文件，释放 $freedStr",
                    Toast.LENGTH_LONG
                ).show()
                updateCacheSummary()
            }
        }.start()
    }

    private fun exportCacheToDownload(cacheDir: File) {
        Thread {
            try {
                val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val targetDir = File(downloadDir, "GBF_Cache")
                targetDir.mkdirs()

                var copied = 0
                if (cacheDir.exists()) {
                    cacheDir.copyRecursively(targetDir, overwrite = true) { _, _ ->
                        OnErrorAction.SKIP
                    }
                    val (count, _) = CacheStats.getStats(targetDir)
                    copied = count
                }

                runOnUiThread {
                    Toast.makeText(
                        this,
                        "成功导出 $copied 个缓存文件到: ${targetDir.absolutePath}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    Toast.makeText(this, "导出缓存失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun updatePreferencesUi() {
        val ramPref = AppPreferences.isRamCacheEnabled(this)
        if (switchRamCache.isChecked != ramPref) {
            switchRamCache.isChecked = ramPref
        }
        val prefetchPref = AppPreferences.isPrefetchEnabled(this)
        if (switchPrefetch.isChecked != prefetchPref) {
            switchPrefetch.isChecked = prefetchPref
        }
    }

    private fun setupPreferences() {
        updatePreferencesUi()

        switchRamCache.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != AppPreferences.isRamCacheEnabled(this)) {
                AppPreferences.setRamCacheEnabled(this, isChecked)
                updateConfigJsonPreference("enable_ram_cache", isChecked)
                if (CoreManager.currentState == CoreManager.State.RUNNING) {
                    CoreManager.applyRuntimeConfig(mapOf("enable_ram_cache" to isChecked))
                }
            }
        }

        switchPrefetch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != AppPreferences.isPrefetchEnabled(this)) {
                AppPreferences.setPrefetchEnabled(this, isChecked)
                updateConfigJsonPreference("enable_prefetch", isChecked)
                if (CoreManager.currentState == CoreManager.State.RUNNING) {
                    CoreManager.applyRuntimeConfig(mapOf("enable_prefetch" to isChecked))
                }
            }
        }
    }

    private fun updateConfigJsonPreference(key: String, value: Any) {
        try {
            val configFile = File(filesDir, "config.json")
            val json = if (configFile.exists()) {
                JSONObject(configFile.readText())
            } else {
                JSONObject()
            }
            json.put(key, value)
            configFile.writeText(json.toString(2))
        } catch (e: Throwable) {
            Log.w("CacheManagement", "Failed to update config.json with $key=$value: ${e.message}")
        }
    }
}
