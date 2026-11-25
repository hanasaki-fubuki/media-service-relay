package local.qqmusic.relay

import android.content.Context
import androidx.annotation.StringRes

internal enum class RelayTheme(@StringRes val label: Int) {
    System(R.string.theme_system),
    Light(R.string.theme_light),
    Dark(R.string.theme_dark),
}

internal enum class PlaybackOption(@get:StringRes val label: Int, @get:StringRes val description: Int) {
    Spectrum(R.string.playback_indicators, R.string.playback_indicators_desc),
    Controls(R.string.playback_controls, R.string.playback_controls_desc),
}

internal class RelayPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("relay_settings", Context.MODE_PRIVATE)

    var theme: RelayTheme
        get() = RelayTheme.entries.firstOrNull {
            it.name == preferences.getString("theme", RelayTheme.System.name)
        } ?: RelayTheme.System
        set(value) { preferences.edit().putString("theme", value.name).apply() }

    var restoreConnections: Boolean
        get() = preferences.getBoolean("restore_connections", true)
        set(value) { preferences.edit().putBoolean("restore_connections", value).apply() }

    var playbackIndicators: Boolean
        get() = preferences.getBoolean("playback_indicators", false)
        set(value) { preferences.edit().putBoolean("playback_indicators", value).apply() }

    var playbackControls: Boolean
        get() = preferences.getBoolean("playback_controls", false)
        set(value) { preferences.edit().putBoolean("playback_controls", value).apply() }

    init {
        // Older versions only showed controls when the spectrum area was enabled.
        if (!preferences.contains("playback_controls")) playbackControls = playbackIndicators
    }
}
