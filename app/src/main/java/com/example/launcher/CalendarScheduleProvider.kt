package com.example.launcher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CalendarEventItem(
    val eventId: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String = "",
    val calendarName: String = "",
    val calendarColor: Int? = null,
    val isGoogleCalendar: Boolean = true
)

data class CalendarDayGroup(
    val dayOffset: Int, // 0 = Today, 1 = Tomorrow, 2 = Next Day
    val dayTitle: String,
    val dateLabel: String,
    val events: List<CalendarEventItem>
)

data class CalendarScheduleState(
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val days: List<CalendarDayGroup> = emptyList(),
    val totalEventCount: Int = 0
)

object CalendarScheduleProvider {

    private const val TAG = "CalendarSchedule"
    private const val GOOGLE_CALENDAR_PACKAGE = "com.google.android.calendar"
    private const val GOOGLE_ACCOUNT_TYPE = "com.google"

    fun hasCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun loadThreeDaySchedule(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): CalendarScheduleState {
        if (!hasCalendarPermission(context)) {
            return CalendarScheduleState(hasPermission = false)
        }

        // Pad query bounds by 24h on each side so UTC-based all-day events are never clipped by timezone offsets.
        val day0Start = startOfLocalDay(nowMillis, 0, timeZone)
        val day3Start = startOfLocalDay(nowMillis, 3, timeZone)
        val queryStart = day0Start - 24 * 60 * 60 * 1000L
        val queryEnd = day3Start + 24 * 60 * 60 * 1000L

        val rawEvents = queryCalendarInstances(context, queryStart, queryEnd)

        // Prefer Google Calendar accounts if present; if a device has only local/other calendars, include them too.
        val hasGoogleEvents = rawEvents.any { it.isGoogleCalendar }
        val filteredEvents = if (hasGoogleEvents) {
            rawEvents.filter { it.isGoogleCalendar }
        } else {
            rawEvents
        }

        val days = groupEventsIntoThreeDays(
            events = filteredEvents,
            nowMillis = nowMillis,
            timeZone = timeZone
        )
        val uniqueEventCount = days.flatMap { it.events }.map { "${it.eventId}_${it.startMillis}" }.distinct().size
        return CalendarScheduleState(
            hasPermission = true,
            isLoading = false,
            days = days,
            totalEventCount = uniqueEventCount
        )
    }

