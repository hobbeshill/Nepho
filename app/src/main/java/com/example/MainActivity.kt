package com.example

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.ai.configureAppCheck
import com.example.data.SupportedApp
import com.example.ui.NephoApp
import com.example.ui.PulseViewModel
import com.example.ui.theme.NephoTheme
import com.example.widget.NephoUnreadWidgetProvider

class MainActivity : ComponentActivity() {

    private val viewModel: PulseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before rendering UI or creating ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        handleIncomingShareIntent(intent)
        handleWidgetIntent(intent)

        setContent {
            NephoTheme {
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {
                    viewModel.refreshNotificationPermission()
                }
                val coarseLocationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        viewModel.useCurrentLocationForWeather()
                    } else {
                        viewModel.notifyLocationPermissionDenied()
                    }
                }
                val calendarPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        viewModel.refreshCalendarSchedule()
                    } else {
                        viewModel.notifyCalendarPermissionDenied()
                    }
                }
                val screenLockAdminLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    val enabled = com.example.launcher.HomeGestureActions.isScreenLockEnabled(this)
                    viewModel.onScreenLockAdminResult(enabled)
                }
                val launchScreenLockAdminPrompt: () -> Unit = {
                    runCatching {
                        screenLockAdminLauncher.launch(
                            com.example.launcher.HomeGestureActions.buildActivateScreenLockIntent(this)
                        )
                    }.onFailure { e ->
                        Log.w("MainActivity", "Device admin activation unavailable", e)
                        viewModel.notifyScreenLockUnavailable()
                    }
                }

                NephoApp(
                    viewModel = viewModel,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.refreshNotificationPermission()
                        }
                    },
                    onOpenNotificationAccessSettings = {
                        try {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (e: ActivityNotFoundException) {
                            Log.w("MainActivity", "Notification access settings unavailable", e)
                            Toast.makeText(
                                this,
                                R.string.notification_access_settings_unavailable,
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: SecurityException) {
                            Log.w("MainActivity", "Notification access settings restricted", e)
                            Toast.makeText(
                                this,
                                R.string.notification_access_settings_unavailable,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onRequestWeatherLocation = {
                        if (com.example.launcher.WeatherService.hasCoarseLocationPermission(this)) {
                            viewModel.useCurrentLocationForWeather()
                        } else {
                            coarseLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                    onRequestCalendarPermission = {
                        if (com.example.launcher.CalendarScheduleProvider.hasCalendarPermission(this)) {
                            viewModel.refreshCalendarSchedule()
                        } else {
                            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }
                    },
                    onDoubleTapScreenOff = {
                        when (com.example.launcher.HomeGestureActions.turnScreenOff(this)) {
                            com.example.launcher.ScreenLockResult.LOCKED -> Unit
                            com.example.launcher.ScreenLockResult.NEEDS_ADMIN_ACTIVATION -> launchScreenLockAdminPrompt()
                            com.example.launcher.ScreenLockResult.UNAVAILABLE -> viewModel.notifyScreenLockUnavailable()
                        }
                    },
                    onEnableScreenLockAdmin = launchScreenLockAdminPrompt
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAccessAndPermissions()
        viewModel.refreshLauncherState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleHomeIntent(intent)
        handleIncomingShareIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleHomeIntent(intent: Intent?) {
        if (intent == null) return
        val isHomeOrMainReturn = intent.action == Intent.ACTION_MAIN &&
            (intent.hasCategory(Intent.CATEGORY_HOME) ||
                (intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
                    !intent.hasExtra(NephoUnreadWidgetProvider.EXTRA_WIDGET_THREAD_ID) &&
                    !intent.hasExtra(NephoUnreadWidgetProvider.EXTRA_WIDGET_APP_PACKAGE)))
        if (isHomeOrMainReturn) {
            viewModel.onHomeButtonPressed()
        }
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent == null) return
        val threadId = intent.getStringExtra(NephoUnreadWidgetProvider.EXTRA_WIDGET_THREAD_ID)
        if (!threadId.isNullOrBlank()) {
            viewModel.openThreadFromWidget(threadId)
            intent.removeExtra(NephoUnreadWidgetProvider.EXTRA_WIDGET_THREAD_ID)
            return
        }
        val appPackage = intent.getStringExtra(NephoUnreadWidgetProvider.EXTRA_WIDGET_APP_PACKAGE)
        if (!appPackage.isNullOrBlank()) {
            val appLabel = intent.getStringExtra(NephoUnreadWidgetProvider.EXTRA_WIDGET_APP_LABEL)
            val unreadOnly = intent.getBooleanExtra(NephoUnreadWidgetProvider.EXTRA_WIDGET_UNREAD_ONLY, true)
            viewModel.applyAppFilterFromWidget(
                packageName = appPackage,
                appLabel = appLabel,
                unreadOnly = unreadOnly
            )
            intent.removeExtra(NephoUnreadWidgetProvider.EXTRA_WIDGET_APP_PACKAGE)
            intent.removeExtra(NephoUnreadWidgetProvider.EXTRA_WIDGET_APP_LABEL)
            intent.removeExtra(NephoUnreadWidgetProvider.EXTRA_WIDGET_UNREAD_ONLY)
        }
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        if (sharedText.isBlank() && sharedSubject.isNullOrBlank()) return

        val detectedApp = SupportedApp.detectFromSharedContent(sharedSubject, sharedText)
        viewModel.ingestSharedOrCustomMessage(
            app = detectedApp,
            title = sharedSubject ?: "${detectedApp.displayName} Shared Alert",
            body = sharedText,
            sourceChannel = "SHARE_SHEET"
        )
        intent.action = null
    }
}
