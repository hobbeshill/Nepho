package com.example.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.R
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID

/**
 * Local cached copy of a GIF selected from Gboard/Samsung Keyboard or the device photo picker,
 * together with its durable [FileProvider] content URI ready for sharing.
 */
data class CachedGifAttachment(
    val filePath: String,
    val shareUri: Uri
)

data class DirectShareIntentSpec(
    val intent: Intent,
    val usedChooserFallback: Boolean
)

/**
 * Immediately copies keyboard and picker GIF streams into Nepho's private cache (preserving
 * original animated bytes) and wraps them with [FileProvider] so temporary IME URI grants
 * never cause SecurityExceptions when sharing into Messages or X (Twitter).
 */
object GifReplyStore {
    const val DIRECTORY = "shared_gifs"
    const val MAX_CACHED_GIFS = 30
    private const val MAX_GIF_BYTES = 25L * 1024L * 1024L
    private const val COPY_BUFFER_BYTES = 8 * 1024
    private const val TAG = "GifReplyStore"

    private val DIRECT_SHARE_ALWAYS_PACKAGES = setOf(
        SupportedApp.MESSAGES.packageName,
        SupportedApp.TWITTER.packageName
    )

    fun fileProviderAuthority(context: Context): String =
        "${context.packageName}.fileprovider"

    /**
     * Messages and X (Twitter) always use direct ACTION_SEND sharing for GIFs so animated files
     * hand off reliably; other apps fall back to direct share when their RemoteInput is text-only.
     */
    fun shouldDirectShareGif(packageName: String, supportsNativeGifReply: Boolean): Boolean =
        packageName in DIRECT_SHARE_ALWAYS_PACKAGES || !supportsNativeGifReply

    fun isDirectSharePreferredApp(packageName: String): Boolean =
        packageName in DIRECT_SHARE_ALWAYS_PACKAGES

    /**
     * Copies raw bytes from [sourceUri] into `cacheDir/shared_gifs/` without re-encoding and
     * returns a [CachedGifAttachment] backed by [FileProvider].
     */
    fun cacheGifFromUri(context: Context, sourceUri: Uri): CachedGifAttachment? {
        val dir = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val targetFile = File(dir, "gif_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.gif")
        return try {
            val inputStream = if (sourceUri.scheme == "file") {
                sourceUri.path?.let { FileInputStream(File(it)) }
            } else {
                context.contentResolver.openInputStream(sourceUri)
            } ?: throw IOException("GIF input stream unavailable for $sourceUri")

            var totalBytes = 0L
            inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        if (totalBytes > MAX_GIF_BYTES) {
                            throw IOException("GIF exceeds maximum supported size")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (totalBytes <= 0L) {
                targetFile.delete()
                return null
            }
            val shareUri = FileProvider.getUriForFile(
                context,
                fileProviderAuthority(context),
                targetFile
            )
            pruneOldGifs(context, preserveFile = targetFile)
            CachedGifAttachment(
                filePath = targetFile.absolutePath,
                shareUri = shareUri
            )
        } catch (e: IOException) {
            targetFile.delete()
            Log.e(TAG, "Failed to cache GIF attachment", e)
            null
        } catch (e: SecurityException) {
            targetFile.delete()
            Log.e(TAG, "Access denied while caching GIF attachment", e)
            null
        } catch (e: IllegalArgumentException) {
            targetFile.delete()
            Log.e(TAG, "Invalid GIF URI or FileProvider path", e)
            null
        } catch (e: UnsupportedOperationException) {
            targetFile.delete()
            Log.e(TAG, "Unsupported GIF URI stream", e)
            null
        }
    }

    /**
     * Returns a [FileProvider] URI for an already-cached file inside `cacheDir/shared_gifs/`.
     */
    fun getShareUriForCachedFile(context: Context, filePath: String): Uri? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
        }.onFailure { error ->
            Log.w(TAG, "Could not resolve FileProvider URI for $filePath", error)
        }.getOrNull()
    }

    /**
     * Builds an [Intent.ACTION_SEND] intent carrying the animated GIF (`image/gif`) + optional
     * caption text, targeted directly to [targetPackage] when resolvable, with an automatic
     * [Intent.createChooser] fallback if the package cannot handle direct GIF sharing.
     */
    fun buildDirectShareIntent(
        context: Context,
        targetPackage: String,
        targetAppLabel: String,
        gifUri: Uri,
        caption: String?
    ): DirectShareIntentSpec {
        val cleanCaption = caption?.trim().orEmpty()
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = PulseRepository.GIF_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, gifUri)
            if (cleanCaption.isNotEmpty()) {
                putExtra(Intent.EXTRA_TEXT, cleanCaption)
            }
            clipData = ClipData.newRawUri(targetAppLabel, gifUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(targetPackage)
        }

        val resolvesTarget = runCatching {
            context.packageManager.resolveActivity(sendIntent, 0) != null
        }.getOrDefault(false)

        if (resolvesTarget) {
            runCatching {
                context.grantUriPermission(
                    targetPackage,
                    gifUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return DirectShareIntentSpec(intent = sendIntent, usedChooserFallback = false)
        }

        sendIntent.setPackage(null)
        val chooserTitle = context.getString(R.string.share_gif_chooser_title, targetAppLabel)
        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return DirectShareIntentSpec(intent = chooser, usedChooserFallback = true)
    }

    /** Keeps at most [maxFiles] newest cached GIFs in `cacheDir/shared_gifs/`. */
    fun pruneOldGifs(
        context: Context,
        maxFiles: Int = MAX_CACHED_GIFS,
        preserveFile: File? = null
    ) {
        try {
            val dir = File(context.cacheDir, DIRECTORY)
            val files = dir.listFiles()?.filter { it.isFile } ?: return
            if (files.size <= maxFiles) return
            val sortedOldestFirst = files.sortedBy { it.lastModified() }
            var remaining = files.size
            for (file in sortedOldestFirst) {
                if (remaining <= maxFiles) break
                if (preserveFile != null && file.absolutePath == preserveFile.absolutePath) continue
                if (file.delete()) {
                    remaining--
                }
            }
        } catch (_: Exception) {
            // Best-effort cache cleanup
        }
    }
}
