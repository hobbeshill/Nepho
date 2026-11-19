package com.example

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.example.ui.ComposeCaptureDialog
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
}
