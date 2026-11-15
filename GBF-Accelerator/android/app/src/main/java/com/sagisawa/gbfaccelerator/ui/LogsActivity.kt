package com.sagisawa.gbfaccelerator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.sagisawa.gbfaccelerator.R
import com.sagisawa.gbfaccelerator.core.CoreManager

class LogsActivity : BasePageActivity() {

    private lateinit var svLogs: ScrollView
    private lateinit var tvLogContent: TextView
    private var pendingExportContent: String = ""

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            writeExport(uri)
        }
    }

    private val logListener: (String) -> Unit = { line ->
        runOnUiThread { appendLog(line) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)
        setupPageHeader(R.string.title_logs)
        initViews()
        renderLogs()
        CoreManager.addLogListener(logListener)
    }

    private fun initViews() {
        svLogs = findViewById(R.id.sv_logs)
        tvLogContent = findViewById(R.id.tv_log_content)

        findViewById<View>(R.id.tv_clear_logs).setOnClickListener {
            CoreManager.clearLogs()
            tvLogContent.setText(R.string.logs_empty)
        }

        findViewById<View>(R.id.tv_copy_logs).setOnClickListener {
            val text = currentLogText()
            if (text.isEmpty()) {
                Toast.makeText(this, R.string.logs_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("GBF Core Logs", text))
            Toast.makeText(this, R.string.logs_copied, Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.tv_export_logs).setOnClickListener {
            val text = currentLogText()
            if (text.isEmpty()) {
                Toast.makeText(this, R.string.logs_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pendingExportContent = text
            exportLauncher.launch(getString(R.string.logs_export_filename))
        }
    }

    private fun renderLogs() {
        val logs = CoreManager.getLogs()
        tvLogContent.text = if (logs.isEmpty()) {
            getString(R.string.logs_empty)
        } else {
            logs.joinToString("\n")
        }
        scrollToBottom()
    }

    private fun appendLog(line: String) {
        if (line.isBlank()) {
            renderLogs()
            return
        }
        val emptyText = getString(R.string.logs_empty)
        val current = tvLogContent.text?.toString().orEmpty()
        val normalized = if (current == emptyText) "" else current
        tvLogContent.text = if (normalized.isEmpty()) line else "$normalized\n$line"
        scrollToBottom()
    }

    private fun currentLogText(): String {
        val text = tvLogContent.text?.toString().orEmpty()
        return if (text == getString(R.string.logs_empty)) "" else text
    }

    private fun scrollToBottom() {
        svLogs.post {
            svLogs.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun writeExport(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(pendingExportContent)
            } ?: throw IllegalStateException("无法打开输出文件")
            Toast.makeText(this, R.string.logs_export_success, Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            Toast.makeText(
                this,
                getString(R.string.logs_export_failed, e.message ?: "未知错误"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        CoreManager.removeLogListener(logListener)
        super.onDestroy()
    }
}
