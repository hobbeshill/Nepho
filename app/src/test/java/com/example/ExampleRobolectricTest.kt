package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.SmartDigestService
import com.example.data.MockNotificationFactory
import com.example.data.PulseNotificationItem
import com.example.data.PulseRepository
import com.example.data.SupportedApp
import com.example.data.UrlExtractor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PulseHub", appName)
    }

    @Test
    fun `url extractor parses and deduplicates trailing punctuation`() {
        val extracted = UrlExtractor.extractUrls(
            "Urgent invoice at https://www.contoso.com/inv/42.",
            "Also see https://www.contoso.com/inv/42/ and https://go.teamsnap.com/events/u14!"
        )
        assertEquals(2, extracted.size)
        assertEquals("contoso.com", extracted[0].domain)
        assertEquals("go.teamsnap.com", extracted[1].domain)
    }

    @Test
    fun `repository enforces 24h rolling purge on expired notifications and links`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        val now = System.currentTimeMillis()
        val freshSpec = MockNotificationFactory.createRealisticBatch(now).first()
        val expiredSpec = MockNotificationFactory.createExpiredTestNotification(now)

        repo.ingestNotification(
            key = freshSpec.key,
            notificationId = freshSpec.notificationId,
            packageName = freshSpec.app.packageName,
            appLabel = freshSpec.app.displayName,
            title = freshSpec.title,
            body = freshSpec.body,
            postTime = freshSpec.postTime,
            canReply = freshSpec.canReply,
            secondaryActions = freshSpec.secondaryActions,
            isSimulated = true,
            sourceChannel = "TEST"
        )

        repo.ingestNotification(
            key = expiredSpec.key,
            notificationId = expiredSpec.notificationId,
            packageName = expiredSpec.app.packageName,
            appLabel = expiredSpec.app.displayName,
            title = expiredSpec.title,
            body = expiredSpec.body,
            postTime = expiredSpec.postTime,
            canReply = expiredSpec.canReply,
            secondaryActions = expiredSpec.secondaryActions,
            isSimulated = true,
            sourceChannel = "TEST"
        )

        val activeNotifications = repo.activeNotificationsFlow.first()
        val activeLinks = repo.activeLinksFlow.first()

        assertEquals(1, activeNotifications.size)
        assertEquals(freshSpec.key, activeNotifications.first().key)
        assertTrue(activeLinks.none { it.domain == "expired.example.com" })
    }

    @Test
    fun `local fallback digest groups by app and detects action keywords`() {
        val service = SmartDigestService()
        val now = System.currentTimeMillis()
        val items = listOf(
            PulseNotificationItem(
                key = "k1",
                notificationId = 1,
                packageName = SupportedApp.OUTLOOK.packageName,
                appLabel = "Outlook",
                title = "Finance",
                body = "Urgent invoice approval needed today.",
                postTime = now,
                isRead = false,
                isDismissed = false,
                canReply = true,
                secondaryActions = listOf("Archive"),
                replies = emptyList(),
                urls = emptyList(),
                isSimulated = true,
                sourceChannel = "TEST"
            ),
            PulseNotificationItem(
                key = "k2",
                notificationId = 2,
                packageName = SupportedApp.TEAMSNAP.packageName,
                appLabel = "TeamSnap",
                title = "Coach",
                body = "Practice schedule moved to 5:30 PM.",
                postTime = now,
                isRead = false,
                isDismissed = false,
                canReply = true,
                secondaryActions = listOf("Mark Read"),
                replies = emptyList(),
                urls = emptyList(),
                isSimulated = true,
                sourceChannel = "TEST"
            )
        )

        val digest = service.generateLocalRuleBasedDigest(items)
        assertEquals(2, digest.appGroups.size)
        assertEquals(2, digest.actionItems.size)
    }
}
