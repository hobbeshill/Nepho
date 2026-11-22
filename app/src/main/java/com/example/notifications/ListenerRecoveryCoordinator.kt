package com.example.notifications

import android.content.Context
import android.util.Log
import com.example.data.CaptureOutcome
import com.example.data.PulseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pure deterministic rules governing automatic yellow-light listener recovery so all
 * cooldowns, foreground checks, and loop guards can be verified directly in JVM unit tests.
 */
internal object ListenerRecoveryPolicy {
    const val DEBOUNCE_DELAY_MS: Long = 1_500L
    const val STAGE_TWO_WAIT_MS: Long = 4_000L
    const val STAGE_THREE_WAIT_MS: Long = 6_000L
    const val POLL_INTERVAL_MS: Long = 250L
    const val SILENT_RECOVERY_COOLDOWN_MS: Long = 30_000L
    const val AUTO_RESTART_COOLDOWN_MS: Long = 10L * 60L * 1_000L

    fun isYellowDisconnected(
        isAccessGranted: Boolean,
        isListenerConnected: Boolean
    ): Boolean = isAccessGranted && !isListenerConnected

    fun canRunSilentRecovery(
        isAccessGranted: Boolean,
        isListenerConnected: Boolean,
        nowMs: Long,
        lastSilentAttemptMs: Long
    ): Boolean {
        if (!isYellowDisconnected(isAccessGranted, isListenerConnected)) return false
        return lastSilentAttemptMs <= 0L || (nowMs - lastSilentAttemptMs) >= SILENT_RECOVERY_COOLDOWN_MS
    }

    fun canTriggerAutoRestart(
        isAccessGranted: Boolean,
        isListenerConnected: Boolean,
        isForeground: Boolean,
        nowMs: Long,
        lastAutoRestartMs: Long
    ): Boolean {
        if (!isForeground) return false
        if (!isYellowDisconnected(isAccessGranted, isListenerConnected)) return false
        return lastAutoRestartMs <= 0L || (nowMs - lastAutoRestartMs) >= AUTO_RESTART_COOLDOWN_MS
    }
}

enum class RecoveryTrigger(val label: String) {
    DISCONNECT("listener disconnect"),
    APP_RESUME("app resume"),
    BACKGROUND_WATCHDOG("periodic watchdog")
}

/**
 * Coordinates automatic recovery whenever Notification Access is granted but Android has
 * unbound [NephoNotificationListener] (yellow status dot):
 * - Stage 1: Silent `requestRebind()` after a brief debounce.
 * - Stage 2: Silent component reset + `requestRebind()` if still disconnected.
 * - Stage 3: Single foreground-only clean app restart (guarded by a persisted 10-minute
 *   cooldown that clears immediately once the green connected state returns).
 */
object ListenerRecoveryCoordinator {

    private const val TAG = "ListenerRecovery"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val _isReconnecting = MutableStateFlow(false)
    val isReconnectingFlow: StateFlow<Boolean> = _isReconnecting.asStateFlow()

    private val _foregroundRestartRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val foregroundRestartRequests: SharedFlow<Unit> = _foregroundRestartRequests.asSharedFlow()

    @Volatile
    private var isForeground: Boolean = false

    @Volatile
    private var lastSilentAttemptMs: Long = 0L

    @Volatile
    private var activeRecoveryJob: Job? = null

    fun setForeground(foreground: Boolean) {
        isForeground = foreground
    }

    fun onListenerConnected(context: Context) {
        val appCtx = context.applicationContext
        synchronized(lock) {
            activeRecoveryJob?.cancel()
            activeRecoveryJob = null
            lastSilentAttemptMs = 0L
            _isReconnecting.value = false
        }
        scope.launch {
            runCatching {
                PulseRepository.getInstance(appCtx)
                    .sessionPreferences
                    .setLastAutoRestartAtMillis(0L)
            }.onFailure { e ->
                Log.w(TAG, "Failed clearing auto-restart cooldown on reconnect", e)
            }
        }
    }

    fun onYellowStatusDetected(
        context: Context,
        trigger: RecoveryTrigger,
        allowForegroundRestart: Boolean = isForeground
    ) {
        val appCtx = context.applicationContext
        val accessGranted = NephoNotificationListener.isListenerEnabled(appCtx)
        val connected = NephoNotificationListener.listenerConnectedFlow.value
        val nowMs = System.currentTimeMillis()

        synchronized(lock) {
            if (activeRecoveryJob?.isActive == true) return
            if (!ListenerRecoveryPolicy.canRunSilentRecovery(
                    isAccessGranted = accessGranted,
                    isListenerConnected = connected,
                    nowMs = nowMs,
                    lastSilentAttemptMs = lastSilentAttemptMs
                )
            ) {
                return
            }
            lastSilentAttemptMs = nowMs
            _isReconnecting.value = true

            var launchedJob: Job? = null
            launchedJob = scope.launch {
                try {
                    runEscalatingRecovery(
                        appCtx = appCtx,
                        trigger = trigger,
                        debounceMs = ListenerRecoveryPolicy.DEBOUNCE_DELAY_MS,
                        allowForegroundRestart = allowForegroundRestart
                    )
                } finally {
                    synchronized(lock) {
                        if (activeRecoveryJob === launchedJob) {
                            activeRecoveryJob = null
                            _isReconnecting.value = false
                        }
                    }
                }
            }
            activeRecoveryJob = launchedJob
        }
    }

