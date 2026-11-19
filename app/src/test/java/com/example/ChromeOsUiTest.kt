package com.example

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.CachedGifAttachment
import com.example.data.PulseNotificationItem
import com.example.data.SupportedApp
import com.example.launcher.LauncherAppInfo
import com.example.launcher.MediaPlaybackState
import com.example.ui.BottomShortcutDock
import com.example.ui.ComposeCaptureDialog
import com.example.ui.FeedTab
import com.example.ui.HomeClockCard
import com.example.ui.HomeMusicCard
import com.example.ui.QuickAlarmDialog
import com.example.ui.ThreadPopupDialog
import com.example.ui.theme.NephoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChromeOsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `capture draft survives activity state restoration`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            NephoTheme {
                ComposeCaptureDialog(onDismiss = {}, onSubmit = { _, _, _ -> })
            }

        }
        composeRule.onNodeWithTag("capture_title_input").performTextInput("Sender")
        composeRule.onNodeWithTag("capture_body_input").performTextInput("Message")

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("capture_title_input").assertTextContains("Sender")
        composeRule.onNodeWithTag("capture_body_input").assertTextContains("Message")
    }

    @Test
    fun `selected app shortcut remains available with or without alerts`() {
        val selected = mutableStateOf<SupportedApp?>(null)
        val notifications = mutableStateOf<List<PulseNotificationItem>>(emptyList())
        var openedApp: SupportedApp? = null
        composeRule.setContent {
            NephoTheme {
                FeedTab(
                    notifications = notifications.value,
                    sessionOpenedAt = 0,
                    searchQuery = "",
                    selectedAppFilter = selected.value,
                    unreadOnly = false,
                    hasLinksOnly = false,
                    starredOnly = false,
                    bannerDismissed = true,
                    hasNotificationPermission = true,
                    isNotificationAccessGranted = true,
                    onSearchQueryChange = {},
                    onSelectAppFilter = { selected.value = it },
                    onToggleUnreadOnly = {},
                    onToggleHasLinksOnly = {},
                    onToggleStarredOnly = {},
                    onRequestNotificationPermission = {},
                    onOpenNotificationAccessSettings = {},
                    onOpenShadePreferences = {},
                    onDismissBanner = {},
                    onOpenThread = {},
                    onOpenApp = {},
                    onOpenSelectedApp = { openedApp = it },
                    onToggleStar = {},
                    onSwipeToggleStar = {},
                    onSwipeDismissItem = {},
                    onSwipeStarBlocked = {},
                    onMarkGroupRead = { _, _ -> },
                    onClearGroup = { _, _ -> },
                    onOpenReminderDialog = {},
                    onDismissItem = {},
                    onOpenVaultTab = {}
                )
            }
        }

        composeRule.onNodeWithTag("selected_app_link").assertDoesNotExist()
        composeRule.onNodeWithTag("filter_chip_gmail").performClick()
        composeRule.onNodeWithTag("selected_app_link").performScrollTo()
            .assertTextContains("Open Gmail").performClick()
        composeRule.runOnIdle {
            assertEquals(SupportedApp.GMAIL, openedApp)
            notifications.value = listOf(
                PulseNotificationItem(
                    key = "test", notificationId = 1, packageName = SupportedApp.GMAIL.packageName,
                    appLabel = "Gmail", title = "Sender", body = "Message", postTime = 0,
                    isRead = false, isDismissed = false, canReply = false,
                    secondaryActions = emptyList(), replies = emptyList(), urls = emptyList(),
                    sourceChannel = "TEST"
                )
            )
        }
        composeRule.onNodeWithTag("selected_app_link").performScrollTo()
            .assertTextContains("Open Gmail")
        composeRule.runOnIdle { selected.value = SupportedApp.OUTLOOK }
        composeRule.onNodeWithTag("selected_app_link").performScrollTo()
            .assertTextContains("Open Outlook").performClick()
        composeRule.runOnIdle {
            assertEquals(SupportedApp.OUTLOOK, openedApp)
            selected.value = null
        }
        composeRule.onNodeWithTag("selected_app_link").assertDoesNotExist()
    }

    @Test
    fun `thread popup exposes gif button, animated preview, remove, and share gif cta for x and messages`() {
        var sentKey: String? = null
        var sentText: String? = null
        var sentGif: CachedGifAttachment? = null
        val stagedGif = CachedGifAttachment(
            filePath = "",
            shareUri = Uri.parse("content://com.aistudio.pulsehub.kxmvpq.fileprovider/shared_gifs/sample.gif")
        )
        val xMention = PulseNotificationItem(
            key = "x_mention_key",
            notificationId = 42,
            packageName = SupportedApp.TWITTER.packageName,
            appLabel = SupportedApp.TWITTER.displayName,
            title = "@jordan mentioned you",
            body = "Drop your favorite reaction GIF!",
            postTime = 1_700_000_000_000L,
            isRead = false,
            isDismissed = false,
            canReply = false, // X mention without inline RemoteInput still supports direct GIF sharing
            secondaryActions = emptyList(),
            replies = emptyList(),
            urls = emptyList(),
            sourceChannel = "LISTENER"
        )

        composeRule.setContent {
            NephoTheme {
                ThreadPopupDialog(
                    threadItems = listOf(xMention),
                    onDismissRequest = {},
                    onOpenApp = {},
                    onToggleStar = {},
                    onOpenReminderDialog = {},
                    shouldDirectShareGif = true,
                    onStageGif = { stagedGif },
                    onSendReply = { key, text, _, gif ->
                        sentKey = key
                        sentText = text
                        sentGif = gif
                    },
                    onDismissThread = {},
                    onSecondaryAction = { _, _ -> },
                    initialStagedGif = stagedGif
                )
            }
        }

        // GIF picker button, preview card, and Share GIF button are visible even when canReply is false
        composeRule.onNodeWithTag("thread_attach_gif_button").assertIsDisplayed()
        composeRule.onNodeWithTag("thread_gif_preview_card").assertIsDisplayed()
        composeRule.onNodeWithTag("thread_send_reply_button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertTextContains("Share GIF")

        // Typing a caption and tapping Share GIF dispatches both caption and staged GIF
        composeRule.onNodeWithTag("thread_reply_input").performTextInput("Haha love this")
        composeRule.onNodeWithTag("thread_send_reply_button").performClick()

        composeRule.runOnIdle {
            assertEquals("x_mention_key", sentKey)
            assertEquals("Haha love this", sentText)
            assertEquals(stagedGif, sentGif)
        }
        // Preview clears after sending
        composeRule.onNodeWithTag("thread_gif_preview_card").assertDoesNotExist()
    }

    @Test
    fun `home clock card and quick alarm dialog dispatch alarm and clock actions`() {
        val showAlarmDialog = mutableStateOf(false)
        var scheduledAlarm: Triple<Int, Int, String?>? = null
        var openedClockApp = false

        composeRule.setContent {
            NephoTheme {
                HomeClockCard(
                    nextAlarmSummary = "Thu 7:00 AM",
                    onClick = { showAlarmDialog.value = true }
                )
                if (showAlarmDialog.value) {
                    QuickAlarmDialog(
                        onSetAlarm = { h, m, label -> scheduledAlarm = Triple(h, m, label) },
                        onOpenClockApp = { openedClockApp = true },
                        onDismiss = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("home_clock_alarm_pill", useUnmergedTree = true)
            .assertTextContains("Thu 7:00 AM", substring = true)
        composeRule.onNodeWithTag("home_clock_card").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("alarm_preset_7am").performScrollTo().performClick()
        composeRule.onNodeWithTag("alarm_label_input").performScrollTo().performTextInput("Gym")
        composeRule.onNodeWithTag("alarm_confirm_button").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(Triple(7, 0, "Gym"), scheduledAlarm)
        }

        composeRule.onNodeWithTag("alarm_open_clock_button").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertTrue(openedClockApp)
        }
    }

    @Test
    fun `home music controls and bottom shortcut dock invoke callbacks`() {
        var playPauseClicks = 0
        var nextClicks = 0
        var prevClicks = 0
        var openMusicClicks = 0
        var launchedDockApp: LauncherAppInfo? = null
        var expandedDock = false

        val sampleShortcuts = listOf(
            LauncherAppInfo("com.android.chrome", "Main", "Chrome", "C"),
            LauncherAppInfo("com.spotify.music", "Main", "Spotify", "S")
        )

        composeRule.setContent {
            NephoTheme {
                androidx.compose.foundation.layout.Column {
                    HomeMusicCard(
                        mediaState = MediaPlaybackState(
                            isActive = true,
                            isPlaying = true,
                            title = "Midnight City",
                            artist = "M83",
                            appLabel = "Spotify",
                            hasSessionAccess = true
                        ),
                        onTogglePlayPause = { playPauseClicks++ },
                        onSkipPrevious = { prevClicks++ },
                        onSkipNext = { nextClicks++ },
                        onOpenMusicApp = { openMusicClicks++ },
                        onGrantNotificationAccess = {}
                    )
                    BottomShortcutDock(
                        pinnedApps = sampleShortcuts,
                        onLaunchApp = { launchedDockApp = it },
                        onExpandDrawer = { expandedDock = true }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("music_track_title", useUnmergedTree = true)
            .assertTextContains("Midnight City")
        composeRule.onNodeWithTag("music_open_app_area").performClick()
        composeRule.onNodeWithTag("music_prev_button").performClick()
        composeRule.onNodeWithTag("music_play_pause_button").performClick()
        composeRule.onNodeWithTag("music_next_button").performClick()

        composeRule.onNodeWithTag("dock_shortcut_com.spotify.music").performClick()
        composeRule.onNodeWithTag("dock_expand_button").performClick()

        composeRule.runOnIdle {
            assertEquals(1, openMusicClicks)
            assertEquals(1, prevClicks)
            assertEquals(1, playPauseClicks)
            assertEquals(1, nextClicks)
            assertEquals("com.spotify.music", launchedDockApp?.packageName)
            assertTrue(expandedDock)
        }
    }
}
