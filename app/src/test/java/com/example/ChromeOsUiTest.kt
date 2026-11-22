package com.example

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionEquals
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
import com.example.data.CaptureOutcome
import com.example.data.PulseNotificationItem
import com.example.data.SupportedApp
import com.example.data.local.CalmVisualPrefs
import com.example.data.local.CaptureDiagnosticEntity
import com.example.data.local.HomeSectionId
import com.example.data.local.HomeSectionPrefs
import com.example.launcher.CalendarDayGroup
import com.example.launcher.CalendarEventItem
import com.example.launcher.CalendarScheduleState
import com.example.launcher.LauncherAppInfo
import com.example.launcher.MediaPlaybackState
import com.example.launcher.WeatherUiState
import com.example.ui.BottomShortcutDock
import com.example.ui.ComposeCaptureDialog
import com.example.ui.FeedTab
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import com.example.launcher.LauncherAppCatalog
import com.example.ui.AlphabeticalAppDrawerSheet
import com.example.ui.CaptureDiagnosticsDialog
import com.example.ui.HomeCalendarCard
import com.example.ui.HomeClockCard
import com.example.ui.HomeMusicCard
import com.example.ui.HomeSectionsDialog
import com.example.ui.InboxModeSwitcher
import com.example.ui.KeyboardInputBoundsRegistry
import com.example.ui.LinksVaultTab
import com.example.ui.ListenerHealthState
import com.example.ui.LocalKeyboardInputBoundsRegistry
import com.example.ui.MinimizedAppDrawerBar
import com.example.ui.MinimizedCalendarBar
import com.example.ui.MinimizedClockBar
import com.example.ui.MinimizedInboxBar
import com.example.ui.MinimizedMusicBar
import com.example.ui.MusicServicePickerDialog
import com.example.ui.NephoHeaderTitle
import com.example.ui.PulseTab
import com.example.ui.QuickAlarmDialog
import com.example.ui.RestartNephoMenuItem
import com.example.ui.SoothingQuotesCard
import com.example.ui.SoothingQuotesCatalog
import com.example.ui.appDrawerSectionItems
import com.example.ui.areAllVisibleSectionsMinimized
import com.example.ui.dismissKeyboardOnOutsideInteraction
import com.example.ui.homeScreenGestures
import com.example.ui.keepKeyboardOpenArea
import com.example.ui.ThreadPopupDialog
import com.example.ui.WeatherSetupDialog
import com.example.ui.theme.NephoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        var openedThread: String? = null
        composeRule.setContent {
            NephoTheme {
                FeedTab(
                    notifications = notifications.value,
                    selectedAppFilter = selected.value,
                    unreadOnly = false,
                    hasLinksOnly = false,
                    starredOnly = false,
                    bannerDismissed = true,
                    hasNotificationPermission = true,
                    isNotificationAccessGranted = true,
                    onSelectAppFilter = { selected.value = it },
                    onToggleUnreadOnly = {},
                    onToggleHasLinksOnly = {},
                    onToggleStarredOnly = {},
                    onRequestNotificationPermission = {},
                    onOpenNotificationAccessSettings = {},
                    onOpenShadePreferences = {},
                    onDismissBanner = {},
                    onOpenThread = { openedThread = it.key },
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

        composeRule.onNodeWithTag("feed_search_input").assertDoesNotExist()
        composeRule.onNodeWithTag("empty_feed_card").assertDoesNotExist()
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
        composeRule.onNodeWithTag("notification_card_test").performScrollTo()
        composeRule.onNodeWithTag("card_app_watermark_test", useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithTag("notification_card_test").performClick()
        composeRule.runOnIdle {
            assertEquals("test", openedThread)
            notifications.value = notifications.value.map { it.copy(isRead = true, isStarred = true) }
        }
        composeRule.onNodeWithTag("card_app_watermark_test", useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithTag("starred_badge_test", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
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

    @Test
    fun `swiping up on the bottom shortcut dock pulls up the app search`() {
        var swipedUp = false

        composeRule.setContent {
            NephoTheme {
                BottomShortcutDock(
                    pinnedApps = listOf(LauncherAppInfo("com.android.chrome", "Main", "Chrome", "C")),
                    onLaunchApp = {},
                    onExpandDrawer = {},
                    onSwipeUp = { swipedUp = true }
                )
            }
        }

        composeRule.onNodeWithTag("bottom_shortcut_dock").performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertTrue(swipedUp)
        }
    }

    @Test
    fun `home sections dialog toggles section visibility`() {
        var latest: HomeSectionPrefs? = null

        composeRule.setContent {
            NephoTheme {
                HomeSectionsDialog(
                    sections = HomeSectionPrefs(),
                    onSetSections = { latest = it },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_section_toggle_calendar").performClick()
        composeRule.runOnIdle {
            assertEquals(false, latest?.showCalendar)
        }

        composeRule.onNodeWithTag("home_section_toggle_dock").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(false, latest?.showShortcutDock)
        }
    }

    @Test
    fun `clock weather area opens weather dialog and lets user add and launch a weather app shortcut`() {
        val showWeatherDialog = mutableStateOf(false)
        val selectedShortcut = mutableStateOf<LauncherAppInfo?>(null)
        var openedShortcutApp = false

        val weatherApp = LauncherAppInfo("com.accuweather.android", "Main", "AccuWeather", "A")
        val chromeApp = LauncherAppInfo("com.android.chrome", "Main", "Chrome", "C")

        composeRule.setContent {
            NephoTheme {
                val weatherState = WeatherUiState(
                    locationName = "Seattle, WA",
                    latitude = 47.6062,
                    longitude = -122.3321,
                    tempC = 20.0,
                    highC = 22.0,
                    lowC = 13.0,
                    weatherCode = 0,
                    isDay = true,
                    useFahrenheit = true,
                    shortcutApp = selectedShortcut.value
                )
                HomeClockCard(
                    nextAlarmSummary = null,
                    onClick = {},
                    weatherState = weatherState,
                    onWeatherClick = { showWeatherDialog.value = true }
                )
                if (showWeatherDialog.value) {
                    WeatherSetupDialog(
                        weatherState = weatherState,
                        installedApps = listOf(chromeApp, weatherApp),
                        citySearchResults = emptyList(),
                        isSearchingCities = false,
                        onSearchCities = {},
                        onSelectLocation = {},
                        onUseCurrentLocation = {},
                        onRefreshWeather = {},
                        onToggleUnit = {},
                        onOpenShortcutApp = { openedShortcutApp = true },
                        onSelectShortcutApp = { selectedShortcut.value = it },
                        onDismiss = {}
                    )
                }
            }
        }

        // Clock area displays live temperature and opens the weather dialog when tapped
        composeRule.onNodeWithTag("home_clock_weather_temp", useUnmergedTree = true)
            .assertTextContains("68°F")
        composeRule.onNodeWithTag("home_clock_weather_area").performClick()

        // User chooses a weather app shortcut from the dialog
        composeRule.onNodeWithTag("weather_choose_app_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("weather_app_option_com.accuweather.android").performClick()

        // Once selected, the Open <App> shortcut button appears and invokes launch
        composeRule.onNodeWithTag("weather_open_app_button")
            .performScrollTo()
            .assertTextContains("Open AccuWeather")
            .performClick()

        composeRule.runOnIdle {
            assertEquals("com.accuweather.android", selectedShortcut.value?.packageName)
            assertTrue(openedShortcutApp)
        }
    }

    @Test
    fun `HomeCalendarCard shows permission onboarding when calendar access is not granted`() {
        var requestedPermission = false
        var openedCalendarApp = false

        composeRule.setContent {
            NephoTheme {
                HomeCalendarCard(
                    scheduleState = CalendarScheduleState(hasPermission = false),
                    onRequestCalendarPermission = { requestedPermission = true },
                    onRefresh = {},
                    onOpenEvent = {},
                    onOpenCalendarApp = { openedCalendarApp = true }
                )
            }
        }

        composeRule.onNodeWithTag("calendar_permission_prompt").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar_grant_permission_button").performClick()
        composeRule.onNodeWithTag("calendar_open_app_button").performClick()

        composeRule.runOnIdle {
            assertTrue(requestedPermission)
            assertTrue(openedCalendarApp)
        }
    }

    @Test
    fun `HomeCalendarCard renders today tomorrow and next day events and opens tapped event`() {
        var clickedEvent: CalendarEventItem? = null
        var refreshed = false
        var openedCalendarApp = false

        val todayEvent = CalendarEventItem(
            eventId = 101L,
            title = "Design Review",
            startMillis = 1_763_571_600_000L,
            endMillis = 1_763_575_200_000L,
            allDay = false,
            location = "Room B"
        )
        val nextDayEvent = CalendarEventItem(
            eventId = 202L,
            title = "Weekend Hike",
            startMillis = 1_763_740_800_000L,
            endMillis = 1_763_827_200_000L,
            allDay = true
        )

        val state = CalendarScheduleState(
            hasPermission = true,
            days = listOf(
                CalendarDayGroup(0, "Today", "Thu, Nov 19", listOf(todayEvent)),
                CalendarDayGroup(1, "Tomorrow", "Fri, Nov 20", emptyList()),
                CalendarDayGroup(2, "Saturday", "Nov 21", listOf(nextDayEvent))
            ),
            totalEventCount = 2
        )

        composeRule.setContent {
            NephoTheme {
                HomeCalendarCard(
                    scheduleState = state,
                    onRequestCalendarPermission = {},
                    onRefresh = { refreshed = true },
                    onOpenEvent = { clickedEvent = it },
                    onOpenCalendarApp = { openedCalendarApp = true }
                )
            }
        }

        composeRule.onNodeWithTag("calendar_day_title_0", useUnmergedTree = true).assertTextContains("Today")
        composeRule.onNodeWithTag("calendar_day_title_1", useUnmergedTree = true).assertTextContains("Tomorrow")
        composeRule.onNodeWithTag("calendar_day_title_2", useUnmergedTree = true).assertTextContains("Saturday")
        composeRule.onNodeWithTag("calendar_day_empty_1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("calendar_event_title_101_0", useUnmergedTree = true)
            .assertTextContains("Design Review")
        composeRule.onNodeWithTag("calendar_event_title_202_2", useUnmergedTree = true)
            .assertTextContains("Weekend Hike")

        composeRule.onNodeWithTag("calendar_event_101_0").performClick()
        composeRule.onNodeWithTag("calendar_refresh_button").performClick()
        composeRule.onNodeWithTag("calendar_open_app_button").performClick()

        composeRule.runOnIdle {
            assertEquals(101L, clickedEvent?.eventId)
            assertTrue(refreshed)
            assertTrue(openedCalendarApp)
        }
    }

    @Test
    fun `HomeMusicCard lets user pick a connected music service and switch back to Auto`() {
        val showPicker = mutableStateOf(false)
        val selectedMusicApp = mutableStateOf<LauncherAppInfo?>(null)
        var openedSelected = false

        val spotify = LauncherAppInfo("com.spotify.music", "Main", "Spotify", "S")
        val ytMusic = LauncherAppInfo("com.google.android.apps.youtube.music", "Main", "YouTube Music", "Y")
        val chrome = LauncherAppInfo("com.android.chrome", "Main", "Chrome", "C")

        composeRule.setContent {
            NephoTheme {
                HomeMusicCard(
                    mediaState = MediaPlaybackState(isActive = false, hasSessionAccess = true),
                    onTogglePlayPause = {},
                    onSkipPrevious = {},
                    onSkipNext = {},
                    onOpenMusicApp = {},
                    onGrantNotificationAccess = {},
                    preferredMusicApp = selectedMusicApp.value,
                    onChooseMusicService = { showPicker.value = true }
                )
                if (showPicker.value) {
                    MusicServicePickerDialog(
                        selectedApp = selectedMusicApp.value,
                        installedApps = listOf(chrome, ytMusic, spotify),
                        hasSessionAccess = true,
                        onSelectService = { selectedMusicApp.value = it },
                        onOpenSelectedService = { openedSelected = true },
                        onGrantNotificationAccess = {},
                        onDismiss = { showPicker.value = false }
                    )
                }
            }
        }

        // Open service picker from the Music card and pick Spotify
        composeRule.onNodeWithTag("music_choose_service_button").performClick()
        composeRule.onNodeWithTag("music_service_picker_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("music_service_option_com.spotify.music").performClick()

        // Music card now reflects Spotify as the connected service
        composeRule.onNodeWithTag("music_idle_title_text", useUnmergedTree = true)
            .assertTextContains("Spotify")

        // Reopen picker to verify Open Spotify button and switching back to Auto
        composeRule.onNodeWithTag("music_choose_service_button").performClick()
        composeRule.onNodeWithTag("music_service_open_selected_button")
            .assertTextContains("Open Spotify")
            .performClick()

        composeRule.runOnIdle {
            assertEquals("com.spotify.music", selectedMusicApp.value?.packageName)
            assertTrue(openedSelected)
        }
    }

    @Test
    fun `homeScreenGestures handles double-tap screen off and swipe-left Google Search without blocking buttons`() {
        var screenOffCount = 0
        var googleSearchCount = 0
        var buttonClicks = 0

        composeRule.setContent {
            NephoTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .homeScreenGestures(
                            onDoubleTapScreenOff = { screenOffCount++ },
                            onSwipeLeftGoogleSearch = { googleSearchCount++ }
                        )
                        .testTag("gesture_test_root")
                ) {
                    Button(
                        onClick = { buttonClicks++ },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .testTag("gesture_child_button")
                    ) {
                        Text("Action")
                    }
                }
            }
        }

        // Swiping right or up should NOT trigger Google Search
        composeRule.onNodeWithTag("gesture_test_root").performTouchInput { swipeRight() }
        composeRule.onNodeWithTag("gesture_test_root").performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertEquals(0, googleSearchCount)
        }

        // Swiping left triggers Google Search
        composeRule.onNodeWithTag("gesture_test_root").performTouchInput { swipeLeft() }
        composeRule.runOnIdle {
            assertEquals(1, googleSearchCount)
        }

        // Double-tapping a child button should click the button, NOT lock the screen
        composeRule.onNodeWithTag("gesture_child_button").performTouchInput { doubleClick() }
        composeRule.runOnIdle {
            assertEquals(2, buttonClicks)
            assertEquals(0, screenOffCount)
        }

        // Double-tapping the open home surface turns the screen off
        composeRule.onNodeWithTag("gesture_test_root").performTouchInput {
            doubleClick(position = center)
        }
        composeRule.runOnIdle {
            assertEquals(1, screenOffCount)
        }
    }

    @Test
    fun `homeScreenGestures opens app search on upward swipe only when screen cannot scroll`() {
        var appSearchOpenCount = 0
        val canScrollForward = mutableStateOf(true)

        composeRule.setContent {
            NephoTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .homeScreenGestures(
                            onDoubleTapScreenOff = {},
                            onSwipeLeftGoogleSearch = {},
                            canScrollForward = canScrollForward.value,
                            onSwipeUpWhenCannotScroll = { appSearchOpenCount++ }
                        )
                        .testTag("non_scrollable_gesture_root")
                )
            }
        }

        composeRule.onNodeWithTag("non_scrollable_gesture_root").performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertEquals(0, appSearchOpenCount)
            canScrollForward.value = false
        }
        composeRule.onNodeWithTag("non_scrollable_gesture_root").performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertEquals(1, appSearchOpenCount)
        }
    }

    @Test
    fun `two-line NephoHeaderTitle shows full app name, full version number, and mental clarity tagline`() {
        var brandClicks = 0
        composeRule.setContent {
            NephoTheme {
                NephoHeaderTitle(
                    versionName = "2.14.3",
                    listenerHealth = ListenerHealthState(
                        isAccessGranted = true,
                        isListenerConnected = true
                    ),
                    onClick = { brandClicks++ }
                )
            }
        }

        composeRule.onNodeWithTag("top_bar_title", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Nepho")
        composeRule.onNodeWithTag("top_bar_version", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("v2.14.3")
        composeRule.onNodeWithTag("top_bar_tagline", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("mental clarity")
        composeRule.onNodeWithTag("session_status_pill").assertDoesNotExist()

        composeRule.onNodeWithTag("top_bar_brand_area").performClick()
        composeRule.runOnIdle {
            assertEquals(1, brandClicks)
        }
    }

    @Test
    fun `LinksVaultTab keeps search bar and app drawer search notifies focus and filters apps`() {
        val vaultQuery = mutableStateOf("")
        val appQuery = mutableStateOf("")
        var searchFocusedCount = 0
        val apps = listOf(
            LauncherAppInfo("com.android.chrome", "Main", "Chrome", "C"),
            LauncherAppInfo("com.spotify.music", "Main", "Spotify", "S"),
            LauncherAppInfo("com.google.android.calendar", "Main", "Calendar", "C")
        )

        composeRule.setContent {
            NephoTheme {
                val grouped = LauncherAppCatalog.groupAlphabetically(
                    LauncherAppCatalog.filterApps(apps, appQuery.value)
                )
                androidx.compose.foundation.layout.Column {
                    LinksVaultTab(
                        links = emptyList(),
                        searchQuery = vaultQuery.value,
                        onSearchQueryChange = { vaultQuery.value = it },
                        onToggleLinkStar = { _, _ -> },
                        modifier = Modifier.weight(1f)
                    )
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        appDrawerSectionItems(
                            totalInstalledCount = apps.size,
                            searchQuery = appQuery.value,
                            groupedApps = grouped,
                            pinnedPackages = emptySet(),
                            onSearchQueryChange = { appQuery.value = it },
                            onJumpToLetter = {},
                            onLaunchApp = {},
                            onTogglePin = {},
                            onSearchFocusChanged = { focused ->
                                if (focused) searchFocusedCount++
                            }
                        )
                    }
                }
            }
        }

        // Links Vault retains its search input
        composeRule.onNodeWithTag("vault_search_input").assertIsDisplayed().performTextInput("github")
        composeRule.runOnIdle {
            assertEquals("github", vaultQuery.value)
        }

        // Focusing & typing in the app drawer search bar fires focus callback and narrows visible apps
        composeRule.onNodeWithTag("app_drawer_search_input")
            .assertIsDisplayed()
            .performClick()
            .performTextInput("Spot")
        composeRule.onNodeWithTag("drawer_app_row_com.spotify.music").assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_app_row_com.android.chrome").assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(searchFocusedCount >= 1)
        }
    }

    @Test
    fun `HomeSectionPrefs reorders normalizes and toggles minimized sections`() {
        val initial = HomeSectionPrefs()
        assertEquals(HomeSectionId.DEFAULT_ORDER, initial.normalizedOrder)

        val movedMusicUp = initial.moveSection(HomeSectionId.MUSIC, -1)
        assertEquals(HomeSectionId.MUSIC, movedMusicUp.normalizedOrder[0])
        assertEquals(HomeSectionId.CLOCK, movedMusicUp.normalizedOrder[1])

        // Moving top item further up is a no-op
        assertEquals(movedMusicUp.normalizedOrder, movedMusicUp.moveSection(HomeSectionId.MUSIC, -1).normalizedOrder)

        // Toggle minimize on Calendar
        val minimized = movedMusicUp.toggleMinimized(HomeSectionId.CALENDAR)
        assertTrue(minimized.isMinimized(HomeSectionId.CALENDAR))
        assertFalse(minimized.toggleMinimized(HomeSectionId.CALENDAR).isMinimized(HomeSectionId.CALENDAR))

        // Partial/duplicated order gets normalized with all default sections
        val normalized = HomeSectionId.normalizeOrder(listOf(HomeSectionId.INBOX, HomeSectionId.INBOX))
        assertEquals(HomeSectionId.DEFAULT_ORDER.size, normalized.size)
        assertEquals(HomeSectionId.INBOX, normalized.first())
    }

    @Test
    fun `HomeSectionsDialog updates Calm appearance toggles reorders minimizes and resets`() {
        val sectionsState = mutableStateOf(HomeSectionPrefs())
        val calmState = mutableStateOf(CalmVisualPrefs())
        var resetClicked = false

        composeRule.setContent {
            NephoTheme(calmPrefs = calmState.value) {
                HomeSectionsDialog(
                    sections = sectionsState.value,
                    onSetSections = { sectionsState.value = it },
                    onDismiss = {},
                    calmPrefs = calmState.value,
                    onSetCalmPrefs = { calmState.value = it },
                    onResetLayout = {
                        resetClicked = true
                        sectionsState.value = HomeSectionPrefs()
                    }
                )
            }
        }

        // Default Calm preferences are all enabled; toggle monochrome & hide counts off
        composeRule.onNodeWithTag("calm_toggle_monochrome").performScrollTo().performClick()
        composeRule.onNodeWithTag("calm_toggle_hide_counts").performScrollTo().performClick()
        // Move Music above Clock and minimize Calendar
        composeRule.onNodeWithTag("home_section_move_up_music").performScrollTo().performClick()
        composeRule.onNodeWithTag("home_section_minimize_calendar").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertFalse(calmState.value.monochromeIcons)
            assertFalse(calmState.value.hideUnreadCounts)
            assertTrue(calmState.value.calmPaletteEnabled)
            assertEquals(HomeSectionId.MUSIC, sectionsState.value.normalizedOrder.first())
            assertTrue(sectionsState.value.isMinimized(HomeSectionId.CALENDAR))
        }

        composeRule.onNodeWithTag("home_layout_reset_button").performClick()
        composeRule.runOnIdle {
            assertTrue(resetClicked)
            assertEquals(HomeSectionId.DEFAULT_ORDER, sectionsState.value.normalizedOrder)
            assertFalse(sectionsState.value.isMinimized(HomeSectionId.CALENDAR))
        }
    }

    @Test
    fun `minimized home bars expand on click and respect quiet inbox dot`() {
        var expandedClock = false
        var expandedMusic = false
        var toggledPlay = false
        var expandedCalendar = false
        var expandedInbox = false
        var expandedApps = false
        var quickSearchedApps = false

        composeRule.setContent {
            NephoTheme(calmPrefs = CalmVisualPrefs(hideUnreadCounts = true)) {
                androidx.compose.foundation.layout.Column {
                    MinimizedClockBar(
                        weatherState = WeatherUiState(),
                        onExpand = { expandedClock = true }
                    )
                    MinimizedMusicBar(
                        mediaState = MediaPlaybackState(
                            isActive = true,
                            isPlaying = true,
                            title = "Ambient Rain",
                            artist = "Calm Radio"
                        ),
                        onTogglePlayPause = { toggledPlay = true },
                        onExpand = { expandedMusic = true }
                    )
                    MinimizedCalendarBar(
                        scheduleState = CalendarScheduleState(hasPermission = true),
                        onExpand = { expandedCalendar = true }
                    )
                    MinimizedInboxBar(
                        unreadCount = 4,
                        linksCount = 2,
                        onExpand = { expandedInbox = true }
                    )
                    MinimizedAppDrawerBar(
                        totalInstalledCount = 24,
                        onExpand = { expandedApps = true },
                        onQuickSearch = { quickSearchedApps = true }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("minimized_inbox_quiet_dot", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("minimized_section_clock").performClick()
        composeRule.onNodeWithTag("minimized_music_play_pause").performClick()
        composeRule.onNodeWithTag("minimized_section_music").performClick()
        composeRule.onNodeWithTag("minimized_section_calendar").performClick()
        composeRule.onNodeWithTag("minimized_section_inbox").performClick()
        composeRule.onNodeWithTag("minimized_apps_search_button").performClick()
        composeRule.onNodeWithTag("minimized_section_apps").performClick()

        composeRule.runOnIdle {
            assertTrue(expandedClock)
            assertTrue(toggledPlay)
            assertTrue(expandedMusic)
            assertTrue(expandedCalendar)
            assertTrue(expandedInbox)
            assertTrue(quickSearchedApps)
            assertTrue(expandedApps)
        }
    }

    @Test
    fun `FeedTab collapses filters with quiet toggle and declutters cards in Calm Mode`() {
        val calmMode = mutableStateOf(CalmVisualPrefs(calmPaletteEnabled = true, hideUnreadCounts = true))
        val sampleItem = PulseNotificationItem(
            key = "msg_calm_1",
            notificationId = 42,
            packageName = SupportedApp.GMAIL.packageName,
            appLabel = "Gmail",
            title = "Quiet Digest",
            body = "Everything is calm today.",
            postTime = 1_763_571_600_000L,
            isRead = false,
            isDismissed = false,
            canReply = false,
            secondaryActions = emptyList(),
            replies = emptyList(),
            urls = emptyList(),
            sourceChannel = "TEST",
            isStarred = false
        )

        composeRule.setContent {
            NephoTheme(calmPrefs = calmMode.value) {
                FeedTab(
                    notifications = listOf(sampleItem),
                    selectedAppFilter = null,
                    unreadOnly = false,
                    hasLinksOnly = false,
                    starredOnly = false,
                    bannerDismissed = true,
                    hasNotificationPermission = true,
                    isNotificationAccessGranted = true,
                    onSelectAppFilter = {},
                    onToggleUnreadOnly = {},
                    onToggleHasLinksOnly = {},
                    onToggleStarredOnly = {},
                    onRequestNotificationPermission = {},
                    onOpenNotificationAccessSettings = {},
                    onOpenShadePreferences = {},
                    onDismissBanner = {},
                    onOpenThread = {},
                    onOpenApp = {},
                    onOpenSelectedApp = {},
                    onToggleStar = {},
                    onSwipeToggleStar = {},
                    onSwipeDismissItem = {},
                    onSwipeStarBlocked = {},
                    onMarkGroupRead = { _, _ -> },
                    onClearGroup = { _, _ -> },
                    onOpenReminderDialog = {},
                    onDismissItem = {},
                    onOpenVaultTab = {},
                    initialFiltersExpanded = false
                )
            }
        }

        // Filters start collapsed behind quiet toggle pill
        composeRule.onNodeWithTag("feed_filter_toggle_button").assertIsDisplayed()
        composeRule.onNodeWithTag("filter_chip_gmail").assertDoesNotExist()
        // In Calm Mode, inline card action buttons are omitted and group shows quiet status
        composeRule.onNodeWithTag("star_button_msg_calm_1").assertDoesNotExist()
        composeRule.onNodeWithTag("group_quiet_status_${sampleItem.packageName}", useUnmergedTree = true)
            .assertIsDisplayed()

        // Tapping Filter expands the chip rows
        composeRule.onNodeWithTag("feed_filter_toggle_button").performClick()
        composeRule.onNodeWithTag("filter_chip_gmail").assertIsDisplayed()

        // Switching to Vivid Mode restores inline card action buttons
        composeRule.runOnIdle {
            calmMode.value = CalmVisualPrefs(calmPaletteEnabled = false, hideUnreadCounts = false)
        }
        composeRule.onNodeWithTag("star_button_msg_calm_1").assertIsDisplayed()
    }

    @Test
    fun `dismissKeyboardOnOutsideInteraction dismisses keyboard on any outside tap or scroll without blocking controls`() {
        val registry = KeyboardInputBoundsRegistry()
        val query = mutableStateOf("")
        var dismissCount = 0
        var otherButtonClicks = 0

        composeRule.setContent {
            NephoTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalKeyboardInputBoundsRegistry provides registry
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .dismissKeyboardOnOutsideInteraction(
                                registry = registry,
                                shouldDismiss = { true },
                                onDismissKeyboard = { dismissCount++ }
                            )
                            .testTag("keyboard_dismiss_root")
                    ) {
                        androidx.compose.material3.OutlinedTextField(
                            value = query.value,
                            onValueChange = { query.value = it },
                            modifier = Modifier
                                .keepKeyboardOpenArea("test_search")
                                .testTag("test_search_field")
                        )
                        Button(
                            onClick = { otherButtonClicks++ },
                            modifier = Modifier.testTag("other_home_control")
                        ) {
                            Text("Clock")
                        }
                    }
                }
            }
        }

        // Tapping and typing inside the search input should NOT dismiss the keyboard
        composeRule.onNodeWithTag("test_search_field").performClick().performTextInput("Maps")
        composeRule.runOnIdle {
            assertEquals("Maps", query.value)
            assertEquals(0, dismissCount)
        }

        // Tapping another control on the home screen dismisses the keyboard AND triggers the control
        composeRule.onNodeWithTag("other_home_control").performClick()
        composeRule.runOnIdle {
            assertEquals(1, otherButtonClicks)
            assertEquals(1, dismissCount)
        }

        // Swiping/scrolling elsewhere on the home screen also dismisses the keyboard
        composeRule.onNodeWithTag("keyboard_dismiss_root").performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertEquals(2, dismissCount)
        }
    }

    @Test
    fun `InboxModeSwitcher shows calming blue glowing heading when inbox has items and stays quiet when empty`() {
        val totalCount = mutableStateOf(0)
        val unreadCount = mutableStateOf(0)

        composeRule.setContent {
            NephoTheme {
                InboxModeSwitcher(
                    selectedTab = PulseTab.FEED,
                    unreadCount = unreadCount.value,
                    linksCount = 0,
                    onSelectTab = {},
                    onMinimize = {},
                    totalInboxCount = totalCount.value
                )
            }
        }

        // Empty inbox: heading is displayed without active glow indicator
        composeRule.onNodeWithTag("inbox_section_heading", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Inbox")
        composeRule.onNodeWithTag("inbox_heading_glow_indicator", useUnmergedTree = true)
            .assertDoesNotExist()

        // Any item in inbox (even if already read): activates larger bold calming blue glowing heading
        composeRule.runOnIdle {
            totalCount.value = 1
        }
        composeRule.onNodeWithTag("inbox_section_heading", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Inbox")
        composeRule.onNodeWithTag("inbox_heading_glow_indicator", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `MinimizedInboxBar stops showing New activity once all messages are reviewed`() {
        val totalCount = mutableStateOf(2)
        val unreadCount = mutableStateOf(2)

        composeRule.setContent {
            NephoTheme(calmPrefs = CalmVisualPrefs(hideUnreadCounts = true)) {
                MinimizedInboxBar(
                    unreadCount = unreadCount.value,
                    linksCount = 0,
                    onExpand = {},
                    totalInboxCount = totalCount.value
                )
            }
        }

        // Unread messages present: shows quiet dot and "New activity"
        composeRule.onNodeWithTag("minimized_inbox_quiet_dot", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("minimized_inbox_status", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("New activity")

        // All messages reviewed (unread == 0, total > 0): no dot and no "New activity"
        composeRule.runOnIdle {
            unreadCount.value = 0
        }
        composeRule.onNodeWithTag("minimized_inbox_quiet_dot", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag("minimized_inbox_status", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Reviewed")

        // All messages cleared (unread == 0, total == 0): shows "All caught up"
        composeRule.runOnIdle {
            totalCount.value = 0
        }
        composeRule.onNodeWithTag("minimized_inbox_status", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("All caught up")
    }

    @Test
    fun `SoothingQuotesCard displays famous quotes and cycles to a different quote when tapped`() {
        val firstQuote = SoothingQuotesCatalog.quotes[0]
        val secondQuote = SoothingQuotesCatalog.quotes[1]

        composeRule.setContent {
            NephoTheme {
                SoothingQuotesCard(initialQuoteIndex = 0)
            }
        }

        composeRule.onNodeWithTag("soothing_quotes_card").assertIsDisplayed()
        composeRule.onNodeWithTag("soothing_quote_text", useUnmergedTree = true)
            .assertTextContains(firstQuote.text, substring = true)
        composeRule.onNodeWithTag("soothing_quote_attribution", useUnmergedTree = true)
            .assertTextContains(firstQuote.attribution, substring = true)

        composeRule.onNodeWithTag("soothing_quote_next_button").performClick()

        composeRule.onNodeWithTag("soothing_quote_text", useUnmergedTree = true)
            .assertTextContains(secondQuote.text, substring = true)
        composeRule.onNodeWithTag("soothing_quote_attribution", useUnmergedTree = true)
            .assertTextContains(secondQuote.attribution, substring = true)
    }

    @Test
    fun `areAllVisibleSectionsMinimized detects when every visible home drawer is closed`() {
        val defaultPrefs = HomeSectionPrefs()
        assertFalse(areAllVisibleSectionsMinimized(defaultPrefs))

        val allClosed = HomeSectionPrefs(
            minimizedSections = HomeSectionId.entries.toSet()
        )
        assertTrue(areAllVisibleSectionsMinimized(allClosed))

        val hiddenSomeRestClosed = HomeSectionPrefs(
            showMusic = false,
            showCalendar = false,
            minimizedSections = setOf(HomeSectionId.CLOCK, HomeSectionId.INBOX, HomeSectionId.APPS)
        )
        assertTrue(areAllVisibleSectionsMinimized(hiddenSomeRestClosed))
    }

    @Test
    fun `FeedTab shows SoothingQuotesCard when inbox is empty and hides it when messages arrive`() {
        val notifications = mutableStateOf<List<PulseNotificationItem>>(emptyList())

        composeRule.setContent {
            NephoTheme {
                FeedTab(
                    notifications = notifications.value,
                    selectedAppFilter = null,
                    unreadOnly = false,
                    hasLinksOnly = false,
                    starredOnly = false,
                    bannerDismissed = true,
                    hasNotificationPermission = true,
                    isNotificationAccessGranted = true,
                    onSelectAppFilter = {},
                    onToggleUnreadOnly = {},
                    onToggleHasLinksOnly = {},
                    onToggleStarredOnly = {},
                    onRequestNotificationPermission = {},
                    onOpenNotificationAccessSettings = {},
                    onOpenShadePreferences = {},
                    onDismissBanner = {},
                    onOpenThread = {},
                    onOpenApp = {},
                    onOpenSelectedApp = {},
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

        composeRule.onNodeWithTag("soothing_quotes_card").performScrollTo().assertIsDisplayed()

        composeRule.runOnIdle {
            notifications.value = listOf(
                PulseNotificationItem(
                    key = "inbox_msg_1",
                    notificationId = 10,
                    packageName = SupportedApp.MESSAGES.packageName,
                    appLabel = "Messages",
                    title = "Sam",
                    body = "See you soon!",
                    postTime = 1_763_571_600_000L,
                    isRead = false,
                    isDismissed = false,
                    canReply = true,
                    secondaryActions = emptyList(),
                    replies = emptyList(),
                    urls = emptyList(),
                    sourceChannel = "TEST"
                )
            )
        }

        composeRule.onNodeWithTag("soothing_quotes_card").assertDoesNotExist()
        composeRule.onNodeWithTag("notification_card_inbox_msg_1").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `RestartNephoMenuItem and CaptureDiagnosticsDialog expose yellow-light reset and restart action`() {
        val isYellow = mutableStateOf(true)
        var settingsRestarts = 0
        var diagnosticsRestarts = 0

        composeRule.setContent {
            NephoTheme {
                androidx.compose.foundation.layout.Column {
                    RestartNephoMenuItem(
                        isYellowStatus = isYellow.value,
                        onClick = { settingsRestarts++ }
                    )
                    CaptureDiagnosticsDialog(
                        health = ListenerHealthState(
                            isAccessGranted = true,
                            isListenerConnected = !isYellow.value,
                            lastConnectedAt = null,
                            hasPostNotificationPermission = true,
                            isIgnoringBatteryOptimizations = true
                        ),
                        diagnostics = emptyList(),
                        onRequestRebind = {},
                        onOpenNotificationAccessSettings = {},
                        onRequestNotificationPermission = {},
                        onOpenBatterySettings = {},
                        onClearLog = {},
                        onDismiss = {},
                        onRestartNepho = { diagnosticsRestarts++ }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("menu_restart_yellow_indicator", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("menu_restart_nepho").performClick()
        composeRule.onNodeWithTag("diagnostics_restart_nepho_button").performClick()

        composeRule.runOnIdle {
            assertEquals(1, settingsRestarts)
            assertEquals(1, diagnosticsRestarts)
            isYellow.value = false
        }

        composeRule.onNodeWithTag("menu_restart_yellow_indicator", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `homeScreenGestures opens alphabetical app drawer on bottom-edge swipe up even when scrollable`() {
        var bottomSwipeCount = 0
        var nonScrollableSwipeCount = 0

        composeRule.setContent {
            NephoTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .homeScreenGestures(
                            onDoubleTapScreenOff = {},
                            onSwipeLeftGoogleSearch = {},
                            canScrollForward = true,
                            onSwipeUpWhenCannotScroll = { nonScrollableSwipeCount++ },
                            onSwipeUpFromBottom = { bottomSwipeCount++ },
                            bottomSwipeUpZoneHeight = 96.dp
                        )
                        .testTag("bottom_edge_gesture_root")
                )
            }
        }

        // Swiping up from the middle when scrollable should NOT trigger bottom-edge swipe
        composeRule.onNodeWithTag("bottom_edge_gesture_root").performTouchInput {
            swipeUp(startY = centerY, endY = top + 40f)
        }
        composeRule.runOnIdle {
            assertEquals(0, bottomSwipeCount)
            assertEquals(0, nonScrollableSwipeCount)
        }

        // Swiping up from near the bottom edge opens the alphabetical app drawer even when scrollable
        composeRule.onNodeWithTag("bottom_edge_gesture_root").performTouchInput {
            swipeUp(startY = bottom - 16f, endY = centerY)
        }
        composeRule.runOnIdle {
            assertEquals(1, bottomSwipeCount)
            assertEquals(0, nonScrollableSwipeCount)
        }
    }

    @Test
    fun `BottomShortcutDock swipe up over pinned icon opens drawer without launching icon and AlphabeticalAppDrawerSheet filters apps`() {
        val apps = listOf(
            LauncherAppInfo("com.android.chrome", "Main", "Chrome", "C"),
            LauncherAppInfo("com.google.android.calendar", "Main", "Calendar", "C"),
            LauncherAppInfo("com.spotify.music", "Main", "Spotify", "S")
        )
        var launchedApp: LauncherAppInfo? = null
        val drawerOpen = mutableStateOf(false)

        composeRule.setContent {
            NephoTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    BottomShortcutDock(
                        pinnedApps = apps,
                        onLaunchApp = { launchedApp = it },
                        onExpandDrawer = {},
                        onSwipeUp = { drawerOpen.value = true },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                    )
                    if (drawerOpen.value) {
                        AlphabeticalAppDrawerSheet(
                            installedApps = apps,
                            pinnedPackages = setOf("com.android.chrome"),
                            onLaunchApp = { launchedApp = it },
                            onTogglePin = {},
                            onDismiss = { drawerOpen.value = false }
                        )
                    }
                }
            }
        }

        // Swiping up starting directly on a pinned dock shortcut opens the drawer without launching the app
        composeRule.onNodeWithTag("dock_shortcut_com.android.chrome").performTouchInput {
            swipeUp(startY = centerY, endY = centerY - 220f)
        }
        composeRule.runOnIdle {
            assertTrue(drawerOpen.value)
            assertEquals(null, launchedApp)
        }

        composeRule.onNodeWithTag("alphabetical_app_drawer_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("alphabetical_drawer_search_input").performTextInput("Spot")
        composeRule.onNodeWithTag("alphabetical_drawer_app_com.spotify.music").performClick()

        composeRule.runOnIdle {
            assertEquals("com.spotify.music", launchedApp?.packageName)
            assertFalse(drawerOpen.value)
        }
    }

    @Test
    fun `NephoHeaderTitle and CaptureDiagnosticsDialog display subtle reconnecting status and recovery log entries`() {
        val reconnectingHealth = ListenerHealthState(
            isAccessGranted = true,
            isListenerConnected = false,
            lastConnectedAt = null,
            hasPostNotificationPermission = true,
            isIgnoringBatteryOptimizations = true,
            isReconnecting = true
        )
        val entries = listOf(
            CaptureDiagnosticEntity(
                id = 1L,
                timestamp = 1_700_000_000_000L,
                packageName = "com.aistudio.pulsehub.kxmvpq",
                appLabel = "Nepho",
                titlePreview = "Auto-reconnecting listener",
                outcome = CaptureOutcome.LISTENER_RECONNECTING.name,
                detail = "Auto-recovery (app resume): requested Android to rebind."
            ),
            CaptureDiagnosticEntity(
                id = 2L,
                timestamp = 1_700_000_010_000L,
                packageName = "com.aistudio.pulsehub.kxmvpq",
                appLabel = "Nepho",
                titlePreview = "Auto-restarting Nepho",
                outcome = CaptureOutcome.LISTENER_AUTO_RESTARTED.name,
                detail = "Auto-recovery (app resume): cleanly restarting Nepho."
            )
        )

        composeRule.setContent {
            NephoTheme {
                androidx.compose.foundation.layout.Column {
                    NephoHeaderTitle(
                        versionName = "1.0",
                        listenerHealth = reconnectingHealth,
                        onClick = {}
                    )
                    CaptureDiagnosticsDialog(
                        health = reconnectingHealth,
                        diagnostics = entries,
                        onRequestRebind = {},
                        onOpenNotificationAccessSettings = {},
                        onRequestNotificationPermission = {},
                        onOpenBatterySettings = {},
                        onClearLog = {},
                        onDismiss = {},
                        onRestartNepho = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("top_bar_health_dot", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Reconnecting notification listener")
        composeRule.onNodeWithTag("health_status_text_notification_listener", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Reconnecting…")
    }
}







