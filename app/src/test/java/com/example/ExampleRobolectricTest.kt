package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.PulseRepository
import com.example.data.SupportedApp
import com.example.data.UrlExtractor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Before
    fun setUpWorkManager() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private suspend fun ingest(
        repo: PulseRepository,
        key: String,
        app: SupportedApp,
        title: String,
        body: String,
        postTime: Long,
        canReply: Boolean = false,
        secondaryActions: List<String> = emptyList(),
        notificationId: Int = (key.hashCode() and 0x7FFFFFFF).coerceAtLeast(1000)
    ) = repo.ingestNotification(
        key = key,
        notificationId = notificationId,
        packageName = app.packageName,
        appLabel = app.displayName,
        title = title,
        body = body,
        postTime = postTime,
        canReply = canReply,
        secondaryActions = secondaryActions,
        sourceChannel = "TEST"
    )

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
    fun `image and gif urls are classified for inline rendering`() {
        assertTrue(UrlExtractor.isImageUrl("https://example.com/photos/cat.PNG"))
        assertTrue(UrlExtractor.isImageUrl("https://example.com/funny.gif?size=large"))
        assertTrue(UrlExtractor.isImageUrl("https://media.giphy.com/media/abc123/giphy"))
        assertTrue(UrlExtractor.isImageUrl("https://c.tenor.com/xyz/tenor"))
        assertEquals(false, UrlExtractor.isImageUrl("https://example.com/report.pdf"))
        assertEquals(false, UrlExtractor.isImageUrl("https://docs.google.com/document/d/notes"))
    }

    @Test
    fun `ingested notifications persist attached image and expose embedded image urls`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        val now = System.currentTimeMillis()
        repo.ingestNotification(
            key = "image_test_key",
            notificationId = 77,
            packageName = SupportedApp.MESSAGES.packageName,
            appLabel = SupportedApp.MESSAGES.displayName,
            title = "Jordan (Mobile)",
            body = "Check this out https://media.giphy.com/media/abc/giphy.gif and the doc https://docs.google.com/document/d/notes",
            postTime = now,
            canReply = true,
            secondaryActions = emptyList(),
            sourceChannel = "TEST",
            imageUri = "/data/fake/notification_images/123.png"
        )

        val item = repo.activeNotificationsFlow.first().first { it.key == "image_test_key" }
        assertEquals("/data/fake/notification_images/123.png", item.imageUri)
        assertEquals(listOf("https://media.giphy.com/media/abc/giphy.gif"), item.imageUrls)
        assertEquals(2, item.urls.size)
    }

    @Test
    fun `repository enforces 24h rolling purge on expired notifications and links`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        val now = System.currentTimeMillis()
        val twentyFiveHoursAgo = now - (25 * 60 * 60 * 1000L)

        ingest(
            repo = repo,
            key = "fresh_purge_test",
            app = SupportedApp.OUTLOOK,
            title = "Finance",
            body = "Invoice sheet at https://contoso.sharepoint.com/sites/finance/inv-4092",
            postTime = now,
            canReply = true,
            secondaryActions = listOf("Archive", "Delete")
        )
        ingest(
            repo = repo,
            key = "expired_purge_test",
            app = SupportedApp.GMAIL,
            title = "Expired Archive Alert (25h Old)",
            body = "25 hours old with https://expired.example.com/old-report must be purged.",
            postTime = twentyFiveHoursAgo
        )

        val activeNotifications = repo.activeNotificationsFlow.first()
        val activeLinks = repo.activeLinksFlow.first()

        assertEquals(1, activeNotifications.size)
        assertEquals("fresh_purge_test", activeNotifications.first().key)
        assertTrue(activeLinks.none { it.domain == "expired.example.com" })
    }

    @Test
    fun `starred notifications and links are never purged by 24h cleanup or session reset`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        val now = System.currentTimeMillis()
        val twentyFiveHoursAgo = now - (25 * 60 * 60 * 1000L)

        // Insert old item and star it before purge
        ingest(
            repo = repo,
            key = "starred_old_test",
            app = SupportedApp.GMAIL,
            title = "Starred Old Alert",
            body = "Old but starred with https://expired.example.com/starred-report",
            postTime = now
        )
        repo.toggleNotificationStar("starred_old_test")

        ingest(
            repo = repo,
            key = "unstarred_old_test",
            app = SupportedApp.GMAIL,
            title = "Unstarred Old Alert",
            body = "Unstarred 25h old message with https://unstarred.example.com/item",
            postTime = twentyFiveHoursAgo
        )

        repo.purgeExpired()
        repo.resetSession()

        val activeAfterReset = repo.activeNotificationsFlow.first()
        val linksAfterReset = repo.activeLinksFlow.first()

        assertEquals(1, activeAfterReset.size)
        assertEquals("starred_old_test", activeAfterReset.first().key)
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

    @Test
    fun `blocked shade mode is persisted and never posts shade alerts`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        val prefs = repo.sessionPreferences

        prefs.setAppShadeMode(SupportedApp.GROUPME.packageName, com.example.data.ShadeMode.BLOCKED)
        assertEquals(
            com.example.data.ShadeMode.BLOCKED,
            prefs.getEffectiveShadeMode(SupportedApp.GROUPME.packageName)
        )
        assertEquals(false, repo.shouldPostShadeAlert(SupportedApp.GROUPME.packageName))

        // Unblocking restores normal behavior
        prefs.setAppShadeMode(SupportedApp.GROUPME.packageName, com.example.data.ShadeMode.NEPHO_ALERT)
        assertEquals(true, repo.shouldPostShadeAlert(SupportedApp.GROUPME.packageName))
    }

    @Test
    fun `replying to a notification dismisses it from the active feed`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        val now = System.currentTimeMillis()
        ingest(
            repo = repo,
            key = "reply_test_key",
            app = SupportedApp.MESSAGES,
            title = "Jordan (Mobile)",
            body = "Running 10 mins behind for the 10am sync.",
            postTime = now,
            canReply = true,
            secondaryActions = listOf("Mark Read")
        )

        assertTrue(repo.sendInlineReply("reply_test_key", "On my way!"))

        val entity = repo.getNotificationByKey("reply_test_key")
        assertTrue(entity!!.isDismissed)
        assertTrue(entity.repliesJson.contains("On my way!"))
        assertTrue(repo.activeNotificationsFlow.first().none { it.key == "reply_test_key" })
    }

    @Test
    fun `any secondary action dismisses the notification`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        val now = System.currentTimeMillis()
        ingest(
            repo = repo,
            key = "action_test_key",
            app = SupportedApp.TEAMSNAP,
            title = "Coach",
            body = "Practice moved to 5 PM",
            postTime = now,
            secondaryActions = listOf("Mark Read")
        )

        // Non-archive actions used to only mark as read; acting on an alert must now dismiss it
        repo.executeSecondaryAction("action_test_key", "Mark Read")
        assertTrue(repo.getNotificationByKey("action_test_key")!!.isDismissed)
    }

    @Test
    fun `system removal sync dismisses only undismissed known notifications`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        val now = System.currentTimeMillis()
        ingest(
            repo = repo,
            key = "sync_test_key",
            app = SupportedApp.GMAIL,
            title = "Inbox",
            body = "New mail",
            postTime = now
        )

        assertEquals(false, repo.syncDismissalFromSystem("unknown_key"))
        assertEquals(true, repo.syncDismissalFromSystem("sync_test_key"))
        assertTrue(repo.getNotificationByKey("sync_test_key")!!.isDismissed)
        // Already dismissed entries are not re-processed
        assertEquals(false, repo.syncDismissalFromSystem("sync_test_key"))
    }
}
