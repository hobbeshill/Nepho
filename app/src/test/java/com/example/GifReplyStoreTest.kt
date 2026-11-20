package com.example

import com.example.data.GifReplyStore
import com.example.data.SupportedApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GifReplyStoreTest {

    @Test
    fun `gif is sent inline when the conversation remote input supports gifs`() {
        // Messages must keep the Send button a true send instead of switching to Share.
        assertFalse(
            GifReplyStore.shouldDirectShareGif(
                packageName = SupportedApp.MESSAGES.packageName,
                supportsNativeGifReply = true
            )
        )
        assertFalse(
            GifReplyStore.shouldDirectShareGif(
                packageName = SupportedApp.GROUPME.packageName,
                supportsNativeGifReply = true
            )
        )
    }

    @Test
    fun `gif falls back to direct share when remote input is text only`() {
        assertTrue(
            GifReplyStore.shouldDirectShareGif(
                packageName = SupportedApp.MESSAGES.packageName,
                supportsNativeGifReply = false
            )
        )
        assertTrue(
            GifReplyStore.shouldDirectShareGif(
                packageName = SupportedApp.TWITTER.packageName,
                supportsNativeGifReply = false
            )
        )
    }

    @Test
    fun `direct share capable apps still allow the gif-only composer`() {
        assertTrue(GifReplyStore.isDirectSharePreferredApp(SupportedApp.MESSAGES.packageName))
        assertTrue(GifReplyStore.isDirectSharePreferredApp(SupportedApp.TWITTER.packageName))
        assertFalse(GifReplyStore.isDirectSharePreferredApp(SupportedApp.OUTLOOK.packageName))
    }
}
