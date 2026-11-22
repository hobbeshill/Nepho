package com.example

import org.junit.Assert.*
import org.junit.Test
import com.example.data.SupportedApp
import com.example.data.local.HomeSectionId
import com.example.data.local.HomeSectionPrefs
import com.example.launcher.LauncherAppCatalog
import com.example.launcher.LauncherAppInfo
import com.example.notifications.ListenerRecoveryPolicy
import com.example.ui.SoothingQuotesCatalog
import com.example.ui.areAllVisibleSectionsMinimized
import com.example.ui.computeLetterOffsetInDrawer
import com.example.ui.feedAppColor
import com.example.ui.isBottomEdgeSwipeUp
import com.example.ui.isUpwardSwipe

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun feedColorsAreStableAndDistinctForSupportedApps() {
    val colors = SupportedApp.filterableApps.map { feedAppColor(it.packageName) }
    assertEquals(colors.size, colors.distinct().size)
    assertEquals(feedAppColor("com.example.custom"), feedAppColor("com.example.custom"))
    assertNotEquals(feedAppColor("com.example.custom"), feedAppColor("com.example.custom2"))
  }

  @Test
  fun soothingQuotesCatalogHasCuratedNonRepeatingQuotes() {
    val quotes = SoothingQuotesCatalog.quotes
    assertTrue("Expected at least 20 soothing quotes", quotes.size >= 20)
    assertTrue(quotes.all { it.text.isNotBlank() && it.attribution.isNotBlank() })
    assertEquals(quotes.size, quotes.map { it.text }.distinct().size)

    val initial = SoothingQuotesCatalog.initialIndex(seedMillis = 123_456_789L)
    assertTrue(initial in quotes.indices)
    val next = SoothingQuotesCatalog.nextIndex(initial)
    assertNotEquals(initial, next)
    assertTrue(next in quotes.indices)
  }

  @Test
  fun areAllVisibleSectionsMinimizedChecksVisibleDrawersOnly() {
    assertFalse(areAllVisibleSectionsMinimized(HomeSectionPrefs()))
    assertTrue(
      areAllVisibleSectionsMinimized(
        HomeSectionPrefs(minimizedSections = HomeSectionId.entries.toSet())
      )
    )
    assertTrue(
      areAllVisibleSectionsMinimized(
        HomeSectionPrefs(
          showMusic = false,
          showCalendar = false,
          minimizedSections = setOf(HomeSectionId.CLOCK, HomeSectionId.INBOX, HomeSectionId.APPS)
        )
      )
    )
    assertFalse(
      areAllVisibleSectionsMinimized(
        HomeSectionPrefs(
          showMusic = false,
          minimizedSections = setOf(HomeSectionId.CLOCK, HomeSectionId.INBOX, HomeSectionId.APPS)
        )
      )
    )
  }

  @Test
  fun bottomEdgeSwipeUpDetectsUpwardSwipesFromBottomZoneOnly() {
    // Starts in bottom 200px of a 1000px screen and moves clearly upward past 120px threshold
    assertTrue(
      isBottomEdgeSwipeUp(
        startY = 920f,
        containerHeight = 1000f,
        bottomZonePx = 200f,
        totalDx = 12f,
        totalDy = -160f,
        thresholdPx = 120f
      )
    )
    // Starts in the middle of the screen -> not a bottom-edge swipe
    assertFalse(
      isBottomEdgeSwipeUp(
        startY = 500f,
        containerHeight = 1000f,
        bottomZonePx = 200f,
        totalDx = 0f,
        totalDy = -220f,
        thresholdPx = 120f
      )
    )
    // Mostly horizontal swipe from bottom -> ignored
    assertFalse(
      isBottomEdgeSwipeUp(
        startY = 950f,
        containerHeight = 1000f,
        bottomZonePx = 200f,
        totalDx = -180f,
        totalDy = -130f,
        thresholdPx = 120f
      )
    )
    // Downward or too-short drag -> ignored
    assertFalse(isUpwardSwipe(totalDx = 0f, totalDy = 150f, thresholdPx = 120f))
    assertFalse(isUpwardSwipe(totalDx = 0f, totalDy = -80f, thresholdPx = 120f))
  }

  @Test
  fun computeLetterOffsetInDrawerMatchesAlphabeticalSectionIndices() {
    val apps = listOf(
      LauncherAppInfo("com.a.alpha", "Main", "Alpha", "A"),
      LauncherAppInfo("com.a.atlas", "Main", "Atlas", "A"),
      LauncherAppInfo("com.c.chrome", "Main", "Chrome", "C"),
      LauncherAppInfo("com.s.spotify", "Main", "Spotify", "S")
    )
    val grouped = LauncherAppCatalog.groupAlphabetically(apps)
    assertEquals(0, computeLetterOffsetInDrawer(grouped, "A"))
    assertEquals(3, computeLetterOffsetInDrawer(grouped, "C"))
    assertEquals(5, computeLetterOffsetInDrawer(grouped, "S"))
    assertEquals(-1, computeLetterOffsetInDrawer(grouped, "Z"))
  }

  @Test
  fun listenerRecoveryPolicyEnforcesYellowDetectionAndCooldownGuardrails() {
    assertTrue(ListenerRecoveryPolicy.isYellowDisconnected(isAccessGranted = true, isListenerConnected = false))
    assertFalse(ListenerRecoveryPolicy.isYellowDisconnected(isAccessGranted = false, isListenerConnected = false))
    assertFalse(ListenerRecoveryPolicy.isYellowDisconnected(isAccessGranted = true, isListenerConnected = true))

    val baseTime = 1_000_000L
    // First silent attempt allowed when yellow
    assertTrue(
      ListenerRecoveryPolicy.canRunSilentRecovery(
        isAccessGranted = true,
        isListenerConnected = false,
        nowMs = baseTime,
        lastSilentAttemptMs = 0L
      )
    )
    // Blocked within 30s silent cooldown
    assertFalse(
      ListenerRecoveryPolicy.canRunSilentRecovery(
        isAccessGranted = true,
        isListenerConnected = false,
        nowMs = baseTime + 15_000L,
        lastSilentAttemptMs = baseTime
      )
    )
    // Allowed after 30s silent cooldown
    assertTrue(
      ListenerRecoveryPolicy.canRunSilentRecovery(
        isAccessGranted = true,
        isListenerConnected = false,
        nowMs = baseTime + ListenerRecoveryPolicy.SILENT_RECOVERY_COOLDOWN_MS,
        lastSilentAttemptMs = baseTime
      )
    )

    // Stage 3 auto-restart is foreground-only and throttled by 10-minute cooldown
    assertFalse(
      ListenerRecoveryPolicy.canTriggerAutoRestart(
        isAccessGranted = true,
        isListenerConnected = false,
        isForeground = false,
        nowMs = baseTime,
        lastAutoRestartMs = 0L
      )
    )
    assertTrue(
      ListenerRecoveryPolicy.canTriggerAutoRestart(
        isAccessGranted = true,
        isListenerConnected = false,
        isForeground = true,
        nowMs = baseTime,
        lastAutoRestartMs = 0L
      )
    )
    assertFalse(
      ListenerRecoveryPolicy.canTriggerAutoRestart(
        isAccessGranted = true,
        isListenerConnected = false,
        isForeground = true,
        nowMs = baseTime + 5L * 60L * 1_000L,
        lastAutoRestartMs = baseTime
      )
    )
    assertTrue(
      ListenerRecoveryPolicy.canTriggerAutoRestart(
        isAccessGranted = true,
        isListenerConnected = false,
        isForeground = true,
        nowMs = baseTime + ListenerRecoveryPolicy.AUTO_RESTART_COOLDOWN_MS,
        lastAutoRestartMs = baseTime
      )
    )
    // Once connected again, auto-restart must never fire
    assertFalse(
      ListenerRecoveryPolicy.canTriggerAutoRestart(
        isAccessGranted = true,
        isListenerConnected = true,
        isForeground = true,
        nowMs = baseTime + ListenerRecoveryPolicy.AUTO_RESTART_COOLDOWN_MS,
        lastAutoRestartMs = 0L
      )
    )
  }
}

