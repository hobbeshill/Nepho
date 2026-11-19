package com.example.launcher

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ClockAlarmHelper {

    private const val TAG = "ClockAlarmHelper"

    private val knownClockPackages = listOf(
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.android.deskclock",
        "com.oneplus.deskclock",
        "com.motorola.timeweatherwidget"
    )

    fun readNextAlarmSummary(context: Context): String? {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return null
        val nextAlarm = runCatching { alarmManager.nextAlarmClock }.getOrNull() ?: return null
        val triggerTime = nextAlarm.triggerTime
        if (triggerTime <= 0L) return null

        val is24Hour = DateFormat.is24HourFormat(context)
        val pattern = if (is24Hour) "EEE HH:mm" else "EEE h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(triggerTime))
    }

    fun buildSetAlarmIntent(
        hour: Int,
        minute: Int,
        label: String? = null,
        skipUi: Boolean = false
    ): Intent {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        return Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, safeHour)
            putExtra(AlarmClock.EXTRA_MINUTES, safeMinute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
            val cleanLabel = label?.trim().orEmpty()
            if (cleanLabel.isNotEmpty()) {
                putExtra(AlarmClock.EXTRA_MESSAGE, cleanLabel)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildShowAlarmsIntent(): Intent {
        return Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun launchSetAlarm(
        context: Context,
        hour: Int,
        minute: Int,
        label: String? = null
    ): Boolean {
        val intent = buildSetAlarmIntent(hour = hour, minute = minute, label = label, skipUi = false)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to handle ACTION_SET_ALARM", e)
            false
        }
    }

    fun openClockApp(context: Context): Boolean {
        val showAlarms = buildShowAlarmsIntent()
        try {
            context.startActivity(showAlarms)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_SHOW_ALARMS unavailable, trying fallback", e)
        }

        val showTimers = Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(showTimers)
            return true
        } catch (_: Exception) {
            // Fall through to known clock packages
        }

        for (pkg in knownClockPackages) {
            if (LauncherAppCatalog.launchPackage(context, pkg)) {
                return true
            }
        }
        return false
    }

    fun formatAlarmTime(hour: Int, minute: Int, is24Hour: Boolean): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
        }
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(cal.time)
    }
}
