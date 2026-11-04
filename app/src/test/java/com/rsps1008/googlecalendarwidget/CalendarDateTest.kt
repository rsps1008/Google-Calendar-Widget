package com.rsps1008.googlecalendarwidget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

class CalendarDateTest {
    @Test
    fun multiDayEventsKeepTheirLaneAcrossOverlappingDays() {
        val start = LocalDate.of(2026, 11, 1)
        val week = (0L..6L).map(start::plusDays)
        val trip = CalendarEvent(1, 1, "trip", 1, 2, 0, true)
        val meeting = CalendarEvent(2, 1, "meeting", 3, 4, 0, false)
        val events = mapOf(week[1] to listOf(trip), week[2] to listOf(trip, meeting), week[3] to listOf(trip))
        val lanes = weekEventLanes(week, events)
        assertEquals(trip, lanes[1][0])
        assertEquals(trip, lanes[2][0])
        assertEquals(trip, lanes[3][0])
        assertEquals(meeting, lanes[2][1])
        assertEquals(null, lanes[1][1])
    }
    @Test
    fun zeroDurationMidnightEventBelongsToItsStartingDay() {
        val date = LocalDate.of(2026, 11, 28)
        val instant = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val event = CalendarEvent(1, 5, "event", instant, instant, 0, false)
        val grouped = CalendarRepository.eventsByDate(listOf(event), listOf(date.minusDays(1), date))
        assertEquals(emptyList<CalendarEvent>(), grouped[date.minusDays(1)])
        assertEquals(listOf(event), grouped[date])
    }
    @Test
    fun monthGridUsesOnlyWeeksNeededAndStartsOnConfiguredWeekday() {
        val month = LocalDate.of(2026, 11, 1)

        val mondayFirst = monthGrid(month, mondayFirst = true)
        val sundayFirst = monthGrid(month, mondayFirst = false)

        assertEquals(42, mondayFirst.size)
        assertEquals(LocalDate.of(2026, 10, 26), mondayFirst.first())
        assertEquals(35, sundayFirst.size)
        assertEquals(LocalDate.of(2026, 11, 1), sundayFirst.first())
        assertEquals(LocalDate.of(2026, 12, 5), sundayFirst.last())
        val fourWeeks = monthGrid(LocalDate.of(2026, 2, 1), mondayFirst = false)
        assertEquals(28, fourWeeks.size)
        assertEquals(LocalDate.of(2026, 2, 28), fourWeeks.last())
    }

    @Test
    fun allDayEventsStayOnTheirUtcCalendarDateInWestOfUtcTimeZones() {
        val previousZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val event = CalendarEvent(
                id = 1,
                calendarId = 1,
                title = "All day",
                startMillis = LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                endMillis = LocalDate.of(2026, 1, 2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                color = 0,
                allDay = true,
            )

            val dates = listOf(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1))
            val grouped = CalendarRepository.eventsByDate(listOf(event), dates)

            assertEquals(emptyList<CalendarEvent>(), grouped[LocalDate.of(2025, 12, 31)])
            assertEquals(listOf(event), grouped[LocalDate.of(2026, 1, 1)])
        } finally {
            TimeZone.setDefault(previousZone)
        }
    }
}
