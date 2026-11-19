package com.example.launcher

import android.app.admin.DeviceAdminReceiver

/**
 * Minimal DeviceAdminReceiver declaring only the force-lock policy so Nepho can turn the screen
 * off when the user double-taps the home screen.
 */
class ScreenLockAdminReceiver : DeviceAdminReceiver()
