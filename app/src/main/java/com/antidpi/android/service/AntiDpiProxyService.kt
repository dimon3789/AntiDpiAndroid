package com.antidpi.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.antidpi.android.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ProxyState(
    val isRunning: Boolean = false,
    val host: String = "127.0.0.1",
    val port: Int = 1080,
    val activeStrategyName: String = "",
    val activeArgs: List<String> = emptyList(),
    val lastError: String? = null
)

/**
 * Фоновый ForegroundService без VpnService.
 * Запускает нативный бинарник ByeDPI (libciadpi.so из nativeLibraryDir)
 * в режиме локального SOCKS5-прокси на 127.0.0.1:1080.
 */
class AntiDpiProxyService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var process: Process? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopProxyProcess()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val host = intent.getStringExtra(EXTRA_HOST) ?: DEFAULT_HOST
                val port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)
                val strategyName = intent.getStringExtra(EXTRA_STRATEGY_NAME) ?: "Custom"
                val args = intent.getStringArrayListExtra(EXTRA_ARGS)?.toList().orEmpty()

                val notification = buildNotification(strategyName, host, port)
                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)

                startProxyProcess(host, port, strategyName, args)
            }
        }
        return START_STICKY
    }

    private fun startProxyProcess(
        host: String,
        port: Int,
        strategyName: String,
        strategyArgs: List<String>
    ) {
        stopProxyProcess()

        val binary = File(applicationInfo.nativeLibraryDir, BINARY_NAME)
        if (!binary.exists()) {
            val err = "Бинарник $BINARY_NAME не найден в ${applicationInfo.nativeLibraryDir}. " +
                "Соберите его через scripts/build_ciadpi_android.sh."
            appendLog("ERROR: $err")
            _state.value = ProxyState(isRunning = false, lastError = err)
            stopSelf()
            return
        }

        val command = buildList {
            add(binary.absolutePath)
            add("--ip")
            add(host)
            add("--port")
            add(port.toString())
            addAll(strategyArgs)
        }

        appendLog("Запуск: ${command.joinToString(" ")}")

        serviceScope.launch {
            try {
                val pb = ProcessBuilder(command)
                    .redirectErrorStream(true)
                val started = pb.start()
                process = started

                _state.value = ProxyState(
                    isRunning = true,
                    host = host,
                    port = port,
                    activeStrategyName = strategyName,
                    activeArgs = strategyArgs,
                    lastError = null
                )

                BufferedReader(InputStreamReader(started.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { appendLog("[ciadpi] $it") }
                    }
                }

                val exitCode = started.waitFor()
                appendLog("Процесс ciadpi завершен (код: $exitCode)")
                _state.value = _state.value.copy(isRunning = false)
            } catch (e: Exception) {
                appendLog("Ошибка запуска ciadpi: ${e.message}")
                _state.value = _state.value.copy(
                    isRunning = false,
                    lastError = e.message ?: "Неизвестная ошибка"
                )
            }
        }
    }

    private fun stopProxyProcess() {
        val current = process
        process = null
        if (current != null && current.isAlive) {
            current.destroy()
            if (!current.waitFor(1500, TimeUnit.MILLISECONDS)) {
                current.destroyForcibly()
            }
            appendLog("Прокси остановлен.")
        }
        _state.value = _state.value.copy(isRunning = false)
    }

    override fun onDestroy() {
        stopProxyProcess()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Anti-DPI SOCKS5 Proxy",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Локальный прокси ByeDPI для связки с VPN-клиентами"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(strategyName: String, host: String, port: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AntiDpiProxyService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Anti-DPI активен ($host:$port)")
            .setContentText("Стратегия: $strategyName")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stopIntent)
            .build()
    }

    companion object {
        const val BINARY_NAME = "libciadpi.so"
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 1080

        const val ACTION_START = "com.antidpi.android.action.START"
        const val ACTION_STOP = "com.antidpi.android.action.STOP"
        const val EXTRA_HOST = "extra_host"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_STRATEGY_NAME = "extra_strategy_name"
        const val EXTRA_ARGS = "extra_args"

        private const val CHANNEL_ID = "antidpi_proxy_channel"
        private const val NOTIFICATION_ID = 1080
        private const val MAX_LOG_LINES = 200

        private val _state = MutableStateFlow(ProxyState())
        val state: StateFlow<ProxyState> = _state.asStateFlow()

        private val _logs = MutableStateFlow<List<String>>(emptyList())
        val logs: StateFlow<List<String>> = _logs.asStateFlow()

        fun appendLog(message: String) {
            val updated = (_logs.value + message).takeLast(MAX_LOG_LINES)
            _logs.value = updated
        }

        fun clearLogs() {
            _logs.value = emptyList()
        }

        fun start(
            context: Context,
            strategyName: String,
            args: List<String>,
            host: String = DEFAULT_HOST,
            port: Int = DEFAULT_PORT
        ) {
            val intent = Intent(context, AntiDpiProxyService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_STRATEGY_NAME, strategyName)
                putStringArrayListExtra(EXTRA_ARGS, ArrayList(args))
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AntiDpiProxyService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
