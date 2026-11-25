package local.qqmusic.relay

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class SpectrumCaptureService : Service() {
    data class Session(val targetId: String, val bands: StateFlow<FloatArray>)

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)
    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    @Volatile private var reading = false
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { stopSelf() }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (projection != null) return START_NOT_STICKY
        val targetId = intent?.getStringExtra(RelayService.EXTRA_TARGET)
        val target = RelayService.connections.value.firstOrNull {
            it.target.id == targetId && it.phase == RelayService.Phase.Connected
        }?.target
        val consent = intent?.getParcelableExtra(EXTRA_CONSENT, Intent::class.java)
        if (consent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTIFICATION_ID, notification(target?.label), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            if (target == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            val mediaProjection = getSystemService(MediaProjectionManager::class.java)
                .getMediaProjection(Activity.RESULT_OK, consent)
                ?: error("Android did not provide playback capture access")
            projection = mediaProjection
            mediaProjection.registerCallback(projectionCallback, main)
            val uid = packageManager.getApplicationInfo(target.packageName, 0).uid
            startReading(mediaProjection, target, uid)
            scope.launch {
                RelayService.connections.collect { connections ->
                    if (connections.none { it.target.id == target.id && it.phase == RelayService.Phase.Connected }) {
                        stopSelf()
                    }
                }
            }
        } catch (failure: Exception) {
            captureFailed(target, failure.message ?: failure.javaClass.simpleName)
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startReading(mediaProjection: MediaProjection, target: RelayTarget, uid: Int) {
        val sampleRate = AudioSpectrum.SAMPLE_RATE
        val channelMask = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val format = AudioFormat.Builder().setEncoding(encoding)
            .setSampleRate(sampleRate).setChannelMask(channelMask).build()
        val capture = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUid(uid)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val audio = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding),
                AudioSpectrum.FRAME_SIZE * Short.SIZE_BYTES * 4))
            .setAudioPlaybackCaptureConfig(capture)
            .build()
        try {
            audio.startRecording()
            check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "AudioRecord did not start" }
        } catch (failure: RuntimeException) {
            audio.release()
            throw failure
        }
        recorder = audio
        reading = true
        val frames = MutableStateFlow(FloatArray(AudioSpectrum.BAND_COUNT))
        Thread({
            val spectrum = AudioSpectrum()
            val pcm = ShortArray(AudioSpectrum.FRAME_SIZE)
            try {
                var offset = 0
                while (reading) {
                    val count = audio.read(pcm, offset, pcm.size - offset, AudioRecord.READ_BLOCKING)
                    if (!reading) break
                    check(count > 0) { "AudioRecord read failed ($count)" }
                    offset += count
                    if (offset == pcm.size) {
                        frames.value = spectrum.bands(pcm)
                        offset = 0
                    }
                }
            } catch (failure: RuntimeException) {
                main.post {
                    if (reading) captureFailed(target, failure.message ?: "Reading audio failed")
                }
            } finally {
                audio.release()
            }
        }, "Relay playback spectrum").start()
        current.value = Session(target.id, frames.asStateFlow())
    }

    private fun captureFailed(target: RelayTarget?, reason: String) {
        RelayLogs.record(this, "Error", target, "Playback audio capture failed: $reason")
        Toast.makeText(this, R.string.capture_failed, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    private fun notification(label: String?): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.live_spectrum), NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(this, 0,
            Intent(this, SpectrumCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.live_spectrum))
            .setContentText(if (label == null) getString(R.string.capture_starting) else getString(R.string.capture_analyzing, label))
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.stop_spectrum), stop).build())
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        reading = false
        // Stop the blocking read; the reader releases AudioRecord in its finally block.
        runCatching { recorder?.stop() }
        projection?.let {
            it.unregisterCallback(projectionCallback)
            it.stop()
        }
        current.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "local.qqmusic.relay.STOP_SPECTRUM"
        private const val EXTRA_CONSENT = "projection_consent"
        private const val CHANNEL_ID = "spectrum"
        private const val NOTIFICATION_ID = 2
        private val current = MutableStateFlow<Session?>(null)
        val session = current.asStateFlow()

        fun start(context: Context, targetId: String, consent: Intent) {
            context.startForegroundService(Intent(context, SpectrumCaptureService::class.java)
                .putExtra(RelayService.EXTRA_TARGET, targetId)
                .putExtra(EXTRA_CONSENT, consent))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SpectrumCaptureService::class.java))
        }
    }
}
