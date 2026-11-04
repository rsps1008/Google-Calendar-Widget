package com.rsps1008.googlecalendarwidget

import android.content.Intent
import android.net.Uri
import java.time.LocalDate
import java.time.ZoneId

object CalendarIntents {
    private const val GOOGLE_CALENDAR_PACKAGE = "com.google.android.calendar"

    fun viewDate(date: LocalDate): Intent {
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("content://com.android.calendar/time/$millis")
            setPackage(GOOGLE_CALENDAR_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
