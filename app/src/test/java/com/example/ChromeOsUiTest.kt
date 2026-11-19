package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.PulseNotificationItem
import com.example.data.SupportedApp
import com.example.ui.ComposeCaptureDialog
import com.example.ui.FeedTab
import com.example.ui.theme.NephoTheme
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
            org.junit.Assert.assertEquals(SupportedApp.GMAIL, openedApp)
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
            org.junit.Assert.assertEquals(SupportedApp.OUTLOOK, openedApp)
            selected.value = null
        }
        composeRule.onNodeWithTag("selected_app_link").assertDoesNotExist()
    }
}
