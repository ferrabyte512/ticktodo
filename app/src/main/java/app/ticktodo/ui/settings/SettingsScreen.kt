package app.ticktodo.ui.settings

import android.Manifest
import android.content.Intent
import androidx.core.net.toUri
import android.text.format.DateUtils
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.ticktodo.data.settings.AppTheme
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import app.ticktodo.ui.theme.AccentPresets
import app.ticktodo.ui.theme.label
import app.ticktodo.ui.theme.seed
import app.ticktodo.ui.common.syncErrorHelp
import app.ticktodo.ui.common.syncErrorTitle
import app.ticktodo.ui.focus.PlanEditor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ticktodo.BuildConfig
import app.ticktodo.R
import app.ticktodo.data.settings.ThemeMode
import app.ticktodo.ui.common.ConfirmDialog
import app.ticktodo.ui.common.ReminderPermissions
import app.ticktodo.ui.common.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenAppearance: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val uriHandler = LocalUriHandler.current
    var notificationsOk by remember { mutableStateOf(true) }
    var exactOk by remember { mutableStateOf(true) }
    var showFocusPlan by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        notificationsOk = ReminderPermissions.notificationsAllowed(context)
        exactOk = ReminderPermissions.exactAlarmsAllowed(context)
        viewModel.onPermissionsMaybeChanged()
        onPauseOrDispose { }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsOk = it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                // Account
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.AccountCircle, null) },
                    headlineContent = { Text(state.user?.displayName ?: stringResource(R.string.settings_account)) },
                    supportingContent = { state.user?.email?.let { Text(it) } },
                )

                // Sync
                SectionTitle(stringResource(R.string.settings_sync))
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Sync, null) },
                    headlineContent = {
                        Text(
                            when {
                                state.sync.running -> stringResource(R.string.sync_running)
                                state.sync.pending > 0 -> pluralStringResource(R.plurals.sync_pending, state.sync.pending, state.sync.pending)
                                state.sync.lastSuccessAt != null -> stringResource(
                                    R.string.sync_last,
                                    DateUtils.getRelativeTimeSpanString(state.sync.lastSuccessAt!!, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                                )
                                else -> stringResource(R.string.sync_never)
                            },
                        )
                    },
                    trailingContent = { TextButton(onClick = viewModel::syncNow, enabled = !state.sync.running) { Text(stringResource(R.string.action_sync_now)) } },
                )
                state.sync.error?.let { error ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(syncErrorTitle(error), style = MaterialTheme.typography.titleSmall)
                            Text(syncErrorHelp(error), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                            TextButton(onClick = viewModel::syncNow, enabled = !state.sync.running) { Text(stringResource(R.string.action_retry)) }
                        }
                    }
                }

                // Reminder health
                SectionTitle(stringResource(R.string.settings_reminders))
                Text(
                    stringResource(R.string.reminder_health_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HealthItem(
                    icon = Icons.Outlined.Notifications,
                    title = stringResource(R.string.health_notifications),
                    ok = notificationsOk,
                    okText = stringResource(R.string.health_allowed),
                    problemText = stringResource(R.string.health_notifications_off),
                    onFix = {
                        if (ReminderPermissions.canRequestNotificationsAtRuntime()) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else runCatching { context.startActivity(ReminderPermissions.notificationSettingsIntent(context)) }
                    },
                )
                HealthItem(
                    icon = Icons.Outlined.Alarm,
                    title = stringResource(R.string.health_exact),
                    ok = exactOk,
                    okText = stringResource(R.string.health_allowed),
                    problemText = stringResource(R.string.health_exact_off),
                    onFix = { ReminderPermissions.exactAlarmSettingsIntent(context)?.let { runCatching { context.startActivity(it) } } },
                )

                // Focus timer
                SectionTitle(stringResource(R.string.focus_section))
                ListItem(
                    modifier = Modifier.clickable { showFocusPlan = true },
                    leadingContent = { Icon(Icons.Outlined.Timer, null) },
                    headlineContent = { Text(stringResource(R.string.settings_default_focus_plan)) },
                    supportingContent = {
                        val p = state.settings.defaultFocusPlan
                        Text(stringResource(R.string.focus_plan_summary, p.focusMinutes, p.breakMinutes))
                    },
                )

                // Appearance
                SectionTitle(stringResource(R.string.settings_appearance))
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenAppearance),
                    leadingContent = { Icon(Icons.Outlined.Palette, null) },
                    headlineContent = { Text(stringResource(R.string.settings_theme)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                when (state.settings.themeMode) {
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                    ThemeMode.LIGHT -> R.string.theme_light
                                    ThemeMode.DARK -> R.string.theme_dark
                                },
                            ),
                        )
                    },
                    trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
                )
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Colorize, null) },
                    headlineContent = { Text(stringResource(R.string.appearance_accent)) },
                    supportingContent = {
                        val a = state.settings.appearance
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            AccentPresets.take(5).forEach { theme ->
                                val chosen = a.customAccent == null && a.accent == theme && !state.settings.dynamicColor
                                val label = stringResource(theme.label)
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(theme.seed))
                                        .then(if (chosen) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                        .clickable { viewModel.setAppTheme(theme) }
                                        .semantics { role = Role.RadioButton; selected = chosen; contentDescription = label },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (chosen) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                            TextButton(onClick = onOpenAppearance) { Text(stringResource(R.string.appearance_more)) }
                        }
                    },
                )

                // About & legal
                SectionTitle(stringResource(R.string.settings_about))
                if (BuildConfig.PRIVACY_POLICY_URL.isNotBlank()) {
                    LinkItem(Icons.Outlined.Policy, stringResource(R.string.privacy_policy)) { uriHandler.openUri(BuildConfig.PRIVACY_POLICY_URL) }
                }
                if (BuildConfig.TERMS_URL.isNotBlank()) {
                    LinkItem(Icons.Outlined.Description, stringResource(R.string.terms)) { uriHandler.openUri(BuildConfig.TERMS_URL) }
                }
                if (BuildConfig.SUPPORT_EMAIL.isNotBlank()) {
                    LinkItem(Icons.Outlined.Mail, stringResource(R.string.settings_support)) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:${BuildConfig.SUPPORT_EMAIL}".toUri())) }
                    }
                }
                LinkItem(Icons.Outlined.Description, stringResource(R.string.settings_licenses), onOpenLicenses)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_version)) },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                )

                // Account actions
                SectionTitle(stringResource(R.string.settings_account))
                if (state.busy) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                LinkItem(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.action_sign_out), viewModel::askSignOut)
                ListItem(
                    modifier = Modifier.clickable(enabled = !state.busy, onClick = viewModel::askDelete),
                    leadingContent = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                    headlineContent = { Text(stringResource(R.string.action_delete_account), color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text(stringResource(R.string.delete_account_summary)) },
                )
            }
        }
    }

    when (val dialog = state.dialog) {
        is AccountDialog.SignOut -> ConfirmDialog(
            title = stringResource(R.string.confirm_sign_out_title),
            text = if (dialog.pending > 0) pluralStringResource(R.plurals.confirm_sign_out_pending, dialog.pending, dialog.pending)
            else stringResource(R.string.confirm_sign_out_body),
            confirmLabel = stringResource(R.string.action_sign_out),
            destructive = dialog.pending > 0,
            onConfirm = viewModel::signOut,
            onDismiss = viewModel::dismissDialog,
        )
        AccountDialog.Delete -> ConfirmDialog(
            title = stringResource(R.string.confirm_delete_account_title),
            text = stringResource(R.string.confirm_delete_account_body),
            confirmLabel = stringResource(R.string.action_delete_account),
            destructive = true,
            onConfirm = { activity?.let(viewModel::deleteAccount) },
            onDismiss = viewModel::dismissDialog,
        )
        null -> Unit
    }

    if (showFocusPlan) {
        var draft by remember { mutableStateOf(state.settings.defaultFocusPlan) }
        AlertDialog(
            onDismissRequest = { showFocusPlan = false },
            title = { Text(stringResource(R.string.settings_default_focus_plan)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { PlanEditor(draft, { draft = it }) } },
            confirmButton = {
                TextButton(onClick = { viewModel.setDefaultFocusPlan(draft); showFocusPlan = false }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showFocusPlan = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun HealthItem(icon: ImageVector, title: String, ok: Boolean, okText: String, problemText: String, onFix: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(if (ok) okText else problemText, color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error) },
        trailingContent = {
            if (ok) Icon(Icons.Outlined.CheckCircle, okText, tint = MaterialTheme.colorScheme.primary)
            else TextButton(onClick = onFix) {
                Icon(Icons.Outlined.ErrorOutline, null)
                Text(stringResource(R.string.action_fix))
            }
        },
    )
}

@Composable
private fun LinkItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
    )
}
