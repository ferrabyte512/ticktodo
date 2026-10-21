package app.ticktodo.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.ticktodo.R
import app.ticktodo.domain.model.Recurrence
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class DueStatus { OVERDUE, TODAY, TOMORROW, LATER }

fun dueStatus(date: LocalDate, today: LocalDate): DueStatus = when {
    date.isBefore(today) -> DueStatus.OVERDUE
    date == today -> DueStatus.TODAY
    date == today.plusDays(1) -> DueStatus.TOMORROW
    else -> DueStatus.LATER
}

@Composable
fun formatDate(date: LocalDate, today: LocalDate): String {
    val locale = currentLocale()
    return when {
        date == today -> stringResource(R.string.date_today)
        date == today.plusDays(1) -> stringResource(R.string.date_tomorrow)
        date == today.minusDays(1) -> stringResource(R.string.date_yesterday)
        ChronoUnit.DAYS.between(today, date) in 2..6 ->
            date.format(DateTimeFormatter.ofPattern("EEEE", locale))
        date.year == today.year ->
            date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEE d MMM"), locale))
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
}

/** The UI locale, read so that Compose recomposes when it changes. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
fun formatTime(time: LocalTime): String {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    return time.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", currentLocale()))
}

@Composable
fun formatReminder(offsetMinutes: Int): String = when {
    offsetMinutes == 0 -> stringResource(R.string.reminder_at_time)
    offsetMinutes % 1_440 == 0 -> pluralStringResource(R.plurals.reminder_days_before, offsetMinutes / 1_440, offsetMinutes / 1_440)
    offsetMinutes % 60 == 0 -> pluralStringResource(R.plurals.reminder_hours_before, offsetMinutes / 60, offsetMinutes / 60)
    else -> pluralStringResource(R.plurals.reminder_minutes_before, offsetMinutes, offsetMinutes)
}

@Composable
fun formatRecurrence(rule: Recurrence): String = when {
    rule.isWeekdays -> stringResource(R.string.repeat_weekdays)
    rule.frequency == Recurrence.Frequency.DAILY ->
        if (rule.interval == 1) stringResource(R.string.repeat_daily)
        else pluralStringResource(R.plurals.repeat_every_n_days, rule.interval, rule.interval)
    rule.frequency == Recurrence.Frequency.WEEKLY ->
        if (rule.interval == 1) stringResource(R.string.repeat_weekly)
        else pluralStringResource(R.plurals.repeat_every_n_weeks, rule.interval, rule.interval)
    rule.frequency == Recurrence.Frequency.MONTHLY ->
        if (rule.interval == 1) stringResource(R.string.repeat_monthly)
        else pluralStringResource(R.plurals.repeat_every_n_months, rule.interval, rule.interval)
    else ->
        if (rule.interval == 1) stringResource(R.string.repeat_yearly)
        else pluralStringResource(R.plurals.repeat_every_n_years, rule.interval, rule.interval)
}
