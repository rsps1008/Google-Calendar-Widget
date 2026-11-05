package com.rsps1008.googlecalendarwidget

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class ThemeChoice(val storedValue: String, val label: String) {
    SYSTEM("system", "跟隨系統"),
    LIGHT("light", "淺色"),
    DARK("dark", "深色"),
}

object CalendarPreferences {
    private const val FILE_NAME = "calendar_preferences"
    private const val KEY_SELECTED_CALENDARS = "selected_calendars"
    private const val KEY_WEEK_START = "week_start_monday"
    private const val KEY_THEME = "theme"
    private const val KEY_SHOW_LUNAR = "show_lunar_dates"
    private const val KEY_WIDGET_MONTH_PREFIX = "widget_month_"
    private const val KEY_WIDGET_LAYOUT_VARIANT_PREFIX = "widget_layout_variant_"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun selectedCalendarIds(context: Context): Set<Long> =
        prefs(context).getStringSet(KEY_SELECTED_CALENDARS, null)?.mapNotNull(String::toLongOrNull)?.toSet().orEmpty()

    fun setSelectedCalendarIds(context: Context, ids: Set<Long>) {
        prefs(context).edit().putStringSet(KEY_SELECTED_CALENDARS, ids.map(Long::toString).toSet()).apply()
        CalendarWidgetProvider.updateAll(context)
    }

    fun hasExplicitCalendarSelection(context: Context): Boolean =
        prefs(context).contains(KEY_SELECTED_CALENDARS)

    fun weekStartsMonday(context: Context): Boolean = prefs(context).getBoolean(KEY_WEEK_START, true)

    fun setWeekStartsMonday(context: Context, monday: Boolean) {
        prefs(context).edit().putBoolean(KEY_WEEK_START, monday).apply()
        CalendarWidgetProvider.updateAll(context)
    }

    fun theme(context: Context): ThemeChoice =
        ThemeChoice.entries.firstOrNull { it.storedValue == prefs(context).getString(KEY_THEME, ThemeChoice.SYSTEM.storedValue) }
            ?: ThemeChoice.SYSTEM

    fun setTheme(context: Context, theme: ThemeChoice) {
        prefs(context).edit().putString(KEY_THEME, theme.storedValue).apply()
        CalendarWidgetProvider.updateAll(context)
    }

    fun showLunarDates(context: Context): Boolean = prefs(context).getBoolean(KEY_SHOW_LUNAR, true)

    fun setShowLunarDates(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_LUNAR, show).apply()
        CalendarWidgetProvider.updateAll(context)
    }

    fun widgetMonth(context: Context, widgetId: Int): LocalDate {
        val millis = prefs(context).getLong(KEY_WIDGET_MONTH_PREFIX + widgetId, 0L)
        return if (millis == 0L) LocalDate.now().withDayOfMonth(1)
        else java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1)
    }

    fun setWidgetMonth(context: Context, widgetId: Int, month: LocalDate) {
        val millis = month.withDayOfMonth(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        prefs(context).edit().putLong(KEY_WIDGET_MONTH_PREFIX + widgetId, millis).apply()
    }

    fun nextWidgetLayoutVariant(context: Context, widgetId: Int): Boolean {
        val key = KEY_WIDGET_LAYOUT_VARIANT_PREFIX + widgetId
        val next = !prefs(context).getBoolean(key, false)
        prefs(context).edit().putBoolean(key, next).apply()
        return next
    }

    fun removeWidget(context: Context, widgetId: Int) {
        prefs(context).edit()
            .remove(KEY_WIDGET_MONTH_PREFIX + widgetId)
            .remove(KEY_WIDGET_LAYOUT_VARIANT_PREFIX + widgetId)
            .apply()
    }
}

fun monthGrid(month: LocalDate, mondayFirst: Boolean): List<LocalDate> {
    val first = month.withDayOfMonth(1)
    val firstDay = if (mondayFirst) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    val start = first.with(TemporalAdjusters.previousOrSame(firstDay))
    val daysThroughMonthEnd = java.time.temporal.ChronoUnit.DAYS.between(start, first.plusMonths(1))
    val gridDays = ((daysThroughMonthEnd + 6) / 7) * 7
    return (0L until gridDays).map(start::plusDays)
}

fun weekdayLabels(mondayFirst: Boolean): List<String> =
    if (mondayFirst) listOf("一", "二", "三", "四", "五", "六", "日")
    else listOf("日", "一", "二", "三", "四", "五", "六")
