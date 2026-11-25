package local.qqmusic.relay

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import java.util.UUID

internal class RelayShortcuts : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        if (manager.pinnedShortcuts.none { it.id == LEGACY_ID }) return
        val icon = appIcon(context, manager, RelayTargets.qqMusic) ?: return
        manager.updateShortcuts(listOf(ShortcutInfo.Builder(context, LEGACY_ID).setIcon(icon).build()))
    }

    companion object {
        const val ACTION_OPEN = "local.qqmusic.relay.OPEN_PLAYER"
        private const val LEGACY_ID = "qq-music-relay"

        fun requestPin(context: Context, target: RelayTarget, label: String): Boolean {
            val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
            if (!manager.isRequestPinShortcutSupported) return false
            val icon = appIcon(context, manager, target) ?: Icon.createWithResource(context, R.drawable.relay_icon)
            return manager.requestPinShortcut(ShortcutInfo.Builder(context, "relay-${UUID.randomUUID()}")
                .setShortLabel(label)
                .setIcon(icon)
                .setActivity(launcherComponent(context))
                .setIntent(Intent(ACTION_OPEN).setComponent(launcherComponent(context))
                    .putExtra(RelayService.EXTRA_TARGET, target.id))
                .build(), null)
        }

        private fun launcherComponent(context: Context) =
            ComponentName(context.packageName, "local.qqmusic.relay.RelayActivity")

        private fun appIcon(context: Context, manager: ShortcutManager, target: RelayTarget): Icon? {
            val drawable = try {
                context.packageManager.getApplicationIcon(target.packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                return null
            }
            if (drawable is BitmapDrawable) return Icon.createWithBitmap(drawable.bitmap)
            val size = minOf(manager.iconMaxWidth, manager.iconMaxHeight)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(bitmap))
            return Icon.createWithBitmap(bitmap)
        }
    }
}
