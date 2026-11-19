package com.example.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.launcher.HomeGestureActions
import kotlin.math.abs

/**
 * Adds home-screen level gestures without interfering with vertical scrolling,
 * clickable buttons, horizontal chip bars, or swipe-to-dismiss notification cards:
 * - Double-tapping unconsumed home screen space turns the screen off.
 * - Swiping left (right-to-left) anywhere not consumed by a horizontal child opens Google Search.
 */
fun Modifier.homeScreenGestures(
    onDoubleTapScreenOff: () -> Unit,
    onSwipeLeftGoogleSearch: () -> Unit,
    swipeThreshold: Dp = 72.dp
): Modifier = composed {
    val swipeThresholdPx = with(LocalDensity.current) { swipeThreshold.toPx() }

    this
        .pointerInput(onDoubleTapScreenOff) {
            detectTapGestures(
                onDoubleTap = { onDoubleTapScreenOff() }
            )
        }
        .pointerInput(onSwipeLeftGoogleSearch, swipeThresholdPx) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pointerId = down.id
                var totalDx = 0f
                var totalDy = 0f
                var triggered = false

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) break
                    // Yield immediately if a child (e.g. SwipeToDismissBox, horizontalScroll, LazyColumn)
                    // has already claimed this pointer movement.
                    if (change.isConsumed) break

                    val delta = change.positionChange()
                    totalDx += delta.x
                    totalDy += delta.y

                    // Abort as soon as gesture is clearly vertical scrolling.
                    if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx)) {
                        break
                    }

                    // Claim clear leftward horizontal drags past touch slop so underlying cards don't click.
                    if (totalDx < -touchSlop && abs(totalDx) >= abs(totalDy) * 1.5f) {
                        change.consume()
                    }

                    if (!triggered && HomeGestureActions.isHorizontalSwipeLeft(totalDx, totalDy, swipeThresholdPx)) {
                        triggered = true
                        change.consume()
                        onSwipeLeftGoogleSearch()
                        break
                    }
                }
            }
        }
}
