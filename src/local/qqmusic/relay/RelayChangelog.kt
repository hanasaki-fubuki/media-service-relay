package local.qqmusic.relay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private data class ReleaseNote(val version: String, val changes: List<Int>)

private val releases = listOf(
    ReleaseNote("1.9", listOf(R.string.change_19_independent_playback)),
    ReleaseNote("1.8", listOf(R.string.change_18_playback_controls)),
    ReleaseNote("1.7", listOf(R.string.change_17_languages)),
    ReleaseNote("1.6", listOf(
        R.string.change_16_languages,
        R.string.change_16_buttons,
    )),
    ReleaseNote("1.5", listOf(
        R.string.change_15_controls,
    )),
    ReleaseNote("1.4.1", listOf(
        R.string.change_141_layout,
    )),
    ReleaseNote("1.4", listOf(
        R.string.change_14_spectrum,
        R.string.change_14_disconnect,
    )),
    ReleaseNote("1.3.1", listOf(
        R.string.change_131_switch,
    )),
    ReleaseNote("1.3", listOf(
        R.string.change_13_settings,
        R.string.change_13_logs,
        R.string.change_13_bars,
    )),
    ReleaseNote("1.2", listOf(
        R.string.change_12_changelog,
        R.string.change_12_drawer,
    )),
    ReleaseNote("1.1", listOf(
        R.string.change_11_guide,
        R.string.change_11_tablet,
    )),
    ReleaseNote("1.0", listOf(
        R.string.change_10_profiles,
        R.string.change_10_logs,
    )),
    ReleaseNote("0.6", listOf(
        R.string.change_06_notices,
    )),
    ReleaseNote("0.5", listOf(
        R.string.change_05_exit,
    )),
)

@Composable
internal fun RelayChangelog(wide: Boolean) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = if (wide) 24.dp else 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (wide) item { Text(stringResource(R.string.nav_changelog), style = MaterialTheme.typography.headlineSmall) }
        item(key = "history") { RelayHistory() }
        items(releases, key = ReleaseNote::version) { release ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                if (wide) {
                    Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        VersionLabel(release.version, Modifier.widthIn(min = 112.dp))
                        ReleaseChanges(release.changes)
                    }
                } else {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VersionLabel(release.version)
                        ReleaseChanges(release.changes)
                    }
                }
            }
        }
    }
}

@Composable
private fun RelayHistory() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.history_problem), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.history_predecessor), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.history_today), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun VersionLabel(version: String, modifier: Modifier = Modifier) {
    Text(stringResource(R.string.version_label, version), modifier = modifier, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ReleaseChanges(changes: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        changes.forEach { change ->
            Text(stringResource(change), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