    /**
     * Background-safe suspend recovery used by the periodic WorkManager watchdog.
     * Runs silent Stage 1 and Stage 2 only—never launches an Activity from the background.
     */
    suspend fun runWatchdogCheck(context: Context): Boolean {
        val appCtx = context.applicationContext
        val accessGranted = NephoNotificationListener.isListenerEnabled(appCtx)
        val connected = NephoNotificationListener.listenerConnectedFlow.value
        val nowMs = System.currentTimeMillis()

        val shouldRun = synchronized(lock) {
            if (activeRecoveryJob?.isActive == true || _isReconnecting.value) {
                false
            } else if (!ListenerRecoveryPolicy.canRunSilentRecovery(
                    isAccessGranted = accessGranted,
                    isListenerConnected = connected,
                    nowMs = nowMs,
                    lastSilentAttemptMs = lastSilentAttemptMs
                )
            ) {
                false
            } else {
                lastSilentAttemptMs = nowMs
                _isReconnecting.value = true
                true
            }
        }
        if (!shouldRun) return false

        return try {
            runEscalatingRecovery(
                appCtx = appCtx,
                trigger = RecoveryTrigger.BACKGROUND_WATCHDOG,
                debounceMs = 0L,
                allowForegroundRestart = false
            )
            true
        } finally {
            synchronized(lock) {
                if (activeRecoveryJob == null) {
                    _isReconnecting.value = false
                }
            }
        }
    }

    private suspend fun runEscalatingRecovery(
        appCtx: Context,
        trigger: RecoveryTrigger,
        debounceMs: Long,
        allowForegroundRestart: Boolean
    ) {
        if (debounceMs > 0L) {
            if (!waitWhileStillYellow(appCtx, debounceMs)) return
        }
        if (!isStillYellow(appCtx)) return

        val repository = PulseRepository.getInstance(appCtx)

        // Stage 1: Silent soft rebind
        val stage1Ok = NephoNotificationListener.requestRebindService(appCtx)
        repository.recordCaptureDiagnostic(
            packageName = appCtx.packageName,
            appLabel = "Nepho",
            titlePreview = "Auto-reconnecting listener",
            outcome = CaptureOutcome.LISTENER_RECONNECTING,
            detail = if (stage1Ok) {
                "Auto-recovery (${trigger.label}): requested Android to rebind the notification listener."
            } else {
                "Auto-recovery (${trigger.label}): soft rebind request failed; preparing listener reset."
            }
        )

        if (!waitWhileStillYellow(appCtx, ListenerRecoveryPolicy.STAGE_TWO_WAIT_MS)) return

        // Stage 2: Silent component cycle + rebind
        val stage2Ok = NephoNotificationListener.resetAndRebindService(appCtx)
        repository.recordCaptureDiagnostic(
            packageName = appCtx.packageName,
            appLabel = "Nepho",
            titlePreview = "Listener reset & rebind",
            outcome = CaptureOutcome.LISTENER_RECONNECTING,
            detail = if (stage2Ok) {
                "Auto-recovery (${trigger.label}): listener still disconnected; cycled service registration and requested rebind."
            } else {
                "Auto-recovery (${trigger.label}): listener reset attempt could not complete."
            }
        )

        if (!allowForegroundRestart) return

        // Stage 3: Foreground-only clean app restart if still disconnected and cooldown permits
        if (!waitWhileStillYellow(appCtx, ListenerRecoveryPolicy.STAGE_THREE_WAIT_MS)) return

        val nowMs = System.currentTimeMillis()
        val lastAutoRestartMs = runCatching {
            repository.sessionPreferences.getLastAutoRestartAtMillis()
        }.getOrDefault(0L)

        val shouldRestart = ListenerRecoveryPolicy.canTriggerAutoRestart(
            isAccessGranted = NephoNotificationListener.isListenerEnabled(appCtx),
            isListenerConnected = NephoNotificationListener.listenerConnectedFlow.value,
            isForeground = isForeground,
            nowMs = nowMs,
            lastAutoRestartMs = lastAutoRestartMs
        )

        if (shouldRestart) {
            repository.sessionPreferences.setLastAutoRestartAtMillis(nowMs)
            repository.recordCaptureDiagnostic(
                packageName = appCtx.packageName,
                appLabel = "Nepho",
                titlePreview = "Auto-restarting Nepho",
                outcome = CaptureOutcome.LISTENER_AUTO_RESTARTED,
                detail = "Auto-recovery (${trigger.label}): silent rebind did not restore connection; cleanly restarting Nepho."
            )
            _foregroundRestartRequests.tryEmit(Unit)
        }
    }

    private suspend fun waitWhileStillYellow(context: Context, durationMs: Long): Boolean {
        var elapsed = 0L
        while (elapsed < durationMs) {
            val step = minOf(ListenerRecoveryPolicy.POLL_INTERVAL_MS, durationMs - elapsed)
            delay(step)
            elapsed += step
            if (!isStillYellow(context)) return false
        }
        return isStillYellow(context)
    }

    private fun isStillYellow(context: Context): Boolean =
        ListenerRecoveryPolicy.isYellowDisconnected(
            isAccessGranted = NephoNotificationListener.isListenerEnabled(context),
            isListenerConnected = NephoNotificationListener.listenerConnectedFlow.value
        )
}
