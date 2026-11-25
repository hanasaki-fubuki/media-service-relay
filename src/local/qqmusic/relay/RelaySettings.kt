package local.qqmusic.relay

import android.app.NotificationManager
import android.app.LocaleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
internal fun RelaySettings(
    wide: Boolean,
    theme: RelayTheme,
    onThemeChanged: (RelayTheme) -> Unit,
    playbackIndicators: Boolean,
    playbackControls: Boolean,
    onPlaybackOptionChanged: (PlaybackOption, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { RelayPreferences(context) }
    var restore by remember { mutableStateOf(preferences.restoreConnections) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = if (wide) 24.dp else 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (wide) item { Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall) }
        item {
            SettingGroup(stringResource(R.string.appearance)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        RelayTheme.entries.forEachIndexed { index, choice ->
                            SegmentedButton(
                                selected = theme == choice,
                                onClick = { onThemeChanged(choice) },
                                shape = SegmentedButtonDefaults.itemShape(index, RelayTheme.entries.size),
                            ) { Text(stringResource(choice.label)) }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                LanguageSetting()
            }
        }
        item {
            SettingGroup(stringResource(R.string.nav_connections)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.restore_connections)) },
                    supportingContent = { Text(stringResource(R.string.restore_connections_desc)) },
                    trailingContent = { Switch(checked = restore, onCheckedChange = null) },
                    modifier = Modifier.toggleable(restore, role = Role.Switch) { enabled ->
                        restore = enabled
                        preferences.restoreConnections = enabled
                        RelayService.reloadSettings(context)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                PlaybackOption.entries.forEach { option ->
                    val enabled = when (option) {
                        PlaybackOption.Spectrum -> playbackIndicators
                        PlaybackOption.Controls -> playbackControls
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text(stringResource(option.label)) },
                        supportingContent = { Text(stringResource(option.description)) },
                        trailingContent = { Switch(checked = enabled, onCheckedChange = null) },
                        modifier = Modifier.toggleable(enabled, role = Role.Switch) {
                            onPlaybackOptionChanged(option, it)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
        item {
            SettingGroup(stringResource(R.string.notifications)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.manage_notifications)) },
                    supportingContent = { Text(stringResource(R.string.manage_notifications_desc)) },
                    modifier = Modifier.clickable { openRelayNotifications(context) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
internal fun PlaybackAccessDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.allow_notification_access)) },
        text = { Text(stringResource(R.string.allow_notification_access_desc)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.open_android_settings)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) } },
    )
}

@Composable
private fun LanguageSetting() {
    val manager = LocalContext.current.getSystemService(LocaleManager::class.java)
    var selected by remember { mutableStateOf(RelayLanguage.current(manager)) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(manager) {
        selected = RelayLanguage.ensureSelection(manager)
        onPauseOrDispose { }
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.language)) },
        supportingContent = { Text(stringResource(selected.label)) },
        modifier = Modifier.clickable { choosing = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    RelayLanguage.entries.forEach { choice ->
                        ListItem(
                            headlineContent = { Text(stringResource(choice.label)) },
                            leadingContent = { RadioButton(selected = selected == choice, onClick = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.selectable(selected == choice, role = Role.RadioButton) {
                                choosing = false
                                selected = choice
                                choice.select(manager)
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleMedium)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(24.dp),
        ) { Column { content() } }
    }
}

private fun openRelayNotifications(context: Context) {
    val appSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    val manager = context.getSystemService(NotificationManager::class.java)
    val channel = manager.getNotificationChannel(RelayService.CHANNEL_ID)
    if (channel != null && tryOpen(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, RelayService.CHANNEL_ID), context::startActivity)) return
    if (!tryOpen(appSettings, context::startActivity)) {
        Toast.makeText(context, R.string.notification_settings_unavailable, Toast.LENGTH_SHORT).show()
    }
}

internal fun openPlaybackAccess(context: Context, launch: (Intent) -> Unit): Boolean {
    val details = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
        RelayMediaAccess.component(context).flattenToString(),
    )
    if (tryOpen(details, launch) || tryOpen(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), launch)) return true
    Toast.makeText(context, R.string.playback_access_unavailable, Toast.LENGTH_SHORT).show()
    return false
}

private fun tryOpen(intent: Intent, launch: (Intent) -> Unit): Boolean = try {
    launch(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
