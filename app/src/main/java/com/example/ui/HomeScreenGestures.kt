package com.example.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.launcher.HomeGestureActions
import kotlin.math.abs

/**
 * Tracks root-coordinate bounds of active text inputs on the home screen so tapping or
 * scrolling anywhere outside those inputs dismisses the software keyboard immediately.
 */
class KeyboardInputBoundsRegistry {
    private val boundsById = linkedMapOf<String, Rect>()

    fun update(id: String, bounds: Rect) {
        boundsById[id] = bounds
    }

    fun remove(id: String) {
        boundsById.remove(id)
    }

    fun contains(rootPosition: Offset): Boolean =
        boundsById.values.any { rect -> rect.contains(rootPosition) }
}

val LocalKeyboardInputBoundsRegistry = staticCompositionLocalOf<KeyboardInputBoundsRegistry?> { null }

fun Modifier.keepKeyboardOpenArea(id: String): Modifier = composed {
    val registry = LocalKeyboardInputBoundsRegistry.current
    DisposableEffect(registry, id) {
        onDispose { registry?.remove(id) }
    }
    this.onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) {
            registry?.update(id, coordinates.boundsInRoot())
        }
    }
}

fun Modifier.dismissKeyboardOnOutsideInteraction(
    registry: KeyboardInputBoundsRegistry,
    shouldDismiss: () -> Boolean,
    onDismissKeyboard: () -> Unit
): Modifier = composed {
    val currentShouldDismiss = rememberUpdatedState(shouldDismiss)
    val currentOnDismiss = rememberUpdatedState(onDismissKeyboard)

    this.pointerInput(registry) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            if (currentShouldDismiss.value() && !registry.contains(down.position)) {
                currentOnDismiss.value()
            }
        }
    }
}

/**
 * Pure geometry check for a clear upward swipe gesture.
 */
internal fun isUpwardSwipe(
    totalDx: Float,
    totalDy: Float,
    thresholdPx: Float
): Boolean = thresholdPx > 0f &&
    totalDy <= -thresholdPx &&
    abs(totalDy) >= abs(totalDx) * 1.5f

/**
 * Pure geometry check for a swipe-up gesture that originates within the bottom edge zone
 * of a container.
 */
internal fun isInBottomSwipeZone(
    startY: Float,
    containerHeight: Float,
    bottomZonePx: Float
): Boolean {
    if (containerHeight <= 0f || bottomZonePx <= 0f) return false
    val zoneTop = (containerHeight - bottomZonePx).coerceAtLeast(0f)
    return startY >= zoneTop && startY <= containerHeight
}

/**
 * Returns true when an upward swipe originates in the bottom zone of the screen/container
 * and travels past [thresholdPx] with clear vertical dominance.
 */
internal fun isBottomEdgeSwipeUp(
    startY: Float,
    containerHeight: Float,
    bottomZonePx: Float,
    totalDx: Float,
    totalDy: Float,
    thresholdPx: Float
): Boolean = isInBottomSwipeZone(startY, containerHeight, bottomZonePx) &&
    isUpwardSwipe(totalDx, totalDy, thresholdPx)

/**
 * Detects an upward swipe anywhere on the target bar/handle (including over clickable children
 * such as pinned dock shortcuts) without breaking normal taps.
 */
fun Modifier.swipeUpGestureOverlay(
    onSwipeUp: () -> Unit,
    swipeThreshold: Dp = 44.dp
): Modifier = composed {
    val swipeThresholdPx = with(LocalDensity.current) { swipeThreshold.toPx() }
    val currentOnSwipeUp = rememberUpdatedState(onSwipeUp)

    this.pointerInput(swipeThresholdPx) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            val pointerId = down.id
            var totalDx = 0f
            var totalDy = 0f
            var triggered = false
            var claimedSwipe = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (!change.pressed) {
                    if (claimedSwipe) {
                        change.consume()
                    }
                    break
                }

                val delta = change.positionChange()
                totalDx += delta.x
                totalDy += delta.y

                if (!claimedSwipe && totalDy < -touchSlop && abs(totalDy) >= abs(totalDx) * 1.5f) {
                    claimedSwipe = true
                }
                if (claimedSwipe) {
                    change.consume()
                }
                if (!triggered && isUpwardSwipe(totalDx, totalDy, swipeThresholdPx)) {
                    triggered = true
                    claimedSwipe = true
                    change.consume()
                    currentOnSwipeUp.value()
                }
            }
        }
    }
}

