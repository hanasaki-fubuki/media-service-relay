package local.qqmusic.relay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.json.JSONObject

internal data class RelayTarget(
    val service: ComponentName,
    val action: String?,
    val label: String,
    val processName: String = "",
) {
    val id: String get() = service.flattenToString()
    val packageName: String get() = service.packageName
    val serviceName: String get() = service.shortClassName
    val canExit: Boolean get() = service == RelayTargets.qqMusic.service
    val exposesMediaSession: Boolean get() = action in RelayTargets.mediaActions
    fun bindIntent() = Intent(action).setComponent(service)
}

internal class RelayTargets(context: Context) {
    private val pm = context.packageManager
    private val prefs = context.getSharedPreferences("relay_targets", Context.MODE_PRIVATE)

    fun saved(): List<RelayTarget> =
        (listOf(qqMusic) + prefs.all.keys.filter { it.startsWith("target:") }
            .mapNotNull { find(it.removePrefix("target:")) })
            .distinctBy { it.id }.sortedBy { it.label.lowercase() }

    fun find(id: String?): RelayTarget? {
        if (id == null) return null
        if (id == qqMusic.id) return qqMusic
        val component = ComponentName.unflattenFromString(id) ?: return null
        val json = prefs.getString("target:$id", null) ?: return null
        return runCatching {
            val spec = JSONObject(json)
            RelayTarget(component, spec.optString("action").ifEmpty { null },
                spec.getString("label"), spec.optString("process"))
        }.getOrNull()
    }

    fun save(target: RelayTarget) {
        val json = JSONObject().put("action", target.action.orEmpty())
            .put("label", target.label).put("process", target.processName)
        prefs.edit().putString("target:${target.id}", json.toString()).apply()
    }

    fun remove(target: RelayTarget) {
        if (!target.canExit) prefs.edit().remove("target:${target.id}").apply()
    }

    fun activeIds(): Set<String> = prefs.getStringSet("active", emptySet()).orEmpty().toSet()

    fun clearActive() {
        prefs.edit().remove("active").commit()
    }

    fun setActive(id: String, active: Boolean) {
        val ids = activeIds().toMutableSet()
        if (active) ids.add(id) else ids.remove(id)
        prefs.edit().putStringSet("active", ids).commit()
    }

    data class App(val packageName: String, val label: String, val process: String?)

    fun launchableApps(): List<App> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .filter { it.activityInfo.packageName != APP_PACKAGE }
            .map { App(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.activityInfo.processName) }
            .distinctBy(App::packageName)
            .sortedBy { it.label.lowercase() }
    }

    fun candidates(app: App): List<RelayTarget> {
        if (app.packageName == qqMusic.packageName) return listOf(qqMusic).filter(::isBindable)
        val packageInfo = try {
            pm.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(
                PackageManager.GET_SERVICES.toLong()))
        } catch (_: PackageManager.NameNotFoundException) {
            return emptyList()
        }
        val services = packageInfo.services.orEmpty().filter {
            it.exported && it.enabled && it.applicationInfo.enabled && it.permission == null
        }.associateBy { it.name }
        val standard = mediaActions.flatMap { action ->
            pm.queryIntentServices(Intent(action).setPackage(app.packageName), 0)
                .mapNotNull { result ->
                    val info = services[result.serviceInfo.name] ?: return@mapNotNull null
                    RelayTarget(ComponentName(app.packageName, info.name), action, app.label, info.processName)
                }
        }.distinctBy { it.id }.sortedBy { it.processName != app.process }
        val recommended = standard.map { it.id }.toSet()
        val other = services.values.map {
            RelayTarget(ComponentName(app.packageName, it.name), null, app.label, it.processName)
        }.filter { it.id !in recommended }.sortedWith(
            compareBy<RelayTarget> { it.processName != app.process }.thenBy { it.serviceName })
        return standard + other
    }

    fun isBindable(target: RelayTarget): Boolean = try {
        val service = pm.getServiceInfo(target.service, PackageManager.ComponentInfoFlags.of(0))
        service.exported && service.enabled && service.applicationInfo.enabled && service.permission == null
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        private const val APP_PACKAGE = "local.qqmusic.relay"
        val qqMusic = RelayTarget(
            ComponentName("com.tencent.qqmusicpad", "com.tencent.qqmusiccar.third.api.QQMusicApiService"),
            "com.tencent.qqmusicpad.third.api.QQMusicApiService", "QQ Music HD", "com.tencent.qqmusicpad",
        )
        val mediaActions = listOf(
            "androidx.media3.session.MediaSessionService",
            "androidx.media3.session.MediaLibraryService",
            "android.media.browse.MediaBrowserService",
        )
    }
}
