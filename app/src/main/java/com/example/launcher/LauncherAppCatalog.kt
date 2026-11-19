package com.example.launcher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class LauncherAppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val sectionLetter: String
)

object LauncherAppCatalog {

    private const val TAG = "LauncherAppCatalog"
    const val MAX_DOCK_VISIBLE = 5
    const val MAX_PINNED_SHORTCUTS = 15

    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

    private val preferredShortcutGroups: List<List<String>> = listOf(
        // Phone / Dialer
        listOf(
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.dialer",
            "com.android.phone"
        ),
        // Messages
        listOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms"
        ),
        // Browser
        listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.sec.android.app.sbrowser",
            "com.microsoft.emmx"
        ),
        // Camera
        listOf(
            "com.google.android.GoogleCamera",
            "com.sec.android.app.camera",
            "com.android.camera2",
            "com.android.camera"
        ),
        // Music / Clock
        listOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.apple.android.music",
            "com.google.android.deskclock",
            "com.sec.android.app.clockpackage"
        )
    )

    private val knownMusicPackages: List<String> = listOf(
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "com.apple.android.music",
        "com.amazon.mp3",
        "com.pandora.android",
        "com.soundcloud.android",
        "com.aspiro.tidal",
        "com.sec.android.app.music",
        "com.maxmpz.audioplayer",
        "org.videolan.vlc",
        "com.google.android.youtube"
    )

    fun loadLaunchableApps(context: Context): List<LauncherAppInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, 0)
            }
        }.getOrElse { e ->
            Log.w(TAG, "Failed to query launchable apps", e)
            emptyList()
        }

        val selfPkg = context.packageName
        val seenPackages = HashSet<String>()
        val result = ArrayList<LauncherAppInfo>(resolved.size)

        for (info in resolved) {
            val activityInfo = info.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            if (pkg == selfPkg || !seenPackages.add(pkg)) continue
            val rawLabel = runCatching { info.loadLabel(pm)?.toString() }.getOrNull().orEmpty().trim()
            val label = rawLabel.ifEmpty { pkg.substringAfterLast('.') }
            result.add(
                LauncherAppInfo(
                    packageName = pkg,
                    activityName = activityInfo.name ?: "",
                    label = label,
                    sectionLetter = computeSectionLetter(label)
                )
            )
        }

        return result.sortedWith(
            compareBy<LauncherAppInfo>(
                { it.sectionLetter == "#" },
                { it.label.lowercase(Locale.getDefault()) },
                { it.packageName }
            )
        )
    }

    fun computeSectionLetter(label: String): String {
        val first = label.trim().firstOrNull() ?: return "#"
        return if (first.isLetter()) {
            first.uppercaseChar().toString()
        } else {
            "#"
        }
    }

    fun filterApps(apps: List<LauncherAppInfo>, query: String): List<LauncherAppInfo> {
        val trimmed = query.trim().lowercase(Locale.getDefault())
        if (trimmed.isEmpty()) return apps

        val prefixMatches = ArrayList<LauncherAppInfo>()
        val containsMatches = ArrayList<LauncherAppInfo>()
        for (app in apps) {
            val lowerLabel = app.label.lowercase(Locale.getDefault())
            val lowerPkg = app.packageName.lowercase(Locale.getDefault())
            when {
                lowerLabel.startsWith(trimmed) -> prefixMatches.add(app)
                lowerLabel.contains(trimmed) || lowerPkg.contains(trimmed) -> containsMatches.add(app)
            }
        }
        return prefixMatches + containsMatches
    }

    fun groupAlphabetically(apps: List<LauncherAppInfo>): LinkedHashMap<String, List<LauncherAppInfo>> {
        val grouped = LinkedHashMap<String, MutableList<LauncherAppInfo>>()
        for (app in apps) {
            grouped.getOrPut(app.sectionLetter) { ArrayList() }.add(app)
        }
        val result = LinkedHashMap<String, List<LauncherAppInfo>>(grouped.size)
        grouped.forEach { (key, value) -> result[key] = value }
        return result
    }

    fun seedDefaultShortcuts(apps: List<LauncherAppInfo>): List<String> {
        if (apps.isEmpty()) return emptyList()
        val installedSet = apps.map { it.packageName }.toSet()
        val chosen = LinkedHashSet<String>()

        for (group in preferredShortcutGroups) {
            val match = group.firstOrNull { it in installedSet }
            if (match != null) {
                chosen.add(match)
            }
            if (chosen.size >= MAX_DOCK_VISIBLE) break
        }

        if (chosen.size < MAX_DOCK_VISIBLE) {
            for (app in apps) {
                chosen.add(app.packageName)
                if (chosen.size >= MAX_DOCK_VISIBLE) break
            }
        }
        return chosen.toList()
    }

    fun findDefaultMusicPackage(context: Context, installedApps: List<LauncherAppInfo>): String? {
        val pm = context.packageManager
        val musicIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MUSIC)
        }
        val resolved = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    musicIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(musicIntent, 0)
            }
        }.getOrDefault(emptyList())

        val resolvedPkg = resolved.firstOrNull { it.activityInfo?.packageName != context.packageName }
            ?.activityInfo?.packageName
        if (!resolvedPkg.isNullOrBlank()) {
            return resolvedPkg
        }

        val installedSet = installedApps.map { it.packageName }.toSet()
        return knownMusicPackages.firstOrNull { it in installedSet }
    }

    fun loadAppIconBitmap(context: Context, packageName: String, sizePx: Int = 96): ImageBitmap? {
        iconCache[packageName]?.let { return it }
        val drawable = runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: return null
        val bitmap = drawableToBitmap(drawable, sizePx) ?: return null
        val imageBitmap = bitmap.asImageBitmap()
        iconCache[packageName] = imageBitmap
        return imageBitmap
    }

    fun invalidateIcon(packageName: String) {
        iconCache.remove(packageName)
    }

    fun launchApp(context: Context, app: LauncherAppInfo): Boolean {
        val explicitIntent = if (app.activityName.isNotBlank()) {
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(app.packageName, app.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
        } else {
            null
        }
        if (explicitIntent != null) {
            try {
                context.startActivity(explicitIntent)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Explicit launch failed for ${app.packageName}, trying launch intent", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Explicit launch denied for ${app.packageName}, trying launch intent", e)
            }
        }
        return launchPackage(context, app.packageName)
    }

    fun launchPackage(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch package $packageName", e)
            false
        }
    }

    fun isDefaultHomeApp(context: Context): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolved = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveActivity(
                    homeIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        }.getOrNull()
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun openHomeSettings(context: Context): Boolean {
        val primary = Intent(Settings.ACTION_HOME_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(primary)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_HOME_SETTINGS unavailable, falling back to manage default apps", e)
        }
        val fallback = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(fallback)
            true
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_MANAGE_DEFAULT_APPS_SETTINGS unavailable", e)
            false
        }
    }

    private fun drawableToBitmap(drawable: Drawable, targetPx: Int): Bitmap? {
        return try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                val src = drawable.bitmap
                if (src.width <= targetPx && src.height <= targetPx) {
                    return src
                }
                return Bitmap.createScaledBitmap(src, targetPx, targetPx, true)
            }
            val bitmap = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Failed converting app drawable to bitmap", e)
            null
        }
    }
}
