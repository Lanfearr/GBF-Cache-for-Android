package com.sagisawa.gbfaccelerator.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.github.kr328.clash.core.Clash
import com.sagisawa.gbfaccelerator.patch.BrowserCatalog
import com.sagisawa.gbfaccelerator.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetSocketAddress

class CoreService : VpnService() {

    companion object {
        private const val TAG = "GBF-ACC-Service"
        const val CHANNEL_ID = "gbf_accelerator_core_channel"
        const val NOTIFICATION_ID = 8124

        const val ACTION_START = "com.sagisawa.gbfaccelerator.ACTION_START"
        const val ACTION_STOP = "com.sagisawa.gbfaccelerator.ACTION_STOP"

        private const val TUN_GATEWAY = "172.19.0.1"
        private const val TUN_SUBNET_PREFIX = 30
        private const val TUN_PORTAL = "172.19.0.2"
        private const val TUN_MTU = 9000

        fun start(context: Context) {
            val intent = Intent(context, CoreService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Foreground service start failed: ${e.message}")
                CoreManager.logSystem("[-] VPN 服务启动失败: ${e.message}")
                VpnController.update(VpnController.State.ERROR, e.message ?: "VPN service start failed")
            }
        }

        fun stop(context: Context) {
            CoreManager.logSystem("[*] Stop requested")
            CoreManager.stopCore()
            val intent = Intent(context, CoreService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Throwable) {
                Log.w(TAG, "Explicit stop intent failed: ${e.message}")
                context.stopService(Intent(context, CoreService::class.java))
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private var isForeground = false
    private var isTunStarting = false
    private var isTunStarted = false

    private val stateListener: (CoreManager.State) -> Unit = { state ->
        updateNotification(state)
        when (state) {
            CoreManager.State.RUNNING -> startTun()
            CoreManager.State.STOPPED, CoreManager.State.CRASHED -> {
                if (!isTunStarting) {
                    stopTun()
                }
            }
            else -> Unit
        }
        if (state == CoreManager.State.STOPPED && isForeground && !isTunStarted) {
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "CoreService created")
        createNotificationChannel()
        CoreManager.addStateListener(stateListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.i(TAG, "onStartCommand action: $action")

        if (action == ACTION_STOP) {
            stopTun()
            CoreManager.stopCore()
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground(CoreManager.currentState)

        if (CoreManager.currentState == CoreManager.State.STOPPED || CoreManager.currentState == CoreManager.State.CRASHED) {
            CoreManager.startCore(applicationContext)
        } else if (CoreManager.currentState == CoreManager.State.RUNNING) {
            startTun()
        }

        return START_STICKY
    }

    private fun startTun() {
        if (isTunStarted || isTunStarting) return
        isTunStarting = true
        VpnController.update(VpnController.State.STARTING)

        scope.launch {
            try {
                val profileDir = File(filesDir, "mihomo")
                if (!profileDir.isDirectory && !profileDir.mkdirs()) {
                    throw IllegalStateException("Cannot create mihomo profile directory: ${profileDir.absolutePath}")
                }
                val configFile = File(profileDir, "config.yaml")
                assets.open("gbf-cache.yaml").use { input ->
                    configFile.outputStream().use { output -> input.copyTo(output) }
                }

                CoreManager.logSystem("[*] Loading mihomo configuration from ${profileDir.absolutePath}...")
                Clash.reset()
                Clash.clearOverride(Clash.OverrideSlot.Session)
                withTimeout(15000) {
                    Clash.load(profileDir).await()
                }
                CoreManager.logSystem("[+] mihomo configuration loaded")

                if (CoreManager.currentState != CoreManager.State.RUNNING) {
                    throw IllegalStateException("GBF core is not running")
                }

                val builder = Builder()
                    .setSession("GBF Cache")
                    .addAddress(TUN_GATEWAY, TUN_SUBNET_PREFIX)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer(TUN_PORTAL)
                    .setMtu(TUN_MTU)
                    .setBlocking(false)

                val selectedPackages = BrowserCatalog.resolveSelectedPackages(this@CoreService)
                var allowedCount = 0
                for (pkg in selectedPackages) {
                    try {
                        builder.addAllowedApplication(pkg)
                        allowedCount++
                        CoreManager.logSystem("[+] VPN allowlist: $pkg")
                    } catch (_: Throwable) {
                        CoreManager.logSystem("[*] VPN allowlist skipped (not installed): $pkg")
                    }
                }

                if (allowedCount == 0) {
                    throw IllegalStateException("No selected browser is installed")
                }

                val fd = builder.establish()?.detachFd()
                    ?: throw IllegalStateException("VPN establishment rejected by system")

                CoreManager.logSystem("[*] Starting TUN stack=gvisor...")
                Clash.startTun(
                    fd = fd,
                    stack = "gvisor",
                    gateway = "$TUN_GATEWAY/$TUN_SUBNET_PREFIX",
                    portal = TUN_PORTAL,
                    dns = TUN_PORTAL,
                    markSocket = { socketFd -> protect(socketFd) },
                    querySocketUid = ::queryUid
                )
                CoreManager.logSystem("[+] Clash.startTun returned")

                isTunStarted = true
                isTunStarting = false
                VpnController.update(VpnController.State.RUNNING)
                CoreManager.logSystem("[+] VPN TUN started (selected browsers only)")
                updateNotification(CoreManager.currentState)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start VPN", e)
                isTunStarting = false
                isTunStarted = false
                runCatching { Clash.stopTun() }
                val reason = e.message ?: e.javaClass.simpleName
                VpnController.update(VpnController.State.ERROR, reason)
                CoreManager.logSystem("[-] VPN 启动失败: $reason")
                CoreManager.stopCore()
                stopSelf()
            }
        }
    }

    private fun queryUid(
        protocol: Int,
        source: InetSocketAddress,
        target: InetSocketAddress
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
        return runCatching {
            connectivity?.getConnectionOwnerUid(protocol, source, target) ?: -1
        }.getOrDefault(-1)
    }

    private fun stopTun() {
        CoreManager.logSystem("[*] Stopping VPN TUN...")
        runCatching { Clash.stopTun() }
        runCatching { Clash.reset() }
        runCatching { Clash.clearOverride(Clash.OverrideSlot.Session) }
        isTunStarted = false
        isTunStarting = false
        if (VpnController.state != VpnController.State.ERROR) {
            VpnController.update(VpnController.State.STOPPED)
        }
        CoreManager.logSystem("[+] VPN TUN stopped")
    }

    private fun startAsForeground(state: CoreManager.State) {
        val notification = buildNotification(state)
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        isForeground = true
    }

    private fun updateNotification(state: CoreManager.State) {
        if (!isForeground) return
        val notification = buildNotification(state)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(state: CoreManager.State): Notification {
        val contentText = when (state) {
            CoreManager.State.RUNNING -> {
                if (isTunStarted) {
                    "VPN active | Proxy 127.0.0.1:8124 (PID ${CoreManager.currentMetrics.pid})"
                } else {
                    "Starting VPN | Proxy 127.0.0.1:8124 (PID ${CoreManager.currentMetrics.pid})"
                }
            }
            CoreManager.State.STARTING -> "Launching Go Core daemon..."
            CoreManager.State.STOPPING -> "Stopping Go Core..."
            CoreManager.State.STOPPED -> "Proxy is stopped"
            CoreManager.State.CRASHED -> "Proxy crashed or failed to start"
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GBF Cache Service")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(state == CoreManager.State.RUNNING || state == CoreManager.State.STARTING)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GBF Cache Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the local Go Core proxy active in background"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onRevoke() {
        CoreManager.logSystem("[*] VPN permission revoked by system")
        stopTun()
        CoreManager.stopCore()
        stopSelf()
        super.onRevoke()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        runCatching { Clash.forceGc() }
    }

    override fun onDestroy() {
        Log.i(TAG, "CoreService destroying, stopping VPN and CoreManager...")
        CoreManager.removeStateListener(stateListener)
        stopTun()
        CoreManager.stopCore()
        scope.cancel()
        isForeground = false
        super.onDestroy()
    }
}
