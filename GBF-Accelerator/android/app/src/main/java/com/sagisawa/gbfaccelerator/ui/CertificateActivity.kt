package com.sagisawa.gbfaccelerator.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.sagisawa.gbfaccelerator.R
import com.sagisawa.gbfaccelerator.cert.CaCertManager

class CertificateActivity : BasePageActivity() {

    private lateinit var tvCaStatus: TextView
    private lateinit var tvCaSubject: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_certificate)
        setupPageHeader(R.string.title_certificate)
        tvCaStatus = findViewById(R.id.tv_ca_status)
        tvCaSubject = findViewById(R.id.tv_ca_subject)

        findViewById<Button>(R.id.btn_install_ca).setOnClickListener {
            val intent = CaCertManager.createInstallIntent(this)
            if (intent == null) {
                Toast.makeText(this, "CA 证书尚未生成，请先启动代理核心", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            try {
                startActivity(intent)
            } catch (e: Throwable) {
                Toast.makeText(this, "无法打开证书安装界面: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        findViewById<Button>(R.id.btn_export_ca).setOnClickListener {
            Thread {
                val (success, message) = CaCertManager.exportCaCertToDownloads(this)
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    if (success) updateCaUi()
                }
            }.start()
        }
    }

    override fun onResume() {
        super.onResume()
        updateCaUi()
    }

    private fun updateCaUi() {
        Thread {
            val info = CaCertManager.getCertInfo(this)
            runOnUiThread {
                when {
                    !info.exists -> {
                        tvCaStatus.setText(R.string.cert_status_none)
                        tvCaStatus.setTextColor(
                            ContextCompat.getColor(this, R.color.text_secondary)
                        )
                        tvCaSubject.text = ""
                    }
                    info.isTrustedInSystem -> {
                        tvCaStatus.setText(R.string.cert_status_trusted)
                        tvCaStatus.setTextColor(
                            ContextCompat.getColor(this, R.color.color_success)
                        )
                        tvCaSubject.text = getString(R.string.cert_subject_format, info.subject)
                    }
                    else -> {
                        tvCaStatus.setText(R.string.cert_status_untrusted)
                        tvCaStatus.setTextColor(
                            ContextCompat.getColor(this, R.color.color_warning)
                        )
                        tvCaSubject.text = getString(R.string.cert_subject_format, info.subject)
                    }
                }
            }
        }.start()
    }
}
