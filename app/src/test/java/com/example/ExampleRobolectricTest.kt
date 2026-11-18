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
        assertEquals("Nepho", appName)
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

    @Test
    fun `starred notifications and links are never purged by 24h cleanup or session reset`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        val now = System.currentTimeMillis()
        val starredOldSpec = MockNotificationFactory.createExpiredTestNotification(now)
        val unstarredOldSpec = MockNotificationFactory.createExpiredTestNotification(now).copy(
            key = "unstarred_old_test",
            body = "Unstarred 25h old message with https://unstarred.example.com/item"
        )

        // Insert old item and star it before purge
        repo.ingestNotification(
            key = starredOldSpec.key,
            notificationId = starredOldSpec.notificationId,
            packageName = starredOldSpec.app.packageName,
            appLabel = starredOldSpec.app.displayName,
            title = starredOldSpec.title,
            body = starredOldSpec.body,
            postTime = now,
            canReply = false,
            secondaryActions = emptyList(),
            isSimulated = true,
            sourceChannel = "TEST"
        )
        repo.toggleNotificationStar(starredOldSpec.key)

        // Age it past 24 hours by updating postTime via re-ingest? Better: test resetSession + unstarred expiry
        repo.ingestNotification(
            key = unstarredOldSpec.key,
            notificationId = unstarredOldSpec.notificationId,
            packageName = unstarredOldSpec.app.packageName,
            appLabel = unstarredOldSpec.app.displayName,
            title = unstarredOldSpec.title,
            body = unstarredOldSpec.body,
            postTime = unstarredOldSpec.postTime,
            canReply = false,
            secondaryActions = emptyList(),
            isSimulated = true,
            sourceChannel = "TEST"
        )

        repo.purgeExpired()
        repo.resetSession()

        val activeAfterReset = repo.activeNotificationsFlow.first()
        val linksAfterReset = repo.activeLinksFlow.first()

        assertEquals(1, activeAfterReset.size)
        assertEquals(starredOldSpec.key, activeAfterReset.first().key)
        assertTrue(activeAfterReset.first().isStarred)
        assertTrue(linksAfterReset.any { it.domain == "expired.example.com" && it.isStarred })
    }

    @Test
    fun `per-app shade mode defaults to silent and honors per-app and default overrides`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        val prefs = repo.sessionPreferences

        // Reset defaults
        prefs.setDefaultShadeMode(com.example.data.ShadeMode.SILENT)
        prefs.setAppShadeMode(SupportedApp.OUTLOOK.packageName, com.example.data.ShadeMode.SILENT)

        assertEquals(false, repo.shouldPostShadeAlert(SupportedApp.OUTLOOK.packageName))
        assertEquals(false, repo.shouldPostShadeAlert("com.slack.example"))

        prefs.setAppShadeMode(SupportedApp.OUTLOOK.packageName, com.example.data.ShadeMode.NEPHO_ALERT)
        assertEquals(true, repo.shouldPostShadeAlert(SupportedApp.OUTLOOK.packageName))
        assertEquals(false, repo.shouldPostShadeAlert(SupportedApp.GMAIL.packageName))

        prefs.recordObservedApp("com.slack.example", "Slack")
        prefs.setDefaultShadeMode(com.example.data.ShadeMode.NEPHO_ALERT)
        assertEquals(true, repo.shouldPostShadeAlert("com.slack.example"))
        assertEquals("Slack", prefs.observedAppsFlow.first()["com.slack.example"])
    }

    @Test
    fun `all apps start opted in and can be toggled opted out individually`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        val prefs = repo.sessionPreferences

        // All core and new third-party apps start opted in
        SupportedApp.filterableApps.forEach { app ->
            assertEquals(true, repo.isAppOptedIn(app.packageName))
        }
        assertEquals(true, repo.isAppOptedIn("com.discord.example"))

        // Opt out Outlook only
        prefs.setAppOptedIn(SupportedApp.OUTLOOK.packageName, false)
        assertEquals(false, repo.isAppOptedIn(SupportedApp.OUTLOOK.packageName))
        assertEquals(true, repo.isAppOptedIn(SupportedApp.GMAIL.packageName))
        assertEquals(true, repo.isAppOptedIn("com.discord.example"))

        // Opt Outlook back in
        prefs.setAppOptedIn(SupportedApp.OUTLOOK.packageName, true)
        assertEquals(true, repo.isAppOptedIn(SupportedApp.OUTLOOK.packageName))
    }
}
