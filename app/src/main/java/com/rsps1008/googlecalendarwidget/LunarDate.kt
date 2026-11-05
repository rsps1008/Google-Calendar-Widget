package com.rsps1008.googlecalendarwidget

import android.icu.util.ChineseCalendar
import java.time.LocalDate
import java.util.GregorianCalendar

/** Compact traditional Chinese lunar label for a Gregorian date. */
fun lunarDateLabel(date: LocalDate): String {
    val gregorian = GregorianCalendar(java.util.TimeZone.getDefault()).apply {
        clear()
        set(date.year, date.monthValue - 1, date.dayOfMonth, 12, 0, 0)
    }
    val zone = android.icu.util.TimeZone.getDefault()
    val lunar = ChineseCalendar(zone).apply { timeInMillis = gregorian.timeInMillis }
    val day = lunar.get(ChineseCalendar.DATE)
    if (day != 1) {
        val digits = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
        return when {
            day < 10 -> "初${digits[day - 1]}"
            day == 10 -> "初十"
            day < 20 -> "十${digits[day - 11]}"
            day == 20 -> "二十"
            day < 30 -> "廿${digits[day - 21]}"
            else -> "三十"
        }
    }
    val months = listOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "臘")
    val month = months[lunar.get(ChineseCalendar.MONTH)] + "月"
    return if (lunar.get(ChineseCalendar.IS_LEAP_MONTH) == 1) "閏$month" else month
}
