package com.rsps1008.googlecalendarwidget

import android.content.ContentUris
import android.content.Context
import android.util.Log
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val visible: Boolean,
)

data class CalendarEvent(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val color: Int,
    val allDay: Boolean,
)

object CalendarRepository {
    fun calendars(context: Context): List<CalendarInfo> {
        if (!hasReadPermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE,
        )
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "(${CalendarContract.Calendars.SYNC_EVENTS} = 1 OR ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?)",
                arrayOf("com.google"),
                "${CalendarContract.Calendars.ACCOUNT_NAME}, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME}",
            )?.use { cursor ->
                buildList {
                    val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                    val accountCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                    val typeCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
                    val colorCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
                    val visibleCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE)
                    while (cursor.moveToNext()) {
                        add(
                            CalendarInfo(
                                id = cursor.getLong(idCol),
                                name = cursor.getString(nameCol).orEmpty(),
                                accountName = cursor.getString(accountCol).orEmpty(),
                                accountType = cursor.getString(typeCol).orEmpty(),
                                color = cursor.getInt(colorCol),
                                visible = cursor.getInt(visibleCol) == 1,
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    fun events(context: Context, from: LocalDate, untilExclusive: LocalDate, calendarIds: Set<Long>): List<CalendarEvent> {
        if (!hasReadPermission(context) || calendarIds.isEmpty()) {
            Log.w("CalendarRepo", "skip events: permission=${hasReadPermission(context)} calendarIds=${calendarIds.size}")
            return emptyList()
        }
        val calendarColors = calendars(context).associate { it.id to it.color }
        val zone = ZoneId.systemDefault()
        val begin = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = untilExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_COLOR,
            CalendarContract.Instances.ALL_DAY,
        )
        val placeholders = calendarIds.joinToString(",") { "?" }
        val result = runCatching {
            context.contentResolver.query(
                uri,
                projection,
                "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)",
                calendarIds.map(Long::toString).toTypedArray(),
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                buildList {
                    val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                    val calendarCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
                    val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                    val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                    val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                    val colorCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_COLOR)
                    val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                    while (cursor.moveToNext()) {
                        val calendarId = cursor.getLong(calendarCol)
                        val eventColor = cursor.getInt(colorCol)
                        add(
                            CalendarEvent(
                                id = cursor.getLong(idCol),
                                calendarId = calendarId,
                                title = cursor.getString(titleCol).orEmpty().ifBlank { "（無標題）" },
                                startMillis = cursor.getLong(beginCol),
                                endMillis = cursor.getLong(endCol),
                                color = eventColor.takeIf { it != 0 } ?: calendarColors[calendarId] ?: 0,
                                allDay = cursor.getInt(allDayCol) == 1,
                            ),
                        )
                    }
                }
            }.orEmpty()
        }
        result.onFailure { Log.e("CalendarRepo", "event query failed range=$from..$untilExclusive calendars=${calendarIds.size}", it) }
        return result.getOrDefault(emptyList()).also {
            Log.d("CalendarRepo", "event query range=$from..$untilExclusive calendars=${calendarIds.size} count=${it.size}")
        }
    }

    fun eventsByDate(events: List<CalendarEvent>, dates: List<LocalDate>): Map<LocalDate, List<CalendarEvent>> {
        val zone = ZoneId.systemDefault()
        return dates.associateWith { date ->
            val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            events.filter { event ->
                if (event.allDay) {
                    val startDate = Instant.ofEpochMilli(event.startMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    val endDateExclusive = Instant.ofEpochMilli(event.endMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    date >= startDate && date < endDateExclusive
                } else event.startMillis < dayEnd && maxOf(event.endMillis, event.startMillis + 1) > dayStart
            }
        }
    }

    fun hasReadPermission(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

fun CalendarEvent.dateInDeviceZone(): LocalDate =
    Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate()

/** Assign each occurrence one lane across all days it occupies in this week. */
fun weekEventLanes(week: List<LocalDate>, byDate: Map<LocalDate, List<CalendarEvent>>): List<List<CalendarEvent?>> {
    val occurrences = week.flatMap { byDate[it].orEmpty() }.distinct()
        .sortedWith(compareByDescending<CalendarEvent> { event -> week.count { event in byDate[it].orEmpty() } }
            .thenBy { it.startMillis }.thenBy { it.id })
    val lanes = mutableListOf<MutableList<CalendarEvent?>>()
    occurrences.forEach { event ->
        val occupied = week.indices.filter { event in byDate[week[it]].orEmpty() }
        val lane = lanes.firstOrNull { row -> occupied.all { row[it] == null } }
            ?: MutableList<CalendarEvent?>(week.size) { null }.also { lanes.add(it) }
        occupied.forEach { lane[it] = event }
    }
    return week.indices.map { day -> lanes.map { it[day] } }
}
