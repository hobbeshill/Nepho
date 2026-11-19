package com.example.ui

import android.os.Build
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.R
import java.io.File

/** Coil loader with animated GIF/WebP support. */
@Composable
fun rememberNephoImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
    }
}

/**
 * Renders an image or animated GIF attached to (or linked from) a notification.
 * [source] is a private attachment copy, a content/file URI, or a remote image URL.
 */
@Composable
fun NotificationMediaImage(
    source: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 220.dp
) {
    val context = LocalContext.current
    val imageLoader = rememberNephoImageLoader()
    var failed by remember(source) { mutableStateOf(false) }
    val model = remember(context, source) {
        val uri = Uri.parse(source)
        val data: Any = if (uri.scheme != null) uri else File(source)
        ImageRequest.Builder(context)
            .data(data)
            .build()
    }
    Column(modifier = modifier) {
        if (failed) {
            Text(
                text = stringResource(R.string.media_load_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AsyncImage(
            model = model,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            contentScale = ContentScale.FillWidth,
            onError = {
                failed = true
                Log.w("NotificationMediaImage", "Failed to load notification media", it.result.throwable)
            },
            onSuccess = { failed = false },
            modifier = Modifier
                .heightIn(max = maxHeight)
                .clip(RoundedCornerShape(12.dp))
        )
    }
}
