package com.cansin.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.prefs.Settings
import kotlin.math.roundToInt

data class NotificationAccess(val permissionGranted: Boolean, val enabled: Boolean)

@Composable
fun ReminderSection(settings: Settings, access: NotificationAccess, actions: SettingsActions) {
    var showRationale by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) actions.onRemindersEnabled(true)
    }
    val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !access.permissionGranted

    SectionHeader(R.string.section_reminders)
    SwitchRow(R.string.setting_reminders, settings.remindersEnabled) { on ->
        when {
            !on -> actions.onRemindersEnabled(false)
            needsRuntimePermission -> showRationale = true
            else -> actions.onRemindersEnabled(true)
        }
    }
    if ((settings.remindersEnabled && !access.enabled) || (denied && !access.permissionGranted)) {
        Text(
            stringResource(R.string.notifications_blocked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = actions.onOpenNotificationSettings) { Text(stringResource(R.string.open_settings)) }
    }
    if (settings.remindersEnabled) {
        var interval by remember(settings.reminderIntervalMin) { mutableFloatStateOf(settings.reminderIntervalMin.toFloat()) }
        Text(
            stringResource(R.string.setting_reminder_interval, interval.roundToInt()),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.heightIn(min = 32.dp),
        )
        Slider(
            value = interval,
            onValueChange = { interval = it },
            onValueChangeFinished = { actions.onReminderInterval(interval.roundToInt()) },
            valueRange = Settings.MIN_REMINDER_INTERVAL_MIN.toFloat()..Settings.MAX_REMINDER_INTERVAL_MIN.toFloat(),
            steps = (Settings.MAX_REMINDER_INTERVAL_MIN - Settings.MIN_REMINDER_INTERVAL_MIN) / 5 - 1,
        )
        SwitchRow(R.string.setting_reminder_silent, settings.reminderSilent, actions.onReminderSilent)
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(stringResource(R.string.reminder_rationale_title)) },
            text = { Text(stringResource(R.string.reminder_rationale_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
internal fun SwitchRow(title: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(title), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
