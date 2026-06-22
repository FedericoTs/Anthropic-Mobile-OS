package os.amos.shell.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import os.amos.shell.MainActivity
import os.amos.shell.R

/**
 * Hosts the [AgentRuntime] in a foreground service so background agents keep
 * running independently of the launcher UI, and the always-present notification
 * makes their activity legible (transparent agency).
 *
 * A [LifecycleService] so we can collect the agents flow on [lifecycleScope].
 */
class AgentService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground(notification(AgentRuntime.agents.value))

        lifecycleScope.launch {
            AgentRuntime.agents.collectLatest { agents ->
                notificationManager().notify(NOTIF_ID, notification(agents))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startInForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun notification(agents: List<Agent>): Notification {
        val running = agents.count { it.status == AgentStatus.RUNNING }
        val title = if (running == 0) "Agents ready" else "$running agent(s) running"
        val text = agents.firstOrNull { it.status == AgentStatus.RUNNING }?.lastAction
            ?: "${agents.size} agent(s) registered."

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.agent_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.agent_channel_desc) }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "amos_agents"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AgentService::class.java))
        }
    }
}
