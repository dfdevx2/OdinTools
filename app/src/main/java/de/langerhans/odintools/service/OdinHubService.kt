package de.langerhans.odintools.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.R
import de.langerhans.odintools.tools.hardware.PerformanceManager
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class OdinHubService : Service() {

    @Inject lateinit var performanceManager: PerformanceManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var lastForeground: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = buildNotification()

        // Correção para o Android 14+ (Exige declaração do tipo no código)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(32, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(32, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (pollJob?.isActive != true) {
            pollJob = serviceScope.launch { pollLoop() }
        }
        return START_STICKY
    }

    private suspend fun pollLoop() {
        // Colocamos o 'serviceScope.' antes do isActive
        while (serviceScope.isActive) {
            val foreground = currentForegroundPackage()
            if (foreground != null && foreground != lastForeground) {
                lastForeground = foreground
                // Aqui faremos a leitura do Perfil-por-App na próxima etapa
            }
            delay(1000)
        }
    }

    private fun currentForegroundPackage(): String? {
        val usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(now - 10000, now)
        var latest: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                latest = event.packageName
            }
        }
        return latest
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("odinhub_channel", "Odin Hub Monitor", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        channel.description = "Mantém o AutoTDP e os perfis rodando em segundo plano"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, "odinhub_channel")
        .setSmallIcon(R.drawable.ic_app_settings)
        .setContentTitle("Odin Hub: Performance")
        .setContentText("Motor ligado e monitorando...")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()
}
