package com.example.data

import android.content.Context
import android.graphics.Bitmap
import java.io.File

/**
 * Persists bitmaps attached to intercepted notifications (e.g. BigPictureStyle images)
 * into app-private storage so they can be rendered in the feed and thread popups after
 * the original notification has been cancelled.
 */
object NotificationImageStore {
    private const val DIRECTORY = "notification_images"

    fun save(context: Context, key: String, bitmap: Bitmap): String? {
        return try {
            val dir = File(context.filesDir, DIRECTORY).apply { mkdirs() }
            val safeName = "${(key.hashCode() and 0x7FFFFFFF)}_${System.currentTimeMillis()}.png"
            val file = File(dir, safeName)
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /** Deletes stored images that no longer belong to any notification row. */
    fun purgeOrphans(context: Context, referencedPaths: Set<String>) {
        try {
            File(context.filesDir, DIRECTORY).listFiles()?.forEach { file ->
                if (file.absolutePath !in referencedPaths) {
                    file.delete()
                }
            }
        } catch (_: Exception) {
            // Best-effort cleanup
        }
    }
}
