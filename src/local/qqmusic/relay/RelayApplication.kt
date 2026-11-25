package local.qqmusic.relay

import android.app.Application
import android.app.LocaleManager

internal class RelayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RelayLanguage.ensureSelection(getSystemService(LocaleManager::class.java))
    }
}
