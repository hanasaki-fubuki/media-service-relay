package local.qqmusic.relay

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal class SpectrumControls(
    val session: SpectrumCaptureService.Session?,
    val canStart: Boolean,
    val start: (RelayTarget) -> Unit,
    val stop: () -> Unit,
)

@Composable
internal fun spectrumControls(available: Boolean, active: List<RelayService.Status>): SpectrumControls {
    val context = LocalContext.current
    val session by SpectrumCaptureService.session.collectAsStateWithLifecycle()
    var confirmingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(available) {
        onPauseOrDispose {
            if (available) SpectrumCaptureService.stop(context)
        }
    }

    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingId
        pendingId = null
        val consent = result.data
        if (available && id != null && result.resultCode == Activity.RESULT_OK && consent != null &&
            RelayService.connections.value.any { it.target.id == id && it.phase == RelayService.Phase.Connected }) {
            try {
                SpectrumCaptureService.start(context, id, consent)
            } catch (_: IllegalStateException) {
                Toast.makeText(context, R.string.capture_open_connections, Toast.LENGTH_SHORT).show()
            } catch (_: SecurityException) {
                Toast.makeText(context, R.string.capture_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val requestProjection = {
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        projection.launch(manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()))
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && available && pendingId != null) requestProjection() else pendingId = null
    }

    val target = active.firstOrNull {
        it.target.id == confirmingId && it.phase == RelayService.Phase.Connected
    }?.target
    if (available && target != null) {
        AlertDialog(
            title = { Text(stringResource(R.string.capture_confirm_title)) },
            text = { Text(stringResource(R.string.capture_confirm_desc, target.label)) },
            onDismissRequest = { confirmingId = null },
            dismissButton = { TextButton(onClick = { confirmingId = null }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                TextButton(onClick = {
                    confirmingId = null
                    pendingId = target.id
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        requestProjection()
                    } else {
                        audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }) { Text(stringResource(R.string.continue_action)) }
            },
        )
    }
    return SpectrumControls(
        session = session,
        canStart = available && session == null && pendingId == null,
        start = { confirmingId = it.id },
        stop = { SpectrumCaptureService.stop(context) },
    )
}
