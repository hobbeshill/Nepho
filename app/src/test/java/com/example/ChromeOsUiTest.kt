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
import androidx.compose.foundation.lazy.LazyColumn
import com.example.launcher.LauncherAppCatalog
import com.example.ui.HomeCalendarCard
import com.example.ui.HomeClockCard
import com.example.ui.HomeMusicCard
import com.example.ui.HomeSectionsDialog
import com.example.ui.LinksVaultTab
import com.example.ui.ListenerHealthState
import com.example.ui.MusicServicePickerDialog
import com.example.ui.NephoHeaderTitle
import com.example.ui.QuickAlarmDialog
import com.example.ui.appDrawerSectionItems
import com.example.ui.homeScreenGestures
import com.example.ui.ThreadPopupDialog
import com.example.ui.WeatherSetupDialog
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

        composeRule.onNodeWithTag("home_section_toggle_dock").performClick()
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
}



