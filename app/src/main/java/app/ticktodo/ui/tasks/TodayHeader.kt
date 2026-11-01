package app.ticktodo.ui.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ticktodo.R
import app.ticktodo.ui.common.GlassSurface
import app.ticktodo.ui.common.currentLocale
import app.ticktodo.ui.theme.LocalExtraColors
import java.time.LocalDate
import java.time.format.TextStyle

/** Which part of the Today list is shown. Pure view state, kept by the screen. */
enum class TodayFilter { ALL, TODAY, OVERDUE }

/**
 * Today's summary: a completion ring with "done / total", the remaining,
 * overdue and completed counts, and the filter chips.
 */
@Composable
fun TodayDashboard(
    done: Int,
    remaining: Int,
    overdue: Int,
    filter: TodayFilter,
    onFilter: (TodayFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = done + remaining + overdue
    val extra = LocalExtraColors.current
    GlassSurface(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompletionRing(done, total, Modifier.size(104.dp))
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CountLine(Icons.Outlined.RadioButtonUnchecked, remaining, R.string.today_remaining, MaterialTheme.colorScheme.onSurfaceVariant)
                    CountLine(Icons.Outlined.ErrorOutline, overdue, R.string.today_overdue, extra.overdue)
                    CountLine(Icons.Outlined.CheckCircle, done, R.string.today_completed, extra.success)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterPill(stringResource(R.string.filter_all_n, total), filter == TodayFilter.ALL) { onFilter(TodayFilter.ALL) }
                FilterPill(stringResource(R.string.filter_today_n, remaining), filter == TodayFilter.TODAY) { onFilter(TodayFilter.TODAY) }
                FilterPill(stringResource(R.string.filter_overdue_n, overdue), filter == TodayFilter.OVERDUE) { onFilter(TodayFilter.OVERDUE) }
            }
        }
    }
}

@Composable
private fun CompletionRing(done: Int, total: Int, modifier: Modifier = Modifier) {
    val fraction = if (total == 0) 0f else done.toFloat() / total
    val sweep by animateFloatAsState(fraction, tween(500), label = "ring")
    val ring = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val label = stringResource(R.string.today_done_a11y, done, total)
    Box(modifier.semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(104.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (sweep > 0f) {
                drawArc(ring, -90f, 360f * sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$done/$total", style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.today_tasks_done),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CountLine(icon: ImageVector, count: Int, label: Int, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Spacer(Modifier.width(8.dp))
        Text("$count ${stringResource(label)}", style = MaterialTheme.typography.bodyMedium, color = if (tint == MaterialTheme.colorScheme.onSurfaceVariant) MaterialTheme.colorScheme.onSurface else tint)
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * The next seven days as small pills under the Upcoming title. Tapping one
 * scrolls to that day's section; days with no tasks are shown dimmed.
 */
@Composable
fun DateStrip(
    today: LocalDate,
    daysWithTasks: Set<LocalDate>,
    selected: LocalDate?,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (1..7).map { today.plusDays(it.toLong()) }.forEach { day ->
            val has = day in daysWithTasks
            val isSelected = day == selected
            val label = day.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
            Column(
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(1.dp, if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                    .clickable(enabled = has) { onPick(day) }
                    .semantics { role = Role.Tab; this.selected = isSelected; contentDescription = label }
                    .width(52.dp)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fg = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    has -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.outline
                }
                Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = fg, textAlign = TextAlign.Center)
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                )
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (has && !isSelected) MaterialTheme.colorScheme.primary else Color.Transparent))
            }
        }
    }
}