    private fun queryCalendarInstances(
        context: Context,
        queryStartMillis: Long,
        queryEndMillis: Long
    ): List<CalendarEventItem> {
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, queryStartMillis)
        ContentUris.appendId(builder, queryEndMillis)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.VISIBLE
        )

        val items = ArrayList<CalendarEventItem>()
        try {
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val idxId = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val idxTitle = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val idxBegin = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val idxEnd = cursor.getColumnIndex(CalendarContract.Instances.END)
                val idxAllDay = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val idxLoc = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val idxCalName = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
                val idxColor = cursor.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)
                val idxAccType = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                val idxStatus = cursor.getColumnIndex(CalendarContract.Instances.STATUS)
                val idxSelfStatus = cursor.getColumnIndex(CalendarContract.Instances.SELF_ATTENDEE_STATUS)
                val idxVisible = cursor.getColumnIndex(CalendarContract.Instances.VISIBLE)

                while (cursor.moveToNext()) {
                    if (idxVisible >= 0 && cursor.getInt(idxVisible) == 0) continue
                    if (idxStatus >= 0 && cursor.getInt(idxStatus) == CalendarContract.Events.STATUS_CANCELED) continue
                    if (idxSelfStatus >= 0 &&
                        cursor.getInt(idxSelfStatus) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
                    ) continue

                    val eventId = if (idxId >= 0) cursor.getLong(idxId) else continue
                    val title = if (idxTitle >= 0) cursor.getString(idxTitle).orEmpty().trim() else ""
                    val begin = if (idxBegin >= 0) cursor.getLong(idxBegin) else continue
                    val end = if (idxEnd >= 0) cursor.getLong(idxEnd) else begin
                    val allDay = idxAllDay >= 0 && cursor.getInt(idxAllDay) != 0
                    val location = if (idxLoc >= 0) cursor.getString(idxLoc).orEmpty().trim() else ""
                    val calName = if (idxCalName >= 0) cursor.getString(idxCalName).orEmpty().trim() else ""
                    val color = if (idxColor >= 0 && !cursor.isNull(idxColor)) cursor.getInt(idxColor) else null
                    val accountType = if (idxAccType >= 0) cursor.getString(idxAccType).orEmpty() else ""

                    items.add(
                        CalendarEventItem(
                            eventId = eventId,
                            title = title.ifEmpty { "(No title)" },
                            startMillis = begin,
                            endMillis = maxOf(end, begin),
                            allDay = allDay,
                            location = location,
                            calendarName = calName,
                            calendarColor = color,
                            isGoogleCalendar = accountType.equals(GOOGLE_ACCOUNT_TYPE, ignoreCase = true) ||
                                accountType.contains("google", ignoreCase = true)
                        )
                    )
                }
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Calendar permission revoked while querying instances", se)
        } catch (e: Exception) {
            Log.w(TAG, "Failed querying calendar instances", e)
        }
        return items
    }

    fun groupEventsIntoThreeDays(
        events: List<CalendarEventItem>,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): List<CalendarDayGroup> {
        val dayNameFmt = SimpleDateFormat("EEEE", locale).apply { this.timeZone = timeZone }
        val dateFmt = SimpleDateFormat("MMM d", locale).apply { this.timeZone = timeZone }

        return (0..2).map { offset ->
            val dayStart = startOfLocalDay(nowMillis, offset, timeZone)
            val dayEnd = startOfLocalDay(nowMillis, offset + 1, timeZone)
            val dayDate = Date(dayStart)

            val dayTitle = when (offset) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> dayNameFmt.format(dayDate)
            }
            val dateLabel = if (offset <= 1) {
                "${SimpleDateFormat("EEE", locale).apply { this.timeZone = timeZone }.format(dayDate)}, ${dateFmt.format(dayDate)}"
            } else {
                dateFmt.format(dayDate)
            }

            val dayEvents = events
                .filter { event -> eventOccursOnDay(event, dayStart, dayEnd, timeZone) }
                .distinctBy { "${it.eventId}_${it.startMillis}" }
                .sortedWith(
                    compareBy<CalendarEventItem>(
                        { !it.allDay },
                        { it.startMillis },
                        { it.title.lowercase(locale) }
                    )
                )

            CalendarDayGroup(
                dayOffset = offset,
                dayTitle = dayTitle,
                dateLabel = dateLabel,
                events = dayEvents
            )
        }
    }

    private fun eventOccursOnDay(
        event: CalendarEventItem,
        localDayStart: Long,
        localDayEnd: Long,
        timeZone: TimeZone
    ): Boolean {
        return if (event.allDay) {
            // Android CalendarContract stores all-day event timestamps at UTC midnight.
            val localStart = utcMidnightToLocalMidnight(event.startMillis, timeZone)
            val rawEnd = utcMidnightToLocalMidnight(event.endMillis, timeZone)
            val localEnd = if (rawEnd > localStart) rawEnd else localStart + 24 * 60 * 60 * 1000L
            localStart < localDayEnd && localEnd > localDayStart
        } else {
            if (event.endMillis <= event.startMillis) {
                event.startMillis in localDayStart until localDayEnd
            } else {
                event.startMillis < localDayEnd && event.endMillis > localDayStart
            }
        }
    }

    private fun utcMidnightToLocalMidnight(utcMillis: Long, localTz: TimeZone): Long {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        return Calendar.getInstance(localTz).apply {
            clear()
            set(
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH),
                utcCal.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun startOfLocalDay(nowMillis: Long, offsetDays: Int, timeZone: TimeZone): Long {
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (offsetDays != 0) {
                add(Calendar.DAY_OF_YEAR, offsetDays)
            }
        }.timeInMillis
    }

    fun formatEventTimeRange(
        event: CalendarEventItem,
        is24Hour: Boolean,
        allDayLabel: String = "All day",
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): String {
        if (event.allDay) return allDayLabel
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        val fmt = SimpleDateFormat(pattern, locale).apply { this.timeZone = timeZone }
        val startStr = fmt.format(Date(event.startMillis))
        if (event.endMillis <= event.startMillis) return startStr
        val endStr = fmt.format(Date(event.endMillis))
        return "$startStr – $endStr"
    }

    fun openEventInCalendar(context: Context, event: CalendarEventItem): Boolean {
        val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = eventUri
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open calendar event ${event.eventId}, falling back to Calendar app", e)
            openGoogleCalendarApp(context)
        }
    }

    fun openGoogleCalendarApp(context: Context): Boolean {
        // 1. Prefer Google Calendar directly if installed
        if (LauncherAppCatalog.launchPackage(context, GOOGLE_CALENDAR_PACKAGE)) {
            return true
        }
        // 2. Fallback to Android Calendar time URI
        val timeUri = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(System.currentTimeMillis().toString())
            .build()
        val viewTimeIntent = Intent(Intent.ACTION_VIEW, timeUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(viewTimeIntent)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Calendar time URI view unavailable", e)
        }
        // 3. Fallback to CATEGORY_APP_CALENDAR
        val appCalIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_CALENDAR)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(appCalIntent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "No calendar app available to open", e)
            false
        }
    }
}
