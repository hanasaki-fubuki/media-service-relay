package local.qqmusic.relay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.axion.compose.theme.AxionTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class RelayDashboard : ComponentActivity() {
    private var confirmExit by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (forwardShortcut(intent)) {
            finish()
            return
        }
        enableEdgeToEdge()
        confirmExit = intent.action == RelayService.ACTION_STOP
        setContent {
            val preferences = remember { RelayPreferences(this) }
            var theme by remember { mutableStateOf(preferences.theme) }
            val darkTheme = when (theme) {
                RelayTheme.System -> isSystemInDarkTheme()
                RelayTheme.Light -> false
                RelayTheme.Dark -> true
            }
            AxionTheme(darkTheme = darkTheme) {
                Dashboard(
                    exitRequested = confirmExit,
                    onExitHandled = { confirmExit = false },
                    theme = theme,
                    onThemeChanged = { theme = it; preferences.theme = it },
                    open = { startActivity(RelayLaunch.openIntent(this, it)) },
                    stop = { startService(RelayService.stopIntent(this, it.id)) },
                    disconnect = { startService(RelayService.disconnectIntent(this, it.id)) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!forwardShortcut(intent)) confirmExit = intent.action == RelayService.ACTION_STOP
    }

    private fun forwardShortcut(intent: Intent): Boolean {
        if (intent.action != RelayShortcuts.ACTION_OPEN) return false
        startActivity(Intent(intent).setClass(this, RelayLaunch::class.java))
        return true
    }
}

private enum class RelayPage(@StringRes val label: Int, @StringRes val title: Int, val icon: Int) {
    Connections(R.string.nav_connections, R.string.app_name, R.drawable.ic_connections),
    Logs(R.string.nav_logs, R.string.connection_logs, R.drawable.ic_logs),
    Changelog(R.string.nav_changelog, R.string.nav_changelog, R.drawable.ic_changelog),
    Settings(R.string.nav_settings, R.string.nav_settings, R.drawable.ic_settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    exitRequested: Boolean,
    onExitHandled: () -> Unit,
    theme: RelayTheme,
    onThemeChanged: (RelayTheme) -> Unit,
    open: (RelayTarget) -> Unit,
    stop: (RelayTarget) -> Unit,
    disconnect: (RelayTarget) -> Unit,
) {
    val context = LocalContext.current
    val catalog = remember { RelayTargets(context) }
    val preferences = remember(context) { RelayPreferences(context) }
    var playbackIndicators by remember { mutableStateOf(preferences.playbackIndicators) }
    var playbackControls by remember { mutableStateOf(preferences.playbackControls) }
    val setPlaybackOption: (PlaybackOption, Boolean) -> Unit = { option, enabled ->
        when (option) {
            PlaybackOption.Spectrum -> {
                playbackIndicators = enabled
                preferences.playbackIndicators = enabled
            }
            PlaybackOption.Controls -> {
                playbackControls = enabled
                preferences.playbackControls = enabled
            }
        }
    }
    var playbackAccessPrompt by rememberSaveable { mutableStateOf<PlaybackOption?>(null) }
    var playbackAccessRequest by rememberSaveable { mutableStateOf<PlaybackOption?>(null) }
    val playbackAccess = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val option = playbackAccessRequest
        playbackAccessRequest = null
        if (option != null && RelayMediaAccess.hasAccess(context)) setPlaybackOption(option, true)
    }
    LifecycleResumeEffect(context) {
        if ((playbackIndicators || playbackControls) && !RelayMediaAccess.hasAccess(context)) {
            if (playbackIndicators) setPlaybackOption(PlaybackOption.Spectrum, false)
            if (playbackControls) setPlaybackOption(PlaybackOption.Controls, false)
        }
        onPauseOrDispose { }
    }
    var profiles by remember { mutableStateOf(catalog.saved()) }
    val active by RelayService.connections.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(RelayPage.Connections) }
    val spectrum = spectrumControls(page == RelayPage.Connections && playbackIndicators, active)
    var logQuery by rememberSaveable { mutableStateOf("") }
    var picker by rememberSaveable { mutableStateOf(false) }
    var shortcutId by rememberSaveable { mutableStateOf<String?>(null) }
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var canNotify by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        == PackageManager.PERMISSION_GRANTED) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = it
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 840.dp && maxHeight >= 480.dp && maxWidth > maxHeight
        val drawer = rememberDrawerState(DrawerValue.Closed)
        LaunchedEffect(twoPane) {
            if (twoPane) drawer.snapTo(DrawerValue.Closed)
        }
        val dashboard: @Composable () -> Unit = {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(if (twoPane) R.string.app_name else page.title)) },
                        navigationIcon = {
                            if (!twoPane) {
                                IconButton(onClick = { scope.launch { drawer.open() } }) {
                                    Icon(painterResource(R.drawable.ic_navigation_menu), stringResource(R.string.open_navigation))
                                }
                            }
                        },
                    )
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { insets ->
                Row(Modifier.fillMaxSize().padding(insets)) {
                    if (twoPane) {
                        NavigationRail(Modifier.fillMaxHeight().width(96.dp), windowInsets = WindowInsets(0)) {
                            RelayPage.entries.forEach { destination ->
                                if (destination == RelayPage.Settings) Spacer(Modifier.weight(1f))
                                NavigationRailItem(
                                    selected = page == destination,
                                    onClick = { page = destination },
                                    icon = { Icon(painterResource(destination.icon), null) },
                                    label = { Text(stringResource(destination.label)) },
                                )
                                if (destination == RelayPage.Connections) {
                                    HorizontalDivider(Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                                }
                            }
                        }
                        VerticalDivider(Modifier.fillMaxHeight())
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when (page) {
                            RelayPage.Connections -> ConnectionsScreen(
                                twoPane = twoPane,
                                active = active,
                                profiles = profiles,
                                playbackIndicators = playbackIndicators,
                                playbackControls = playbackControls,
                                spectrum = spectrum,
                                open = open,
                                stop = stop,
                                disconnect = disconnect,
                                add = { picker = true },
                                shortcut = { shortcutId = it.id },
                                remove = { removeId = it.id },
                                enableNotifications = if (canNotify) null else {
                                    { notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                                },
                            )
                            RelayPage.Logs -> LogScreen(twoPane, logQuery, { logQuery = it }) {
                                scope.launch { snackbar.showSnackbar(it) }
                            }
                            RelayPage.Changelog -> RelayChangelog(twoPane)
                            RelayPage.Settings -> RelaySettings(
                                wide = twoPane,
                                theme = theme,
                                onThemeChanged = onThemeChanged,
                                playbackIndicators = playbackIndicators,
                                playbackControls = playbackControls,
                                onPlaybackOptionChanged = { option, enabled ->
                                    if (enabled && !RelayMediaAccess.hasAccess(context)) {
                                        playbackAccessPrompt = option
                                    } else {
                                        setPlaybackOption(option, enabled)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        if (twoPane) {
            dashboard()
        } else {
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet(drawerState = drawer) {
                        val select: (RelayPage) -> Unit = { destination ->
                            page = destination
                            scope.launch { drawer.close() }
                        }
                        Column(Modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 16.dp)) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                Text(stringResource(R.string.app_name), modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.titleLarge)
                                RelayPage.entries.filter { it != RelayPage.Settings }.forEach { destination ->
                                    RelayDrawerItem(destination, page, select)
                                    if (destination == RelayPage.Connections) {
                                        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                    }
                                }
                            }
                            RelayDrawerItem(RelayPage.Settings, page, select)
                        }
                    }
                },
                content = dashboard,
            )
        }
        BackHandler(enabled = !twoPane && (drawer.isOpen || drawer.targetValue == DrawerValue.Open)) {
            scope.launch { drawer.close() }
        }
    }
    playbackAccessPrompt?.let { option ->
        PlaybackAccessDialog(
            onDismiss = { playbackAccessPrompt = null },
            onConfirm = {
                playbackAccessPrompt = null
                playbackAccessRequest = option
                if (!openPlaybackAccess(context) { playbackAccess.launch(it) }) playbackAccessRequest = null
            },
        )
    }

    if (picker) {
        TargetPicker(catalog, onDismiss = { picker = false }) { target ->
            catalog.save(target)
            profiles = catalog.saved()
            RelayLogs.record(context, "Info", target, "Saved relay profile")
            picker = false
        }
    }
    catalog.find(shortcutId)?.let { target ->
        val defaultLabel = stringResource(R.string.default_shortcut_name, target.label)
        var label by rememberSaveable(target.id) { mutableStateOf(defaultLabel) }
        AlertDialog(
            onDismissRequest = { shortcutId = null },
            title = { Text(stringResource(R.string.save_shortcut_title)) },
            text = {
                OutlinedTextField(value = label, onValueChange = { label = it },
                    label = { Text(stringResource(R.string.shortcut_name)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(enabled = label.isNotBlank(), onClick = {
                    val requested = RelayShortcuts.requestPin(context, target, label.trim())
                    if (requested) RelayLogs.record(context, "Info", target, "Requested a Home screen shortcut")
                    else scope.launch { snackbar.showSnackbar(context.getString(R.string.shortcut_failed)) }
                    shortcutId = null
                }) { Text(stringResource(R.string.add_shortcut)) }
            },
            dismissButton = { TextButton(onClick = { shortcutId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    catalog.find(removeId)?.let { target ->
        AlertDialog(
            onDismissRequest = { removeId = null },
            title = { Text(stringResource(R.string.remove_relay_title, target.label)) },
            text = { Text(stringResource(R.string.remove_relay_message)) },
            confirmButton = {
                TextButton(onClick = {
                    catalog.remove(target)
                    profiles = catalog.saved()
                    RelayLogs.record(context, "Info", target, "Removed relay profile")
                    removeId = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { removeId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (exitRequested) {
        AlertDialog(
            onDismissRequest = onExitHandled,
            title = { Text(stringResource(R.string.exit_confirm_title)) },
            text = { Text(stringResource(R.string.exit_confirm_message)) },
            confirmButton = {
                Button(onClick = { stop(RelayTargets.qqMusic); onExitHandled() }) { Text(stringResource(R.string.stop)) }
            },
            dismissButton = { TextButton(onClick = onExitHandled) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun RelayDrawerItem(destination: RelayPage, page: RelayPage, select: (RelayPage) -> Unit) {
    NavigationDrawerItem(
        selected = page == destination,
        onClick = { select(destination) },
        label = { Text(stringResource(if (destination == RelayPage.Logs) destination.title else destination.label)) },
        icon = { Icon(painterResource(destination.icon), null) },
    )
}

@Composable
private fun ConnectionsScreen(
    twoPane: Boolean,
    active: List<RelayService.Status>,
    profiles: List<RelayTarget>,
    playbackIndicators: Boolean,
    playbackControls: Boolean,
    spectrum: SpectrumControls,
    open: (RelayTarget) -> Unit,
    stop: (RelayTarget) -> Unit,
    disconnect: (RelayTarget) -> Unit,
    add: () -> Unit,
    shortcut: (RelayTarget) -> Unit,
    remove: (RelayTarget) -> Unit,
    enableNotifications: (() -> Unit)?,
) {
    val activeIds = active.map { it.target.id }.toSet()
    if (twoPane) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AboutRelay(twoPane = true, add = add)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ConnectionPane(stringResource(R.string.active_connections), Modifier.weight(1f)) {
                    activeRows(active, playbackIndicators, playbackControls, spectrum, disconnect, stop, enableNotifications)
                }
                ConnectionPane(stringResource(R.string.saved_relays), Modifier.weight(1.2f)) {
                    savedRows(profiles, activeIds, open, shortcut, remove)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AboutRelay(twoPane = false, add = add) }
            item { SectionHeader(stringResource(R.string.active_connections)) }
            activeRows(active, playbackIndicators, playbackControls, spectrum, disconnect, stop, enableNotifications)
            item { SectionHeader(stringResource(R.string.saved_relays)) }
            savedRows(profiles, activeIds, open, shortcut, remove)
        }
    }
}

@Composable
private fun AboutRelay(twoPane: Boolean, add: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        if (twoPane) {
            Row(
                Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                AboutText(Modifier.weight(1f))
                Button(onClick = add) { Text(stringResource(R.string.add_app)) }
            }
        } else {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AboutText()
                Button(onClick = add) { Text(stringResource(R.string.add_app)) }
            }
        }
    }
}

@Composable
private fun AboutText(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.about_message),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.widthIn(max = 680.dp),
        )
    }
}

@Composable
private fun ConnectionPane(title: String, modifier: Modifier, rows: LazyListScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 17.dp, bottom = 12.dp)) {
                SectionHeader(title)
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = rows,
            )
        }
    }
}

private fun LazyListScope.activeRows(
    active: List<RelayService.Status>,
    playbackIndicators: Boolean,
    playbackControls: Boolean,
    spectrum: SpectrumControls,
    disconnect: (RelayTarget) -> Unit,
    stop: (RelayTarget) -> Unit,
    enableNotifications: (() -> Unit)?,
) {
    if (enableNotifications != null) item {
        TextButton(onClick = enableNotifications) { Text(stringResource(R.string.enable_connection_notifications)) }
    }
    if (active.isEmpty()) item {
        EmptyCard(stringResource(R.string.no_active_connections), stringResource(R.string.choose_saved_or_add))
    }
    items(active, key = { "active:${it.target.id}" }) { status ->
        val connected = status.phase == RelayService.Phase.Connected
        val showSpectrum = connected && playbackIndicators
        val playback = if (connected && (playbackIndicators || playbackControls)) watchPlayback(status.target)
            else PlaybackCardState()
        val transport = playback.transport.takeIf { playbackControls }
        val capture = spectrum.session?.takeIf { showSpectrum && it.targetId == status.target.id }
        RelayCard(
            target = status.target,
            detail = when (status.phase) {
                RelayService.Phase.Connected -> {
                    val statusLabel = when {
                        capture != null -> R.string.spectrum_since
                        playback.playing -> R.string.playing_since
                        else -> R.string.connected_since
                    }
                    stringResource(statusLabel, formatTime(status.since))
                }
                RelayService.Phase.Connecting -> stringResource(R.string.connecting)
                RelayService.Phase.Exiting -> stringResource(R.string.closing_app)
            },
            active = connected,
            trailing = if (showSpectrum || transport != null) {
                {
                    SpectrumCardControls(
                        spectrumEnabled = showSpectrum,
                        playing = playback.playing,
                        transport = transport,
                        capture = capture,
                        startEnabled = spectrum.canStart,
                        start = { spectrum.start(status.target) },
                        stop = spectrum.stop,
                    )
                }
            } else null,
        ) {
            if (status.phase != RelayService.Phase.Exiting) {
                OutlinedButton(onClick = { disconnect(status.target) }) { Text(stringResource(R.string.disconnect)) }
                if (status.target.canExit && connected) {
                    OutlinedButton(onClick = { stop(status.target) }) { Text(stringResource(R.string.stop)) }
                }
            }
        }
    }
}

@Composable
private fun SpectrumCardControls(
    spectrumEnabled: Boolean,
    playing: Boolean,
    transport: PlaybackTransport?,
    capture: SpectrumCaptureService.Session?,
    startEnabled: Boolean,
    start: () -> Unit,
    stop: () -> Unit,
) {
    val showBars = spectrumEnabled && (playing || capture != null)
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.animation.AnimatedVisibility(
            visible = showBars || transport != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                if (showBars) {
                    PlaybackBars(
                        modifier = Modifier.matchParentSize().padding(end = 4.dp),
                        spectrumBands = capture?.bands?.collectAsStateWithLifecycle(),
                    )
                }
                transport?.let { controls ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        controls.previous?.let {
                            PlaybackButton(R.drawable.ic_skip_previous, stringResource(R.string.previous_track), it)
                        }
                        controls.toggle?.let {
                            if (controls.showPause) {
                                PlaybackButton(R.drawable.ic_pause, stringResource(R.string.pause), it)
                            } else {
                                PlaybackButton(R.drawable.ic_play, stringResource(R.string.play), it)
                            }
                        }
                        controls.next?.let {
                            PlaybackButton(R.drawable.ic_skip_next, stringResource(R.string.next_track), it)
                        }
                    }
                }
            }
        }
        if (spectrumEnabled) {
            val capturing = capture != null
            TextButton(onClick = if (capturing) stop else start, enabled = capturing || startEnabled) {
                Text(
                    stringResource(if (capturing) R.string.stop_spectrum else R.string.start_spectrum),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PlaybackButton(icon: Int, description: String, action: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    IconButton(
        onClick = action,
        modifier = Modifier.size(48.dp).padding(3.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = colors.surface.copy(alpha = 0.19f),
            contentColor = colors.onSecondaryContainer.copy(alpha = 0.66f),
        ),
    ) { Icon(painterResource(icon), description) }
}

private fun LazyListScope.savedRows(
    profiles: List<RelayTarget>,
    activeIds: Set<String>,
    open: (RelayTarget) -> Unit,
    shortcut: (RelayTarget) -> Unit,
    remove: (RelayTarget) -> Unit,
) {
    items(profiles, key = { "saved:${it.id}" }) { target ->
        val connected = target.id in activeIds
        RelayCard(target, target.serviceName, active = false) {
            FilledTonalButton(onClick = { open(target) }) {
                Text(if (connected) stringResource(R.string.open_app) else stringResource(R.string.connect_and_open))
            }
            TextButton(onClick = { shortcut(target) }) { Text(stringResource(R.string.create_shortcut)) }
            if (!target.canExit && !connected) {
                TextButton(onClick = { remove(target) }) { Text(stringResource(R.string.remove)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun EmptyCard(title: String, message: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelayCard(
    target: RelayTarget,
    detail: String,
    active: Boolean,
    trailing: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit,
) {
    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor =
            if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
        val content: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(target.packageName)
                    Column(Modifier.weight(1f)) {
                        Text(target.label, style = MaterialTheme.typography.titleMedium)
                        Text(detail, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            }
        }
        if (trailing == null) {
            Box(Modifier.padding(16.dp)) { content() }
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
                val spectrumWidth = 176.dp
                val gap = 16.dp
                if (maxWidth >= 140.dp + gap + spectrumWidth) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        Box(Modifier.weight(1f)) { content() }
                        Box(Modifier.width(spectrumWidth)) { trailing() }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        content()
                        Box(Modifier.align(Alignment.End).widthIn(max = spectrumWidth)) { trailing() }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }
            .getOrNull()
    }
    if (icon != null) Image(icon, null, Modifier.size(40.dp))
    else Spacer(Modifier.size(40.dp))
}

@Composable
private fun TargetPicker(catalog: RelayTargets, onDismiss: () -> Unit, onSave: (RelayTarget) -> Unit) {
    val apps by produceState<List<RelayTargets.App>?>(null, catalog) {
        value = withContext(Dispatchers.IO) { catalog.launchableApps() }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var showOther by rememberSaveable { mutableStateOf(false) }
    val app = apps?.firstOrNull { it.packageName == selectedPackage }
    val candidates by produceState<List<RelayTarget>?>(null, app) {
        value = null
        app?.let { selection -> value = withContext(Dispatchers.IO) { catalog.candidates(selection) } }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 590.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(20.dp).heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(app?.label ?: stringResource(R.string.choose_app), style = MaterialTheme.typography.headlineSmall)
                if (app == null) {
                    OutlinedTextField(value = query, onValueChange = { query = it },
                        label = { Text(stringResource(R.string.search_apps)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    if (apps == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    val matching = apps.orEmpty().filter {
                        it.label.contains(query, true) || it.packageName.contains(query, true)
                    }
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(matching, key = { it.packageName }) { item ->
                            ListItem(
                                headlineContent = { Text(item.label) },
                                supportingContent = { Text(item.packageName) },
                                leadingContent = { AppIcon(item.packageName) },
                                modifier = Modifier.clickable { selectedPackage = item.packageName; showOther = false },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                        }
                        if (apps != null && matching.isEmpty()) item { Text(stringResource(R.string.no_matching_apps)) }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.cancel)) }
                } else {
                    val standard = candidates.orEmpty().filter { it.action != null }
                    val other = candidates.orEmpty().filter { it.action == null }
                    if (candidates == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else if (standard.isEmpty()) Text(stringResource(R.string.no_standard_service))
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(standard, key = { it.id }) { target -> ServiceOption(target, onSave) }
                        if (other.isNotEmpty()) item {
                            TextButton(onClick = { showOther = !showOther }) {
                                Text(stringResource(if (showOther) R.string.hide_other_services else R.string.show_other_services))
                            }
                        }
                        if (showOther) {
                            item { Text(stringResource(R.string.choose_playback_service),
                                style = MaterialTheme.typography.bodyMedium) }
                            items(other, key = { it.id }) { target -> ServiceOption(target, onSave) }
                        }
                        if (candidates?.isEmpty() == true) item { Text(stringResource(R.string.no_bindable_service)) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { selectedPackage = null }) { Text(stringResource(R.string.back)) }
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceOption(target: RelayTarget, onSave: (RelayTarget) -> Unit) {
    ListItem(headlineContent = { Text(target.serviceName) },
        supportingContent = {
            Text(target.action ?: target.processName, style = MaterialTheme.typography.bodySmall)
        },
        modifier = Modifier.clickable { onSave(target) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogScreen(
    twoPane: Boolean,
    query: String,
    onQueryChanged: (String) -> Unit,
    showMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { RelayLogs.export(context, uri) }.isSuccess
            }
            showMessage(context.getString(if (saved) R.string.logs_exported else R.string.logs_export_failed))
        }
    }
    val revision by RelayLogs.changes.collectAsStateWithLifecycle()
    val logs by produceState<Result<List<RelayLogs.Entry>>?>(null, query, revision) {
        value = withContext(Dispatchers.IO) { runCatching { RelayLogs.query(context, query) } }
    }
    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (twoPane) SectionHeader(stringResource(R.string.connection_logs))
        OutlinedTextField(value = query, onValueChange = onQueryChanged,
            label = { Text(stringResource(R.string.search_logs)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { export.launch("media-service-relay-logs.txt") }) {
                Text(stringResource(R.string.export_logs))
            }
            TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.clear_logs)) }
        }
        val entries = logs?.getOrNull().orEmpty()
        when {
            logs == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            logs?.isFailure == true -> EmptyCard(stringResource(R.string.logs_load_failed), stringResource(R.string.logs_retry))
            entries.isEmpty() -> EmptyCard(if (query.isBlank()) stringResource(R.string.no_events) else stringResource(R.string.no_matching_events),
                if (query.isBlank()) stringResource(R.string.events_appear) else stringResource(R.string.try_another_search))
            else -> {
                if (twoPane) LogTable(entries, Modifier.weight(1f).padding(bottom = 20.dp))
                else LazyColumn(Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries, key = { it.id }) { entry -> CompactLogEntry(entry) }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_logs_title)) },
            text = { Text(stringResource(R.string.clear_logs_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        val cleared = RelayLogs.clear(context)
                        showMessage(context.getString(if (cleared) R.string.logs_cleared else R.string.logs_clear_failed))
                    }
                }) { Text(stringResource(R.string.clear_logs)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun LogTable(entries: List<RelayLogs.Entry>, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                LogColumns {
                    Text(stringResource(R.string.log_time), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.log_app), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1.35f))
                    Text(stringResource(R.string.log_event), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(2.3f))
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(entries, key = { it.id }) { entry ->
                    LogColumns {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(formatTime(entry.time), style = MaterialTheme.typography.bodyMedium)
                            LogLevel(entry.level)
                        }
                        Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(entry.target.substringBefore(" · "), style = MaterialTheme.typography.bodyMedium)
                            val packageName = entry.target.substringAfter(" · ", "")
                            if (packageName.isNotEmpty()) Text(packageName, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(entry.event, modifier = Modifier.weight(2.3f), style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun LogColumns(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun LogLevel(level: String, time: Long? = null) {
    val label = when (level) {
        "Error" -> stringResource(R.string.log_error)
        "Info" -> stringResource(R.string.log_info)
        else -> level
    }
    val text = if (time == null) label else "${formatTime(time)}  ·  $label"
    Text(text, style = MaterialTheme.typography.labelMedium,
        color = if (level == "Error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
}

@Composable
private fun CompactLogEntry(entry: RelayLogs.Entry) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            LogLevel(entry.level, entry.time)
            Text(entry.event, style = MaterialTheme.typography.bodyLarge)
            Text(entry.target, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun formatTime(time: Long): String {
    val locale = RelayLanguage.displayLocale(LocalConfiguration.current.locales)
    val zone = ZoneId.systemDefault()
    val formatter = remember(locale, zone) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMdHms"), locale)
            .withZone(zone)
    }
    return formatter.format(Instant.ofEpochMilli(time))
}
