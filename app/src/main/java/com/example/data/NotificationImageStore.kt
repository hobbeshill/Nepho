package com.example.data

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.IOException

/**
 * Copies chat attachments without re-encoding (preserving GIF animation), or saves
 * BigPictureStyle images, before notification URI grants are revoked on cancellation.
 */
object NotificationImageStore {
    private const val DIRECTORY = "notification_images"
    private const val TAG = "NotificationImageStore"

    @Suppress("DEPRECATION")
    fun capture(context: Context, key: String, notification: Notification): String? {
        val extras = notification.extras
        val messages = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(notification)?.messages.orEmpty()
        val attachment = messages.lastOrNull {
            it.dataMimeType?.startsWith("image/") == true && it.dataUri != null
        }
        if (attachment != null) {
            var file: File? = null
            try {
                file = newFile(context, key, "image")
                context.contentResolver.openInputStream(requireNotNull(attachment.dataUri)).use { input ->
                    if (input == null) throw IOException("Attachment stream unavailable")
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                return file.absolutePath
            } catch (e: IOException) {
                file?.delete()
                Log.e(TAG, "Failed to copy notification attachment", e)
            } catch (e: SecurityException) {
                file?.delete()
                Log.e(TAG, "Notification attachment access denied", e)
            }
        }
        val picture = extras.get(Notification.EXTRA_PICTURE) as? Bitmap
        if (picture != null) return save(context, key, picture)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val icon = extras.getParcelable<Icon>(Notification.EXTRA_PICTURE_ICON)
            if (icon != null) {
                try {
                    return icon.loadDrawable(context)?.toBitmap()?.let { save(context, key, it) }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Notification picture access denied", e)
                }
            }
        }
        return null
    }

    private fun newFile(context: Context, key: String, extension: String): File {
        val dir = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        return File.createTempFile("${key.hashCode() and 0x7FFFFFFF}_", ".$extension", dir)
    }

    fun save(context: Context, key: String, bitmap: Bitmap): String? {
        return try {
            val file = newFile(context, key, "png")
            file.outputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)) {
                    throw IOException("Notification picture encoding failed")
                }
            }
            file.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save notification picture", e)
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
