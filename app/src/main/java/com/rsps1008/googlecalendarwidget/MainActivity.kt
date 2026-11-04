package com.rsps1008.googlecalendarwidget

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rsps1008.googlecalendarwidget.ui.theme.GoogleCalendarWidgetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CalendarHome() }
    }
}

@Composable
private fun CalendarHome() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var themeChoice by remember { mutableStateOf(CalendarPreferences.theme(context)) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val useDark = when (themeChoice) {
        ThemeChoice.SYSTEM -> systemDark
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    var canRead by remember { mutableStateOf(CalendarRepository.hasReadPermission(context)) }
    var calendars by remember { mutableStateOf(emptyList<CalendarInfo>()) }
    var selectedCalendars by remember { mutableStateOf(CalendarPreferences.selectedCalendarIds(context)) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var events by remember { mutableStateOf(emptyList<CalendarEvent>()) }
    var mondayFirst by remember { mutableStateOf(CalendarPreferences.weekStartsMonday(context)) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canRead = granted
        if (granted) refreshKey++
        else Toast.makeText(context, "需要日曆讀取權限才能顯示行程", Toast.LENGTH_LONG).show()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canRead = CalendarRepository.hasReadPermission(context)
                refreshKey++
                CalendarWidgetProvider.updateAll(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context, canRead) {
        val observer = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshKey++
                CalendarWidgetProvider.updateAll(context)
            }
        }
        if (canRead) context.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer)
        onDispose {
            if (canRead) context.contentResolver.unregisterContentObserver(observer)
        }
    }

    LaunchedEffect(canRead, refreshKey) {
        if (canRead) {
            val found = withContext(Dispatchers.IO) { CalendarRepository.calendars(context) }
            calendars = found
            if (!CalendarPreferences.hasExplicitCalendarSelection(context)) {
                selectedCalendars = found.filter { it.visible }.map { it.id }.toSet()
                CalendarPreferences.setSelectedCalendarIds(context, selectedCalendars)
            } else {
                selectedCalendars = CalendarPreferences.selectedCalendarIds(context).intersect(found.map { it.id }.toSet())
            }
        }
    }

    LaunchedEffect(canRead, currentMonth, selectedCalendars, mondayFirst, refreshKey) {
        if (canRead && selectedCalendars.isNotEmpty()) {
            val gridDates = monthGrid(currentMonth.atDay(1), mondayFirst)
            val first = gridDates.first()
            val end = gridDates.last().plusDays(1)
            events = withContext(Dispatchers.IO) { CalendarRepository.events(context, first, end, selectedCalendars) }
        } else events = emptyList()
        CalendarWidgetProvider.updateAll(context)
    }

    GoogleCalendarWidgetTheme(darkTheme = useDark, dynamicColor = false) {
        val bg = if (useDark) Color(0xFF101719) else Color(0xFFF3F6F6)
        val surface = if (useDark) Color(0xFF1E3037) else Color.White
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("行事曆", style = MaterialTheme.typography.titleMedium, color = if (useDark) Color(0xFFABC8D1) else Color(0xFF54717A))
                    }
                    TextButton(onClick = { showSettings = true }) { Text("設定") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        currentMonth.format(DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.TAIWAN)),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    TextButton(onClick = { currentMonth = currentMonth.minusMonths(1); selectedDate = currentMonth.atDay(1) }) { Text("‹", fontSize = 28.sp) }
                    TextButton(onClick = { currentMonth = YearMonth.now(); selectedDate = LocalDate.now() }) { Text("今天") }
                    TextButton(onClick = { currentMonth = currentMonth.plusMonths(1); selectedDate = currentMonth.atDay(1) }) { Text("›", fontSize = 28.sp) }
                }
                Spacer(Modifier.height(12.dp))
                if (!canRead) {
                    Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("連結 Google 日曆", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("允許讀取此裝置已同步的日曆，行程就會顯示在月曆與桌面小工具。")
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }) { Text("授予日曆存取權") }
                        }
                    }
                } else if (calendars.isEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("找不到可顯示的日曆", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("請先在 Android 設定中加入並同步 Google 帳戶，再重新開啟此頁。")
                            TextButton(onClick = { refreshKey++ }) { Text("重新整理") }
                        }
                    }
                } else {
                    MonthCalendar(
                        month = currentMonth,
                        selectedDate = selectedDate,
                        events = events,
                        mondayFirst = mondayFirst,
                        dark = useDark,
                        onDateSelected = {
                            selectedDate = it
                            if (YearMonth.from(it) != currentMonth) currentMonth = YearMonth.from(it)
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    DayAgenda(date = selectedDate, events = eventsForDay(events, selectedDate), dark = useDark)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { openNewEvent(context, selectedDate) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("＋　新增行程", fontSize = 16.sp) }
                Spacer(Modifier.height(20.dp))
            }
        }
        if (showSettings) {
            CalendarSettingsDialog(
                calendars = calendars,
                selected = selectedCalendars,
                mondayFirst = mondayFirst,
                themeChoice = themeChoice,
                onCalendarChange = { id, checked ->
                    selectedCalendars = if (checked) selectedCalendars + id else selectedCalendars - id
                    CalendarPreferences.setSelectedCalendarIds(context, selectedCalendars)
                    refreshKey++
                },
                onWeekStartChange = {
                    mondayFirst = it
                    CalendarPreferences.setWeekStartsMonday(context, it)
                    refreshKey++
                },
                onThemeChange = {
                    themeChoice = it
                    CalendarPreferences.setTheme(context, it)
                },
                onRefresh = { refreshKey++ },
                onDismiss = { showSettings = false },
            )
        }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    mondayFirst: Boolean,
    dark: Boolean,
    onDateSelected: (LocalDate) -> Unit,
) {
    val days = remember(month, mondayFirst) { monthGrid(month.atDay(1), mondayFirst) }
    val byDate = remember(events, days) { CalendarRepository.eventsByDate(events, days) }
    val cell = if (dark) Color(0xFF20343C) else Color(0xFFFFFFFF)
    Card(colors = CardDefaults.cardColors(containerColor = cell), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                weekdayLabels(mondayFirst).forEach { label ->
                    Text(label, modifier = Modifier.weight(1f).height(32.dp).wrapCenter(), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, color = if (dark) Color(0xFFB8CDD3) else Color(0xFF657A80))
                }
            }
            days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        val dayEvents = byDate[date].orEmpty()
                        val chosen = date == selectedDate
                        val today = date == LocalDate.now()
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(62.dp)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (chosen) Color(0xFF7BD5F5) else Color.Transparent)
                                .clickable { onDateSelected(date) }
                                .padding(horizontal = 3.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                fontSize = 14.sp,
                                color = when {
                                    chosen -> Color(0xFF10252D)
                                    date.month != month.month -> if (dark) Color(0xFF73868C) else Color(0xFFADB9BC)
                                    today -> Color(0xFF2AA6D6)
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (today || chosen) FontWeight.Bold else FontWeight.Normal,
                            )
                            val event = dayEvents.firstOrNull()
                            if (event != null) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(event.color.takeIf { it != 0 } ?: 0xFF6085D8.toInt()).copy(alpha = if (chosen) 0.75f else 1f))
                                        .padding(horizontal = 2.dp, vertical = 1.dp),
                                ) {
                                    Text(event.title, fontSize = 8.sp, lineHeight = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                                }
                                if (dayEvents.size > 1) Text("+${dayEvents.size - 1}", fontSize = 8.sp, color = if (chosen) Color(0xFF17343D) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayAgenda(date: LocalDate, events: List<CalendarEvent>, dark: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(date.format(DateTimeFormatter.ofPattern("M 月 d 日　EEEE", Locale.TAIWAN)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (events.isEmpty()) {
            Text("這一天沒有行程", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            events.forEach { event ->
                Card(colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF1E3037) else Color.White), shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(event.color.takeIf { it != 0 } ?: 0xFF6085D8.toInt())))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(event.title, fontWeight = FontWeight.Medium)
                            Text(if (event.allDay) "全天" else formatTime(event.startMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarSettingsDialog(
    calendars: List<CalendarInfo>,
    selected: Set<Long>,
    mondayFirst: Boolean,
    themeChoice: ThemeChoice,
    onCalendarChange: (Long, Boolean) -> Unit,
    onWeekStartChange: (Boolean) -> Unit,
    onThemeChange: (ThemeChoice) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("行事曆設定", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState())) {
                Text("顯示的帳號與日曆", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                if (calendars.isEmpty()) {
                    Text("尚無可用日曆，請確認已授權並同步帳戶。")
                } else calendars.forEach { calendar ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onCalendarChange(calendar.id, calendar.id !in selected) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = calendar.id in selected, onCheckedChange = { onCalendarChange(calendar.id, it) })
                        Column(Modifier.weight(1f)) {
                            Text(calendar.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(calendar.accountName.ifBlank { calendar.accountType }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(calendar.color)))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("每週第一天", fontWeight = FontWeight.SemiBold)
                listOf(false to "週日", true to "週一").forEach { (monday, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onWeekStartChange(monday) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mondayFirst == monday, onClick = { onWeekStartChange(monday) })
                        Text(label)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("外觀", fontWeight = FontWeight.SemiBold)
                ThemeChoice.entries.forEach { choice ->
                    Row(Modifier.fillMaxWidth().clickable { onThemeChange(choice) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = themeChoice == choice, onClick = { onThemeChange(choice) })
                        Text(choice.label)
                    }
                }
                TextButton(onClick = onRefresh, modifier = Modifier.align(Alignment.End)) { Text("重新整理日曆") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

private fun eventsForDay(events: List<CalendarEvent>, date: LocalDate): List<CalendarEvent> =
    CalendarRepository.eventsByDate(events, listOf(date))[date].orEmpty()

private fun formatTime(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun openNewEvent(context: android.content.Context, date: LocalDate) {
    val begin = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        setPackage("com.google.android.calendar")
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 60 * 60 * 1000)
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(context, "找不到 Google 日曆，請先安裝或更新 Google 日曆", Toast.LENGTH_LONG).show() }
}

@Composable
private fun Modifier.wrapCenter(): Modifier = this.then(Modifier.padding(vertical = 4.dp))
