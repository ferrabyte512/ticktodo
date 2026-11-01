package app.ticktodo.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
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

/** Edits a focus plan: focus and break length, and whether phases start on their own. */
@Composable
fun PlanEditor(plan: FocusPlan, onChange: (FocusPlan) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Stepper(stringResource(R.string.focus_length), plan.focusMinutes, FocusPlan.FOCUS_RANGE, step = 5, unit = R.string.unit_min) {
            onChange(plan.copy(focusMinutes = it))
        }
        Stepper(stringResource(R.string.focus_break), plan.breakMinutes, FocusPlan.BREAK_RANGE, step = 1, unit = R.string.unit_min) {
            onChange(plan.copy(breakMinutes = it))
        }
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
    onChange: (Int) -> Unit,
) {
    fun clamp(v: Int) = v.coerceIn(range.first, range.last)
    val valueText = "$value ${stringResource(unit)}"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 8.dp))
        OutlinedIconButton(
            onClick = { onChange(clamp(if (value - step < range.first && value > range.first) range.first else value - step)) },
            enabled = value > range.first,
        ) { Icon(Icons.Outlined.Remove, stringResource(R.string.a11y_decrease, label)) }
        Text(
            valueText,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 76.dp).padding(horizontal = 2.dp),
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
