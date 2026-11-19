package com.example.launcher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.notifications.NephoNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaPlaybackState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val packageName: String? = null,
    val appLabel: String = "",
    val artworkBitmap: ImageBitmap? = null,
    val canSkipNext: Boolean = true,
    val canSkipPrevious: Boolean = true,
    val hasSessionAccess: Boolean = false
)

data class MediaSessionCandidate(
    val packageName: String?,
    val isPlaying: Boolean,
    val hasTitle: Boolean
)

class MediaPlaybackController(private val appContext: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listenerComponent = ComponentName(appContext, NephoNotificationListener::class.java)
    private val mediaSessionManager: MediaSessionManager? =
        appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    private val _state = MutableStateFlow(MediaPlaybackState())
    val state: StateFlow<MediaPlaybackState> = _state.asStateFlow()

    @Volatile
    private var preferredPackage: String? = null

    private var activeController: MediaController? = null
    private var isListeningSessions = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publishFromController(activeController, hasSessionAccess = true)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishFromController(activeController, hasSessionAccess = true)
        }

        override fun onSessionDestroyed() {
            refresh()
        }
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        bindBestController(controllers.orEmpty(), hasSessionAccess = true)
    }

    fun setPreferredPackage(packageName: String?) {
        val cleaned = packageName?.trim()?.takeIf { it.isNotEmpty() }
        if (preferredPackage == cleaned) return
        preferredPackage = cleaned
        refresh()
    }

    fun refresh() {
        val manager = mediaSessionManager
        if (manager == null) {
            _state.value = MediaPlaybackState(hasSessionAccess = false)
            return
        }

        try {
            val controllers = manager.getActiveSessions(listenerComponent)
            if (!isListeningSessions) {
                runCatching {
                    manager.addOnActiveSessionsChangedListener(
                        sessionsChangedListener,
                        listenerComponent,
                        mainHandler
                    )
                    isListeningSessions = true
                }
            }
            bindBestController(controllers, hasSessionAccess = true)
        } catch (se: SecurityException) {
            // Notification Access not granted yet; degrade gracefully without crashing.
            detachActiveController()
            isListeningSessions = false
            _state.value = MediaPlaybackState(hasSessionAccess = false)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to query active media sessions", e)
            detachActiveController()
            _state.value = MediaPlaybackState(hasSessionAccess = false)
        }
    }

    fun togglePlayPause() {
        val controller = activeController ?: run {
            refresh()
            activeController
        } ?: return
        val playbackState = controller.playbackState?.state
        runCatching {
            if (playbackState == PlaybackState.STATE_PLAYING || playbackState == PlaybackState.STATE_BUFFERING) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        }.onFailure { Log.w(TAG, "togglePlayPause failed", it) }
    }

    fun skipNext() {
        val controller = activeController ?: return
        runCatching { controller.transportControls.skipToNext() }
            .onFailure { Log.w(TAG, "skipNext failed", it) }
    }

    fun skipPrevious() {
        val controller = activeController ?: return
        runCatching { controller.transportControls.skipToPrevious() }
            .onFailure { Log.w(TAG, "skipPrevious failed", it) }
    }

    fun openActiveMediaApp(
        context: Context,
        preferredMusicPackage: String? = null,
        fallbackMusicPackage: String? = null
    ): Boolean {
        val controller = activeController
        if (controller != null) {
            val sessionActivity = runCatching { controller.sessionActivity }.getOrNull()
            if (sessionActivity != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val options = ActivityOptions.makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            )
                        sessionActivity.send(context, 0, null, null, null, null, options.toBundle())
                    } else {
                        sessionActivity.send()
                    }
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "Session PendingIntent failed for ${controller.packageName}, falling back to launch", e)
                }
            }

            val pkg = controller.packageName
            if (!pkg.isNullOrBlank() && LauncherAppCatalog.launchPackage(context, pkg)) {
                return true
            }
        }

        val preferred = preferredMusicPackage ?: preferredPackage
        if (!preferred.isNullOrBlank() &&
            LauncherAppCatalog.launchPackage(context, preferred)
        ) {
            return true
        }

        if (!fallbackMusicPackage.isNullOrBlank() &&
            LauncherAppCatalog.launchPackage(context, fallbackMusicPackage)
        ) {
            return true
        }

        val musicIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MUSIC)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(musicIntent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "No music app available to open", e)
            false
        }
    }

    fun release() {
        detachActiveController()
        if (isListeningSessions) {
            runCatching {
                mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            }
            isListeningSessions = false
        }
    }

    private fun bindBestController(controllers: List<MediaController>, hasSessionAccess: Boolean) {
        val candidates = controllers.map { ctrl ->
            val st = ctrl.playbackState?.state
            val isPlaying = st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
            val meta = ctrl.metadata
            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            MediaSessionCandidate(
                packageName = ctrl.packageName,
                isPlaying = isPlaying,
                hasTitle = !title.isNullOrBlank()
            )
        }
        val bestIndex = selectBestControllerIndex(candidates, preferredPackage)
        val best = controllers.getOrNull(bestIndex)

        if (activeController?.sessionToken != best?.sessionToken) {
            detachActiveController()
            activeController = best
            if (best != null) {
                runCatching { best.registerCallback(controllerCallback, mainHandler) }
            }
        }
        publishFromController(best, hasSessionAccess = hasSessionAccess)
    }

    private fun detachActiveController() {
        val old = activeController
        activeController = null
        if (old != null) {
            runCatching { old.unregisterCallback(controllerCallback) }
        }
    }

    private fun publishFromController(controller: MediaController?, hasSessionAccess: Boolean) {
        if (controller == null) {
            _state.value = MediaPlaybackState(
                isActive = false,
                hasSessionAccess = hasSessionAccess
            )
            return
        }

        val metadata = runCatching { controller.metadata }.getOrNull()
        val playback = runCatching { controller.playbackState }.getOrNull()
        val pkg = controller.packageName
        val appLabel = resolveAppLabel(pkg)

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: appLabel.ifBlank { "Active Media" }
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""

        val rawBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val artwork = rawBitmap?.let { scaleArtwork(it).asImageBitmap() }

        val pbState = playback?.state ?: PlaybackState.STATE_NONE
        val isPlaying = pbState == PlaybackState.STATE_PLAYING || pbState == PlaybackState.STATE_BUFFERING
        val actions = playback?.actions ?: 0L
        val canSkipNext = actions == 0L || (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L
        val canSkipPrevious = actions == 0L || (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L

        _state.value = MediaPlaybackState(
            isActive = true,
            isPlaying = isPlaying,
            title = title,
            artist = artist,
            album = album,
            packageName = pkg,
            appLabel = appLabel,
            artworkBitmap = artwork,
            canSkipNext = canSkipNext,
            canSkipPrevious = canSkipPrevious,
            hasSessionAccess = hasSessionAccess
        )
    }

    private fun resolveAppLabel(packageName: String?): String {
        if (packageName.isNullOrBlank()) return ""
        return runCatching {
            val appInfo = appContext.packageManager.getApplicationInfo(packageName, 0)
            appContext.packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName.substringAfterLast('.'))
    }

    private fun scaleArtwork(source: Bitmap, maxDim: Int = 256): Bitmap {
        if (source.width <= maxDim && source.height <= maxDim) return source
        val ratio = minOf(maxDim.toFloat() / source.width, maxDim.toFloat() / source.height)
        val w = (source.width * ratio).toInt().coerceAtLeast(1)
        val h = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    companion object {
        private const val TAG = "MediaPlaybackController"

        fun selectBestControllerIndex(
            candidates: List<MediaSessionCandidate>,
            preferredPackage: String?
        ): Int {
            if (candidates.isEmpty()) return -1
            if (!preferredPackage.isNullOrBlank()) {
                val preferredPlaying = candidates.indexOfFirst {
                    it.packageName == preferredPackage && it.isPlaying
                }
                if (preferredPlaying >= 0) return preferredPlaying

                val preferredAny = candidates.indexOfFirst {
                    it.packageName == preferredPackage
                }
                if (preferredAny >= 0) return preferredAny
            }

            val anyPlaying = candidates.indexOfFirst { it.isPlaying }
            if (anyPlaying >= 0) return anyPlaying

            val anyWithTitle = candidates.indexOfFirst { it.hasTitle }
            if (anyWithTitle >= 0) return anyWithTitle

            return 0
        }
    }
}
