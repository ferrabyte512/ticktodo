package app.ticktodo.ui.focus

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ticktodo.R
import app.ticktodo.domain.focus.FocusPlan

/** Edits a focus plan: one-tap presets plus fine-tuning for every value. */
@Composable
fun PlanEditor(plan: FocusPlan, onChange: (FocusPlan) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FocusPlan.PRESETS.forEach { preset ->
                val same = preset.focusMinutes == plan.focusMinutes && preset.shortBreakMinutes == plan.shortBreakMinutes &&
                    preset.longBreakMinutes == plan.longBreakMinutes && preset.roundsBeforeLongBreak == plan.roundsBeforeLongBreak
                FilterChip(
                    selected = same,
                    onClick = { onChange(preset.copy(autoStartBreaks = plan.autoStartBreaks, autoStartFocus = plan.autoStartFocus)) },
                    label = { Text(stringResource(R.string.focus_preset, preset.focusMinutes, preset.shortBreakMinutes)) },
                )
            }
        }
        Stepper(stringResource(R.string.focus_length), plan.focusMinutes, FocusPlan.FOCUS_RANGE, step = 5, unit = R.string.unit_min) {
            onChange(plan.copy(focusMinutes = it))
        }
        Stepper(stringResource(R.string.focus_short_break), plan.shortBreakMinutes, FocusPlan.BREAK_RANGE, step = 1, unit = R.string.unit_min) {
            onChange(plan.copy(shortBreakMinutes = it))
        }
        Stepper(stringResource(R.string.focus_long_break), plan.longBreakMinutes, FocusPlan.BREAK_RANGE, step = 5, unit = R.string.unit_min) {
            onChange(plan.copy(longBreakMinutes = it))
        }
        Stepper(stringResource(R.string.focus_long_break_every), plan.roundsBeforeLongBreak, 1..12, step = 1, unit = R.string.unit_rounds) {
            onChange(plan.copy(roundsBeforeLongBreak = it))
        }
        Stepper(
            label = stringResource(R.string.focus_total_rounds),
            value = plan.totalRounds ?: 0,
            range = 0..24,
            step = 1,
            unit = R.string.unit_rounds,
            zeroLabel = stringResource(R.string.focus_until_stopped),
        ) { onChange(plan.copy(totalRounds = it.takeIf { v -> v > 0 })) }
        SwitchRow(stringResource(R.string.focus_auto_breaks), plan.autoStartBreaks) { onChange(plan.copy(autoStartBreaks = it)) }
        SwitchRow(stringResource(R.string.focus_auto_focus), plan.autoStartFocus) { onChange(plan.copy(autoStartFocus = it)) }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    unit: Int,
    zeroLabel: String? = null,
    onChange: (Int) -> Unit,
) {
    fun clamp(v: Int) = v.coerceIn(range.first, range.last)
    val valueText = if (value == 0 && zeroLabel != null) zeroLabel else "$value ${stringResource(unit)}"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedIconButton(
            onClick = { onChange(clamp(if (value - step < range.first && value > range.first) range.first else value - step)) },
            enabled = value > range.first,
        ) { Icon(Icons.Outlined.Remove, stringResource(R.string.a11y_decrease, label)) }
        Text(
            valueText,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 88.dp).padding(horizontal = 4.dp),
        )
        OutlinedIconButton(
            onClick = { onChange(clamp(if (value == range.first && step > 1 && range.first <= 1) step else value + step)) },
            enabled = value < range.last,
        ) { Icon(Icons.Outlined.Add, stringResource(R.string.a11y_increase, label)) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
