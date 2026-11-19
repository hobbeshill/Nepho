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

class MainActivity : ComponentActivity() {

    private val viewModel: PulseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before rendering UI or creating ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        handleIncomingShareIntent(intent)

        setContent {
            NephoTheme {
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {
                    viewModel.refreshNotificationPermission()
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
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAccessAndPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
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
