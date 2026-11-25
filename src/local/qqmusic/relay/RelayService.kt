package local.qqmusic.relay

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class RelayService : Service() {
    enum class Phase { Connecting, Connected, Exiting }

    data class Status(val target: RelayTarget, val phase: Phase, val since: Long)

    private val handler = Handler(Looper.getMainLooper())
    private val links = linkedMapOf<String, Link>()
    private lateinit var targets: RelayTargets
    private var foreground = false
    private var receivedStart = false
    private val notifications get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        targets = RelayTargets(this)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!receivedStart) {
            receivedStart = true
            if (intent != null) targets.clearActive()
        }
        val restoreEnabled = RelayPreferences(this).restoreConnections
        when (intent?.action) {
            null -> {
                if (!restoreEnabled) targets.clearActive()
                val ids = targets.activeIds()
                if (ids.isNotEmpty()) RelayLogs.record(this, "Info", null, "Restoring connections after service restart")
                ids.forEach { id ->
                    val target = targets.find(id)
                    if (target == null) targets.setActive(id, false) else connect(target, null)
                }
            }
            ACTION_CONNECT -> {
                val target = targets.find(intent.getStringExtra(EXTRA_TARGET))
                val result = intent.getParcelableExtra(EXTRA_RESULT, ResultReceiver::class.java)
                if (target != null) connect(target, result) else {
                    if (!foreground) promote()
                    result?.send(Activity.RESULT_CANCELED, null)
                }
            }
            ACTION_STOP -> links[intent.getStringExtra(EXTRA_TARGET)]?.let(::requestExit)
            ACTION_DISCONNECT -> links[intent.getStringExtra(EXTRA_TARGET)]?.let(::disconnect)
        }
        if (links.isEmpty()) stopSelf()
        return if (links.isNotEmpty() && restoreEnabled) START_STICKY else START_NOT_STICKY
    }

    private fun connect(target: RelayTarget, result: ResultReceiver?) {
        val current = links[target.id]
        if (current != null) {
            when (current.phase) {
                Phase.Connected -> {
                    publish()
                    result?.send(Activity.RESULT_OK, null)
                }
                Phase.Connecting -> if (result != null) current.waiters.add(result)
                Phase.Exiting -> result?.send(RESULT_EXIT_IN_PROGRESS, null)
            }
            return
        }
        if (!targets.isBindable(target)) {
            // A foreground start still needs to be acknowledged even on a fast failure.
            if (!foreground) promote()
            targets.setActive(target.id, false)
            RelayLogs.record(this, "Error", target, "Service unavailable: ${target.serviceName}")
            result?.send(Activity.RESULT_CANCELED, null)
            return
        }
        val link = Link(target)
        if (result != null) link.waiters.add(result)
        links[target.id] = link
        if (!foreground) promote()
        publish()
        RelayLogs.record(this, "Info", target, "Binding to ${target.serviceName}")
        try {
            link.bound = bindService(target.bindIntent(), link, BIND_AUTO_CREATE or BIND_IMPORTANT)
            if (link.bound) handler.postDelayed(link.timeout, BIND_TIMEOUT_MS)
            else remove(link, "Error", "Android declined the service binding")
        } catch (failure: SecurityException) {
            remove(link, "Error", "Permission denied by the target service")
        }
    }

    private fun disconnect(link: Link) {
        remove(link, "Info", "Disconnected by user")
    }

    private fun requestExit(link: Link) {
        if (link.phase == Phase.Exiting) return
        if (!link.target.canExit || link.phase != Phase.Connected) {
            disconnect(link)
            return
        }
        targets.setActive(link.target.id, false)
        link.phase = Phase.Exiting
        publish()
        handler.postDelayed(link.timeout, EXIT_TIMEOUT_MS)
        val request = Intent(Intent.ACTION_VIEW, Uri.parse("qqmusicpad://qqmusicpad?action=1"))
            .setComponent(ComponentName(link.target.packageName,
                "com.tencent.qqmusicpad.app.reciver.BroadcastReceiverCenterForThird"))
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        try {
            sendBroadcast(request)
            RelayLogs.record(this, "Info", link.target, "Requested player exit")
        } catch (_: SecurityException) {
            remove(link, "Error", "Player exit request denied")
        }
    }

    private fun remove(link: Link, level: String, event: String) {
        if (links[link.target.id] !== link) return
        links.remove(link.target.id)
        targets.setActive(link.target.id, false)
        link.finish(Activity.RESULT_CANCELED)
        RelayLogs.record(this, level, link.target, event)
        publish()
        if (links.isEmpty()) stopSelf()
    }

    private fun promote() {
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        foreground = true
    }

    private fun publish() {
        statuses.value = links.values.map { Status(it.target, it.phase, it.since) }
        if (foreground && links.isNotEmpty()) notifications.notify(NOTIFICATION_ID, notification())
    }

    private fun notification(): Notification {
        val link = links.values.singleOrNull()
        val content = when {
            link?.phase == Phase.Connecting -> getString(R.string.notification_connecting, link.target.label)
            link?.phase == Phase.Exiting -> getString(R.string.notification_closing, link.target.label)
            link != null -> getString(R.string.notification_connected, link.target.label)
            links.isEmpty() -> getString(R.string.notification_starting)
            else -> getString(R.string.notification_multiple, links.size)
        }
        val manage = PendingIntent.getActivity(this, 0,
            Intent(this, RelayDashboard::class.java), PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setContentIntent(manage)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (link != null && link.phase != Phase.Exiting) {
            builder.addAction(connectionAction(getString(R.string.disconnect), disconnectIntent(this, link.target.id), link))
            if (link.target.canExit && link.phase == Phase.Connected) {
                builder.addAction(connectionAction(getString(R.string.stop), stopIntent(this, link.target.id), link))
            }
        }
        return builder.build()
    }

    private fun connectionAction(label: String, intent: Intent, link: Link): Notification.Action {
        intent.data = Uri.fromParts("relay", link.target.id, null)
        val action = PendingIntent.getService(this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Action.Builder(null, label, action).build()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (foreground && links.isNotEmpty()) notifications.notify(NOTIFICATION_ID, notification())
    }

    override fun onDestroy() {
        links.values.forEach { it.finish(Activity.RESULT_CANCELED) }
        links.clear()
        statuses.value = emptyList()
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private inner class Link(val target: RelayTarget) : ServiceConnection {
        var bound = false
        var phase = Phase.Connecting
        var since = System.currentTimeMillis()
        val waiters = mutableListOf<ResultReceiver>()
        val timeout = Runnable {
            val message = if (phase == Phase.Exiting) "Timed out waiting for player exit"
                else "Timed out waiting for the service connection"
            remove(this, "Error", message)
        }

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (links[target.id] !== this) return
            handler.removeCallbacks(timeout)
            phase = Phase.Connected
            since = System.currentTimeMillis()
            targets.setActive(target.id, true)
            RelayLogs.record(this@RelayService, "Info", target, "Connected to ${name.shortClassName}")
            publish()
            respond(Activity.RESULT_OK)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (phase == Phase.Exiting) remove(this, "Info", "Player exited; connection released")
            else remove(this, "Error", "Target service disconnected")
        }

        override fun onBindingDied(name: ComponentName) = remove(this, "Error", "Target service binding died")
        override fun onNullBinding(name: ComponentName) = remove(this, "Error", "Target service returned no binder")

        private fun respond(code: Int) {
            waiters.forEach { it.send(code, null) }
            waiters.clear()
        }

        fun finish(code: Int) {
            handler.removeCallbacks(timeout)
            respond(code)
            if (bound) {
                unbindService(this)
                bound = false
            }
        }
    }

    companion object {
        const val EXTRA_TARGET = "relay_target"
        const val EXTRA_RESULT = "connection_result"
        const val ACTION_STOP = "local.qqmusic.relay.STOP"
        private const val ACTION_DISCONNECT = "local.qqmusic.relay.DISCONNECT"
        const val RESULT_EXIT_IN_PROGRESS = Activity.RESULT_FIRST_USER
        private const val ACTION_CONNECT = "local.qqmusic.relay.CONNECT"
        const val CHANNEL_ID = "relay"
        private const val NOTIFICATION_ID = 1
        private const val BIND_TIMEOUT_MS = 3_500L
        private const val EXIT_TIMEOUT_MS = 5_000L
        private val statuses = MutableStateFlow<List<Status>>(emptyList())
        val connections = statuses.asStateFlow()

        fun connectIntent(context: Context, id: String, result: ResultReceiver) =
            Intent(context, RelayService::class.java).setAction(ACTION_CONNECT)
                .putExtra(EXTRA_TARGET, id).putExtra(EXTRA_RESULT, result)

        fun stopIntent(context: Context, id: String) = Intent(context, RelayService::class.java)
            .setAction(ACTION_STOP).putExtra(EXTRA_TARGET, id)

        fun disconnectIntent(context: Context, id: String) = Intent(context, RelayService::class.java)
            .setAction(ACTION_DISCONNECT).putExtra(EXTRA_TARGET, id)

        fun reloadSettings(context: Context) {
            if (connections.value.isNotEmpty()) context.startService(
                Intent(context, RelayService::class.java).setAction("local.qqmusic.relay.RELOAD_SETTINGS")
            )
        }
    }
}
