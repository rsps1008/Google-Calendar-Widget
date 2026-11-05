package com.rsps1008.googlecalendarwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.util.TypedValue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class CalendarWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        CalendarChangeJob.schedule(context)
        appWidgetIds.forEach { updateWidget(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PREVIOUS, ACTION_NEXT, ACTION_TODAY -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val current = CalendarPreferences.widgetMonth(context, id)
                    val target = when (intent.action) {
                        ACTION_PREVIOUS -> current.minusMonths(1)
                        ACTION_NEXT -> current.plusMonths(1)
                        else -> LocalDate.now().withDayOfMonth(1)
                    }
                    CalendarPreferences.setWidgetMonth(context, id, target)
                    updateWidget(context, AppWidgetManager.getInstance(context), id)
                }
            }
            Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_CONFIGURATION_CHANGED -> updateAll(context)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        updateWidget(context, manager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { CalendarPreferences.removeWidget(context, it) }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        CalendarChangeJob.cancel(context)
        super.onDisabled(context)
    }

    companion object {
        const val ACTION_PREVIOUS = "com.rsps1008.googlecalendarwidget.PREVIOUS_MONTH"
        const val ACTION_NEXT = "com.rsps1008.googlecalendarwidget.NEXT_MONTH"
        const val ACTION_TODAY = "com.rsps1008.googlecalendarwidget.TODAY"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CalendarWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val month = CalendarPreferences.widgetMonth(context, widgetId)
            val dates = monthGrid(month, CalendarPreferences.weekStartsMonday(context))
            val weekCount = dates.size / 7
            val theme = CalendarPreferences.theme(context)
            val isDark = when (theme) {
                ThemeChoice.DARK -> true
                ThemeChoice.LIGHT -> false
                ThemeChoice.SYSTEM -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            val foreground = if (isDark) 0xFFEAF4F6.toInt() else 0xFF20343C.toInt()
            val options = manager.getAppWidgetOptions(widgetId)
            val optionHeight = maxOf(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
            )
            val optionWidth = maxOf(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
            )
            // Xiaomi Launcher reports the host height without the extra 16dp available to the RemoteViews content.
            val rowHeightDp = if (optionHeight > 0) ((optionHeight - 52) / weekCount).coerceAtLeast(18) else 22
            val cellWidthDp = if (optionWidth > 0) (optionWidth - 16) / 7f else 40f
            val eventTextSizeSp = when {
                cellWidthDp >= 64f -> 11f
                cellWidthDp >= 52f -> 10f
                else -> 9f
            }
            val textPaint = android.graphics.Paint().apply {
                textSize = eventTextSizeSp * context.resources.displayMetrics.scaledDensity
            }
            val lineHeightDp = kotlin.math.ceil((textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent) /
                context.resources.displayMetrics.density)
            val capacity = ((rowHeightDp - 22f) / lineHeightDp).toInt().coerceAtLeast(0)
            // MIUI can reapply the old nested week rows when only the month label changes. Alternate
            // equivalent root layouts so each refresh gets a fresh hierarchy and a matching month grid.
            val rootLayout = if (CalendarPreferences.nextWidgetLayoutVariant(context, widgetId)) {
                R.layout.widget_calendar_v2_alt
            } else {
                R.layout.widget_calendar_v2
            }
            val views = RemoteViews(context.packageName, rootLayout).apply {
                setTextViewText(R.id.widget_month, month.format(DateTimeFormatter.ofPattern("M月", Locale.TAIWAN)))
                setTextColor(R.id.widget_month, foreground)
                setTextColor(R.id.widget_previous, foreground)
                setTextColor(R.id.widget_next, foreground)
                setTextColor(R.id.widget_today, foreground)
                setInt(R.id.widget_root, "setBackgroundResource", if (isDark) R.drawable.widget_background_dark else R.drawable.widget_background_light)
                setOnClickPendingIntent(R.id.widget_previous, monthButton(context, widgetId, ACTION_PREVIOUS))
                setOnClickPendingIntent(R.id.widget_next, monthButton(context, widgetId, ACTION_NEXT))
                setOnClickPendingIntent(R.id.widget_today, monthButton(context, widgetId, ACTION_TODAY))
                val mondayFirst = CalendarPreferences.weekStartsMonday(context)
                val labels = weekdayLabels(mondayFirst)
                listOf(R.id.weekday_1, R.id.weekday_2, R.id.weekday_3, R.id.weekday_4, R.id.weekday_5, R.id.weekday_6, R.id.weekday_7)
                    .forEachIndexed { index, id ->
                        setTextViewText(id, labels[index])
                        setTextColor(id, if (isDark) 0xFFB7C9CE.toInt() else 0xFF657A80.toInt())
                    }
                setOnClickPendingIntent(R.id.widget_month, calendarMonthIntent(context, month))
            }
            val selected = CalendarPreferences.selectedCalendarIds(context)
            val calendarIds = if (CalendarPreferences.hasExplicitCalendarSelection(context)) selected else
                CalendarRepository.calendars(context).filter { it.visible }.map { it.id }.toSet()
            val events = CalendarRepository.events(context, dates.first(), dates.last().plusDays(1), calendarIds)
            val byDate = CalendarRepository.eventsByDate(events, dates)
            // Launchers reuse the existing RemoteViews tree on update; clear old weeks before adding this month's grid.
            views.removeAllViews(R.id.widget_days_container)
            dates.chunked(7).forEachIndexed { weekIndex, week ->
                val dayLanes = weekEventLanes(week, byDate)
                val weekViews = RemoteViews(context.packageName, R.layout.widget_calendar_week)
                weekViews.setViewLayoutHeight(R.id.widget_week_row, rowHeightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                week.forEachIndexed { dayIndex, date ->
                    val dayViews = RemoteViews(context.packageName, R.layout.widget_day_cell).apply {
                        val isToday = date == LocalDate.now()
                        setTextViewText(R.id.cell_day, date.dayOfMonth.toString())
                        setTextViewTextSize(R.id.cell_day, TypedValue.COMPLEX_UNIT_SP, if (rowHeightDp >= 32) 12f else 10f)
                        setTextViewText(R.id.cell_lunar, if (CalendarPreferences.showLunarDates(context)) lunarDateLabel(date) else "")
                        setViewVisibility(R.id.cell_lunar, if (CalendarPreferences.showLunarDates(context)) android.view.View.VISIBLE else android.view.View.GONE)
                        setTextColor(R.id.cell_day, when {
                            isToday -> 0xFF071E27.toInt()
                            date.month == month.month && date.year == month.year -> foreground
                            else -> if (isDark) 0xFF71848A.toInt() else 0xFFADB9BC.toInt()
                        })
                        val isFirstColumn = dayIndex == 0
                        val isLastColumn = dayIndex == 6
                        val isLastRow = weekIndex == weekCount - 1
                        val cellBorder = when {
                            isDark && isLastRow && isFirstColumn -> R.drawable.widget_cell_dark_first_bottom
                            isDark && isLastRow && isLastColumn -> R.drawable.widget_cell_dark_last_bottom
                            isDark && isLastRow -> R.drawable.widget_cell_dark_middle_bottom
                            isDark && isFirstColumn -> R.drawable.widget_cell_dark_first
                            isDark && isLastColumn -> R.drawable.widget_cell_dark_last
                            isDark -> R.drawable.widget_cell_dark_middle
                            isLastRow && isFirstColumn -> R.drawable.widget_cell_light_first_bottom
                            isLastRow && isLastColumn -> R.drawable.widget_cell_light_last_bottom
                            isLastRow -> R.drawable.widget_cell_light_middle_bottom
                            isFirstColumn -> R.drawable.widget_cell_light_first
                            isLastColumn -> R.drawable.widget_cell_light_last
                            else -> R.drawable.widget_cell_light_middle
                        }
                        setInt(R.id.cell_root, "setBackgroundResource", cellBorder)
                        setInt(R.id.cell_day, "setBackgroundResource", if (isToday) R.drawable.widget_today_badge else android.R.color.transparent)
                        dayLanes[dayIndex].take(capacity).forEachIndexed { lane, event ->
                            val item = RemoteViews(context.packageName, R.layout.widget_event_item)
                            item.setViewLayoutHeight(R.id.event_item, (lineHeightDp - 1f).coerceAtLeast(1f), TypedValue.COMPLEX_UNIT_DIP)
                            item.setTextViewTextSize(R.id.event_title, TypedValue.COMPLEX_UNIT_SP, eventTextSizeSp)
                            if (event == null) {
                                item.setTextViewText(R.id.event_title, " ")
                                item.setViewVisibility(R.id.event_background, android.view.View.INVISIBLE)
                            } else {
                                val start = dayIndex == 0 || dayLanes[dayIndex - 1][lane] != event
                                val end = dayIndex == 6 || dayLanes[dayIndex + 1][lane] != event
                                item.setTextViewText(R.id.event_title, if (start && end) event.title else " ")
                                val color = widgetEventColor(event.color, isDark)
                                val backgroundHeightDp = (lineHeightDp - 1f).coerceAtLeast(1f)
                                item.setViewLayoutHeight(R.id.event_background, backgroundHeightDp, TypedValue.COMPLEX_UNIT_DIP)
                                item.setImageViewBitmap(R.id.event_background, eventBackground(context, color, cellWidthDp, backgroundHeightDp, start, end))
                            }
                            addView(R.id.cell_events, item)
                        }
                        setOnClickPendingIntent(R.id.cell_root, dateIntent(context, widgetId, dayIndex + weekIndex * 7, date))
                    }
                    weekViews.addView(R.id.widget_week_cells, dayViews)
                }
                dayLanes.firstOrNull().orEmpty().indices.take(capacity).forEach { lane ->
                    var day = 0
                    while (day < week.size) {
                        val event = dayLanes[day][lane]
                        if (event == null) { day++; continue }
                        var end = day + 1
                        while (end < week.size && dayLanes[end][lane] == event) end++
                        val span = end - day
                        if (span > 1) {
                            val label = RemoteViews(context.packageName, R.layout.widget_span_label)
                            label.setTextViewText(R.id.span_label, event.title)
                            label.setTextViewTextSize(R.id.span_label, TypedValue.COMPLEX_UNIT_SP, eventTextSizeSp)
                            label.setViewLayoutHeight(R.id.span_label, (lineHeightDp - 1f).coerceAtLeast(1f), TypedValue.COMPLEX_UNIT_DIP)
                            label.setViewLayoutWidth(R.id.span_label, (cellWidthDp * span - 7f).coerceAtLeast(1f), TypedValue.COMPLEX_UNIT_DIP)
                            label.setViewLayoutMargin(R.id.span_label, RemoteViews.MARGIN_LEFT, day * cellWidthDp + 7f, TypedValue.COMPLEX_UNIT_DIP)
                            label.setViewLayoutMargin(R.id.span_label, RemoteViews.MARGIN_TOP, 22f + lane * lineHeightDp, TypedValue.COMPLEX_UNIT_DIP)
                            weekViews.addView(R.id.widget_week_labels, label)
                        }
                        day = end
                    }
                }
                views.addView(R.id.widget_days_container, weekViews)
            }
            manager.updateAppWidget(widgetId, views)
        }

        private fun widgetEventColor(color: Int, dark: Boolean): Int {
            val source = color.takeIf { it != 0 } ?: 0xFF6085D8.toInt()
            if (!dark) return source
            return when (source) {
                0xFFDC2127.toInt() -> 0xFFCE6047.toInt()
                0xFF9FC6E7.toInt() -> 0xFF708AD7.toInt()
                else -> source
            }
        }

        private fun eventBackground(context: Context, color: Int, widthDp: Float, heightDp: Float, start: Boolean, end: Boolean): android.graphics.Bitmap {
            val density = context.resources.displayMetrics.density
            val width = (widthDp * density).toInt().coerceAtLeast(1)
            val height = (heightDp * density).toInt().coerceAtLeast(1)
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            val radius = 3f * density
            val canvas = android.graphics.Canvas(bitmap)
            val left = if (start) 2f * density else 0f
            val right = width.toFloat() - if (end) 2f * density else 0f
            canvas.drawRoundRect(left, 0f, right, height.toFloat(), radius, radius, paint)
            if (!start) canvas.drawRect(0f, 0f, radius, height.toFloat(), paint)
            if (!end) canvas.drawRect(width - radius, 0f, width.toFloat(), height.toFloat(), paint)
            return bitmap
        }

        private fun monthButton(context: Context, id: Int, action: String): PendingIntent {
            val intent = Intent(context, CalendarWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            }
            val offset = when (action) { ACTION_PREVIOUS -> 1; ACTION_NEXT -> 2; else -> 3 }
            return PendingIntent.getBroadcast(context, id * 10 + offset, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun dateIntent(context: Context, widgetId: Int, position: Int, date: LocalDate): PendingIntent {
            return PendingIntent.getActivity(context, widgetId * 100 + position, CalendarIntents.viewDate(date), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun calendarMonthIntent(context: Context, month: LocalDate): PendingIntent {
            val intent = CalendarIntents.viewDate(month.withDayOfMonth(1))
            return PendingIntent.getActivity(context, month.year * 100 + month.monthValue, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

    }
}
