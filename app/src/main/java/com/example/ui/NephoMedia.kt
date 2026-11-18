package com.example.ui

import android.os.Build
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * [source] is either an app-private file path (captured notification picture)
 * or a remote image/GIF URL found in the message body.
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
    val model = remember(source) {
        val data: Any = if (source.startsWith("http", ignoreCase = true)) source else File(source)
        ImageRequest.Builder(context)
            .data(data)
            .build()
    }
    AsyncImage(
        model = model,
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        contentScale = ContentScale.FillWidth,
        modifier = modifier
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(12.dp))
    )
}
