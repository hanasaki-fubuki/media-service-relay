package local.qqmusic.relay

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController as PlatformController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

// Android uses the user-enabled listener to authorize media-session queries.
// Relay never requests notification content from this service.
internal class RelayMediaAccess : NotificationListenerService() {
    companion object {
        fun component(context: Context) = ComponentName(context, RelayMediaAccess::class.java)

        fun hasAccess(context: Context): Boolean =
            context.getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(component(context))
    }
}

internal data class PlaybackTransport(
    val showPause: Boolean,
    val previous: (() -> Unit)?,
    val toggle: (() -> Unit)?,
    val next: (() -> Unit)?,
) {
    val hasActions: Boolean get() = previous != null || toggle != null || next != null
}

internal data class PlaybackCardState(
    val playing: Boolean = false,
    val transport: PlaybackTransport? = null,
)

@Composable
internal fun watchPlayback(target: RelayTarget): PlaybackCardState {
    val context = LocalContext.current
    var state by remember(target.id) { mutableStateOf(PlaybackCardState()) }
    LifecycleResumeEffect(target.id) {
        val watch = PlaybackWatch(context.applicationContext, target) { state = it }
        watch.start()
        onPauseOrDispose {
            watch.close()
            state = PlaybackCardState()
        }
    }
    return state
}

private class PlaybackWatch(
    private val context: Context,
    private val target: RelayTarget,
    private val onState: (PlaybackCardState) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val sessions = mutableMapOf<MediaSession.Token, PlatformSession>()
    private var listeningToSystem = false
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var closed = false

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { updateSessions(it.orEmpty()) }
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
                )) publish()
        }
    }

    fun start() {
        if (target.exposesMediaSession) connectMediaService()
        if (RelayMediaAccess.hasAccess(context)) {
            val component = RelayMediaAccess.component(context)
            try {
                manager.addOnActiveSessionsChangedListener(sessionListener, component, handler)
                listeningToSystem = true
                updateSessions(manager.getActiveSessions(component))
            } catch (_: SecurityException) {
                // Access can be revoked while this screen is opening.
            }
        }
    }

    private fun connectMediaService() {
        val pending = try {
            MediaController.Builder(context, SessionToken(context, target.service))
                .setListener(object : MediaController.Listener {
                    override fun onDisconnected(controller: MediaController) {
                        controller.removeListener(playerListener)
                        if (this@PlaybackWatch.controller === controller) this@PlaybackWatch.controller = null
                        publish()
                    }
                })
                .buildAsync()
        } catch (_: RuntimeException) {
            return
        }
        future = pending
        pending.addListener({
            if (!closed) {
                runCatching { pending.get() }.getOrNull()?.let { ready ->
                    controller = ready
                    ready.addListener(playerListener)
                    publish()
                }
            }
        }, context.mainExecutor)
    }

    private fun updateSessions(available: List<PlatformController>) {
        if (closed) return
        val matching = available.filter { it.packageName == target.packageName }.associateBy { it.sessionToken }
        (sessions.keys - matching.keys).forEach { token ->
            sessions.remove(token)?.let { it.controller.unregisterCallback(it.callback) }
        }
        matching.forEach { (token, session) ->
            if (!sessions.containsKey(token)) {
                val callback = object : PlatformController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = publish()

                    override fun onSessionDestroyed() {
                        sessions.remove(token)?.let { it.controller.unregisterCallback(it.callback) }
                        publish()
                    }
                }
                sessions[token] = PlatformSession(session, callback)
                session.registerCallback(callback, handler)
            }
        }
        publish()
    }

    private fun publish() {
        if (closed) return
        val states = listOfNotNull(controller?.takeIf { it.isConnected }?.let(::media3State)) +
            sessions.values.mapNotNull { platformState(it.controller) }
        val selected = states.firstOrNull { it.playing && it.transport != null }
            ?: states.firstOrNull { it.transport != null }
        onState(PlaybackCardState(states.any { it.playing }, selected?.transport))
    }

    private fun media3State(player: MediaController): PlaybackCardState {
        val showPause = player.playWhenReady && player.playbackState != Player.STATE_ENDED
        val transport = PlaybackTransport(
            showPause = showPause,
            previous = when {
                player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) -> player::seekToPreviousMediaItem
                player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS) -> player::seekToPrevious
                else -> null
            },
            toggle = if (player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
                if (showPause) player::pause else player::play
            } else null,
            next = when {
                player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) -> player::seekToNextMediaItem
                player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT) -> player::seekToNext
                else -> null
            },
        )
        return PlaybackCardState(player.isPlaying, transport.takeIf { it.hasActions })
    }

    private fun platformState(player: PlatformController): PlaybackCardState? {
        val state = player.playbackState ?: return null
        val actions = state.actions
        val controls = player.transportControls
        val playing = state.state == PlaybackState.STATE_PLAYING
        val showPause = playing || (state.state == PlaybackState.STATE_BUFFERING && state.playbackSpeed > 0)
        val transport = PlaybackTransport(
            showPause = showPause,
            previous = if (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) controls::skipToPrevious else null,
            toggle = when {
                showPause && actions and PlaybackState.ACTION_PAUSE != 0L -> controls::pause
                !showPause && actions and PlaybackState.ACTION_PLAY != 0L -> controls::play
                actions and PlaybackState.ACTION_PLAY_PAUSE != 0L -> {
                    {
                        player.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                        player.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                        Unit
                    }
                }
                else -> null
            },
            next = if (actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) controls::skipToNext else null,
        )
        return PlaybackCardState(playing, transport.takeIf { it.hasActions })
    }

    fun close() {
        closed = true
        if (listeningToSystem) manager.removeOnActiveSessionsChangedListener(sessionListener)
        sessions.values.forEach { it.controller.unregisterCallback(it.callback) }
        sessions.clear()
        controller?.removeListener(playerListener)
        future?.let(MediaController::releaseFuture)
    }

    private data class PlatformSession(val controller: PlatformController, val callback: PlatformController.Callback)
}