/**
 * Adds home-screen level gestures without interfering with vertical scrolling,
 * clickable buttons, horizontal chip bars, or swipe-to-dismiss notification cards:
 * - Double-tapping unconsumed home screen space turns the screen off.
 * - Swiping left (right-to-left) anywhere not consumed by a horizontal child opens Google Search.
 * - Swiping up from the bottom edge zone opens the alphabetical app drawer even when the screen can scroll.
 * - Swiping up anywhere opens the app drawer when the home screen cannot scroll further.
 */
fun Modifier.homeScreenGestures(
    onDoubleTapScreenOff: () -> Unit,
    onSwipeLeftGoogleSearch: () -> Unit,
    canScrollForward: Boolean = true,
    onSwipeUpWhenCannotScroll: () -> Unit = {},
    onSwipeUpFromBottom: (() -> Unit)? = null,
    bottomSwipeUpZoneHeight: Dp = 96.dp,
    swipeThreshold: Dp = 72.dp
): Modifier = composed {
    val swipeThresholdPx = with(LocalDensity.current) { swipeThreshold.toPx() }
    val swipeUpThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    val bottomZonePx = with(LocalDensity.current) { bottomSwipeUpZoneHeight.toPx() }
    val currentCanScrollForward = rememberUpdatedState(canScrollForward)
    val currentOnSwipeUpWhenCannotScroll = rememberUpdatedState(onSwipeUpWhenCannotScroll)
    val currentOnSwipeUpFromBottom = rememberUpdatedState(onSwipeUpFromBottom)

    this
        .pointerInput(onDoubleTapScreenOff) {
            detectTapGestures(
                onDoubleTap = { onDoubleTapScreenOff() }
            )
        }
        .pointerInput(swipeUpThresholdPx, bottomZonePx) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
                val pointerId = down.id
                val startY = down.position.y
                val containerHeight = size.height.toFloat()
                val couldScrollForwardAtStart = currentCanScrollForward.value
                val hasBottomSwipeHandler = currentOnSwipeUpFromBottom.value != null
                val startedInBottomZone = hasBottomSwipeHandler &&
                    isInBottomSwipeZone(startY, containerHeight, bottomZonePx)
                var totalDx = 0f
                var totalDy = 0f
                var triggered = false
                var claimedBottomSwipe = false

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) {
                        if (claimedBottomSwipe) {
                            change.consume()
                        }
                        break
                    }

                    val delta = change.positionChange()
                    totalDx += delta.x
                    totalDy += delta.y

                    if (
                        startedInBottomZone &&
                        !claimedBottomSwipe &&
                        totalDy < -touchSlop &&
                        abs(totalDy) >= abs(totalDx) * 1.5f
                    ) {
                        claimedBottomSwipe = true
                    }

                    if (claimedBottomSwipe) {
                        change.consume()
                    }

                    if (!triggered) {
                        val bottomHandler = currentOnSwipeUpFromBottom.value
                        if (
                            bottomHandler != null &&
                            isBottomEdgeSwipeUp(
                                startY = startY,
                                containerHeight = containerHeight,
                                bottomZonePx = bottomZonePx,
                                totalDx = totalDx,
                                totalDy = totalDy,
                                thresholdPx = swipeUpThresholdPx
                            )
                        ) {
                            triggered = true
                            claimedBottomSwipe = true
                            change.consume()
                            bottomHandler()
                        } else if (
                            !couldScrollForwardAtStart &&
                            isUpwardSwipe(totalDx, totalDy, swipeUpThresholdPx)
                        ) {
                            triggered = true
                            currentOnSwipeUpWhenCannotScroll.value()
                            break
                        }
                    }
                }
            }
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
