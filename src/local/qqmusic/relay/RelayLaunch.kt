package local.qqmusic.relay

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.os.Bundle
import android.widget.Toast

internal class RelayLaunch : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { finishLaunch(RESULT_CANCELED) }
    private var entered = false
    private var completed = false
    private var player: Intent? = null

    override fun onPostResume() {
        super.onPostResume()
        if (entered) return
        entered = true
        when (intent.action) {
            RelayShortcuts.ACTION_OPEN -> {
                val id = intent.getStringExtra(RelayService.EXTRA_TARGET) ?: RelayTargets.qqMusic.id
                RelayTargets(this).find(id)?.let(::connect) ?: run {
                    Toast.makeText(this, R.string.profile_unavailable, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            else -> {
                startActivity(Intent(this, RelayDashboard::class.java))
                finish()
            }
        }
    }

    private fun connect(target: RelayTarget) {
        player = packageManager.getLaunchIntentForPackage(target.packageName)
        if (player == null) {
            RelayLogs.record(this, "Error", target, "App has no available launch activity")
            Toast.makeText(this, R.string.app_cannot_open, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val result = object : ResultReceiver(handler) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                finishLaunch(resultCode)
            }
        }
        handler.postDelayed(timeout, 4_500)
        try {
            startForegroundService(RelayService.connectIntent(this, target.id, result))
        } catch (failure: IllegalStateException) {
            RelayLogs.record(this, "Error", target, "Android did not allow the relay to start")
            finishLaunch(RESULT_CANCELED)
        } catch (failure: SecurityException) {
            RelayLogs.record(this, "Error", target, "Android denied foreground service access")
            finishLaunch(RESULT_CANCELED)
        }
    }

    private fun finishLaunch(code: Int) {
        if (completed || isFinishing || isDestroyed) return
        completed = true
        handler.removeCallbacks(timeout)
        if (code == RelayService.RESULT_EXIT_IN_PROGRESS) {
            Toast.makeText(this, R.string.player_closing, Toast.LENGTH_SHORT).show()
        } else {
            if (code != RESULT_OK) Toast.makeText(this, R.string.relay_connect_failed, Toast.LENGTH_LONG).show()
            try {
                player?.let(::startActivity)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.app_cannot_open, Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        super.onDestroy()
    }

    companion object {
        fun openIntent(context: Context, target: RelayTarget) =
            Intent(context, RelayLaunch::class.java).setAction(RelayShortcuts.ACTION_OPEN)
                .putExtra(RelayService.EXTRA_TARGET, target.id)
    }
}
