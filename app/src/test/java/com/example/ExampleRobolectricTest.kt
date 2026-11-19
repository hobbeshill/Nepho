package com.example

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.CaptureOutcome
import com.example.data.GifDeliveryMode
import com.example.data.GifReplyStore
import com.example.data.NativeReplyHandle
import com.example.data.NotificationImageStore
import com.example.data.PulseJsonCodec
import com.example.data.PulseRepository
import com.example.data.ShadeMode
import com.example.data.SupportedApp
import com.example.data.UrlExtractor
import com.example.data.local.DigestEventEntity
import com.example.data.local.DigestSummaryEntity
import com.example.data.local.PulseDatabase
import com.example.data.local.PulseNotificationEntity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.provider.AlarmClock
import com.example.launcher.ClockAlarmHelper
import com.example.launcher.LauncherAppCatalog
import com.example.launcher.LauncherAppInfo
import com.example.launcher.WeatherConditionKind
import com.example.launcher.WeatherService
import com.example.notifications.NephoNotificationListener
import com.example.ui.PulseTab
import com.example.ui.PulseViewModel
import com.example.updates.GitHubReleaseUpdater
import com.example.updates.UpdateCheckState
import com.example.widget.NephoUnreadWidgetProvider
import com.example.widget.NephoWidgetSnapshotBuilder
import com.example.worker.DigestSchedule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Before
    fun setUpWorkManager() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        // Robolectric creates a fresh temp cacheDir per test method while FileProvider caches
        // resolved PathStrategy roots statically across tests in the same sandbox.
        runCatching {
            val cacheField = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            cacheField.isAccessible = true
            (cacheField.get(null) as? MutableMap<*, *>)?.clear()
        }
    }

    @Test
    fun `digest history retains dismissed and replaced alerts across session reset`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PulseDatabase::class.java).build()
        try {
            val dao = db.pulseDao()
            val end = System.currentTimeMillis() + 60_000
            val notification = PulseNotificationEntity(
                key = "same-key", notificationId = 1, packageName = "example",
                appLabel = "Example", title = "First", body = "Original", postTime = System.currentTimeMillis()
            )
            val event = DigestEventEntity(
                capturedAt = 0, appLabel = "Example", title = "First",
                body = "Original", urlsJson = "[]"
            )
            dao.insertNotificationWithLinks(notification, emptyList(), event)
            assertTrue(dao.getDigestEventsBefore(end).isEmpty())
            dao.setDigestEnabled(true, end)
            dao.insertNotificationWithLinks(notification, emptyList(), event)
            dao.dismissByKey(notification.key)
            dao.insertNotificationWithLinks(notification.copy(body = "Updated"), emptyList(), event.copy(body = "Updated"))
            dao.clearUnstarredSessionData()
            assertEquals(listOf("Original", "Updated"), dao.getDigestEventsBefore(end).map { it.body })

            val digest = DigestSummaryEntity(end, end - 1, "summary", "[]")
            dao.setDigestEnabled(false, end)
            assertFalse(dao.completeDigest(digest, end + 1))
            assertEquals(2, dao.getDigestEventsBefore(end).size)
            dao.setDigestEnabled(true, end)
            assertTrue(dao.completeDigest(digest, end + 1))
            assertTrue(dao.getDigestEventsBefore(end).isEmpty())
            assertEquals("summary", dao.observeDigestSummaries().first().single().summary)
        } finally {
            db.close()
        }
    }

    @Test
    fun `digest boundaries stay at six local time across daylight saving`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            fun time(day: Int, hour: Int): Long = Calendar.getInstance().apply {
                clear()
                set(2026, Calendar.MARCH, day, hour, 0, 0)
            }.timeInMillis
            assertEquals(time(8, 6), DigestSchedule.nextBoundary(time(7, 18)))
            assertEquals(time(7, 18), DigestSchedule.previousBoundary(time(8, 6)))
            assertEquals(time(8, 18), DigestSchedule.nextBoundary(time(8, 6)))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `version 4 database migrates without losing notifications`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "digest-migration-${UUID.randomUUID()}"
        val builder = {
            Room.databaseBuilder(context, PulseDatabase::class.java, name)
                .addMigrations(PulseDatabase.MIGRATION_4_5, PulseDatabase.MIGRATION_5_6)
                .build()
        }
        try {
            val original = builder()
            val entity = PulseNotificationEntity(
                key = "retained", notificationId = 1, packageName = "example", appLabel = "Example",
                title = "Title", body = "Body", postTime = System.currentTimeMillis()
            )
            original.pulseDao().insertNotification(entity)
            original.close()
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                db.execSQL("DROP TABLE digest_events")
                db.execSQL("DROP TABLE digest_settings")
                db.execSQL("DROP TABLE digest_summaries")
                db.execSQL("DROP TABLE capture_diagnostics")
                db.version = 4
            }
            val migrated = builder()
            try {
                assertEquals("Body", migrated.pulseDao().getByKey("retained")?.body)
                assertTrue(migrated.pulseDao().getDigestEventsBefore(Long.MAX_VALUE).isEmpty())
                assertTrue(migrated.pulseDao().observeCaptureDiagnostics().first().isEmpty())
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
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
    fun `selected app shortcut launches the app or reports when unavailable`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val packageManager = shadowOf(context.packageManager)
        val component = ComponentName(SupportedApp.GMAIL.packageName, "MainActivity")
        packageManager.addActivityIfNotPresent(component)
        packageManager.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        })
        val viewModel = PulseViewModel(context)

        viewModel.openSelectedApp(SupportedApp.GMAIL)
        val intent = shadowOf(context).nextStartedActivity
        assertEquals(component, intent.component)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)

        val event = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.snackbarEvents.first()
        }
        viewModel.openSelectedApp(SupportedApp.TEAMSNAP)
        assertEquals(context.getString(R.string.open_app_unavailable, "TeamSnap"), event.await().message)
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
    @Config(sdk = [24, 36])
    fun `chat gif attachment is copied without losing encoded animation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Two-frame, one-pixel GIF with a looping application extension.
        val gif = byteArrayOf(
            71, 73, 70, 56, 57, 97, 1, 0, 1, 0, -128, 0, 0, 0, 0, 0, -1, -1, -1,
            33, -1, 11, 78, 69, 84, 83, 67, 65, 80, 69, 50, 46, 48, 3, 1, 0, 0, 0,
            33, -7, 4, 0, 10, 0, 0, 0, 44, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 68, 1, 0,
            33, -7, 4, 0, 10, 0, 0, 0, 44, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 76, 1, 0, 59
        )
        val original = File.createTempFile("chat_attachment", ".gif", context.cacheDir)
        var stored: File? = null
        try {
            original.writeBytes(gif)
            val style = Notification.MessagingStyle("Me")
                .addMessage(Notification.MessagingStyle.Message("Earlier text", 1, "Alex"))
                .addMessage(
                    Notification.MessagingStyle.Message("GIF", 2, "Alex")
                        .setData("image/gif", Uri.fromFile(original))
                )
            val notification = Notification.Builder(context)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setStyle(style)
                .build()

            val path = NotificationImageStore.capture(context, "chat_gif", notification)
            assertNotNull(path)
            stored = File(requireNotNull(path))
            original.delete()
            assertArrayEquals(gif, stored.readBytes())
            assertNotNull(BitmapFactory.decodeFile(stored.absolutePath))
        } finally {
            original.delete()
            stored?.delete()
        }
    }

    @Test
    @Config(sdk = [31, 36])
    fun `big picture icons are captured on supported Android versions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        val notification = Notification.Builder(context, "test")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setStyle(Notification.BigPictureStyle().bigPicture(Icon.createWithBitmap(bitmap)))
            .build()

        val path = NotificationImageStore.capture(context, "icon_picture", notification)
        assertNotNull(path)
        val file = File(requireNotNull(path))
        try {
            val decoded = BitmapFactory.decodeFile(file.absolutePath)
            assertEquals(3, decoded.width)
            assertEquals(2, decoded.height)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `unavailable chat attachment falls back to notification picture`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = Notification.Builder(context, "test")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setStyle(
                Notification.MessagingStyle("Me").addMessage(
                    Notification.MessagingStyle.Message("Photo", 1, "Alex")
                        .setData("image/png", Uri.fromFile(File(context.cacheDir, "missing.png")))
                )
            )
            .build()
        notification.extras.putParcelable(
            Notification.EXTRA_PICTURE, Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
        )
        val path = NotificationImageStore.capture(context, "fallback_picture", notification)
        assertNotNull(path)
        val file = File(requireNotNull(path))
        try {
            assertEquals(3, BitmapFactory.decodeFile(file.absolutePath).height)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `source app opening dispatches the selected conversations original intent`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()
        val first = PendingIntent.getActivity(
            context, 101,
            Intent(context, MainActivity::class.java).setData(Uri.parse("test://conversation/first")),
            PendingIntent.FLAG_IMMUTABLE
        )
        val second = PendingIntent.getActivity(
            context, 102,
            Intent(context, MainActivity::class.java).setData(Uri.parse("test://conversation/second")),
            PendingIntent.FLAG_IMMUTABLE
        )
        repo.registerContentIntent("first", first)
        repo.registerContentIntent("second", second)

        assertTrue(repo.openSourceConversation("second"))
        assertEquals(
            Uri.parse("test://conversation/second"),
            shadowOf(context as android.app.Application).nextStartedActivity.data
        )
        second.cancel()
        assertFalse(repo.openSourceConversation("second"))
        assertFalse(repo.openSourceConversation("unknown"))
        assertTrue(repo.openSourceConversation("first"))
        repo.resetSession()
        assertFalse(repo.openSourceConversation("first"))
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
    fun `active feed emits new and updated notifications without restarting collection`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()
        val seen = Channel<String>(Channel.UNLIMITED)
        val initialObserved = CompletableDeferred<Unit>()
        val collector = launch {
            repo.activeNotificationsFlow.collect { items ->
                initialObserved.complete(Unit)
                items.firstOrNull { it.key == "live_feed_test" }?.let { seen.send(it.body) }
            }
        }
        try {
            withTimeout(5_000) { initialObserved.await() }
            val now = System.currentTimeMillis()
            ingest(repo, "live_feed_test", SupportedApp.MESSAGES, "Jordan", "First", now)
            assertEquals("First", withTimeout(5_000) { seen.receive() })

            ingest(repo, "live_feed_test", SupportedApp.MESSAGES, "Jordan", "Updated", now + 1)
            assertEquals("Updated", withTimeout(5_000) {
                var body = seen.receive()
                while (body != "Updated") body = seen.receive()
                body
            })
        } finally {
            collector.cancel()
            seen.close()
        }
    }

    @Test
    fun `notification listener delivers new shade notifications to an active feed`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()
        val app = SupportedApp.MESSAGES
        repo.sessionPreferences.setAppOptedIn(app.packageName, true)
        repo.sessionPreferences.setAppShadeMode(app.packageName, ShadeMode.SILENT)
        val service = Robolectric.buildService(NephoNotificationListener::class.java).create()
        val initialObserved = CompletableDeferred<Unit>()
        val received = async {
            repo.activeNotificationsFlow.first { items ->
                initialObserved.complete(Unit)
                items.any { it.body == "New message from the shade" }
            }
        }
        try {
            withTimeout(5_000) { initialObserved.await() }
            val notification = Notification.Builder(context, "test")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Jordan")
                .setContentText("New message from the shade")
                .build()
            val sbn = StatusBarNotification(
                app.packageName,
                app.packageName,
                739,
                null,
                Process.myUid(),
                Process.myPid(),
                0,
                notification,
                Process.myUserHandle(),
                System.currentTimeMillis()
            )
            service.get().onNotificationPosted(sbn)
            val items = withTimeout(10_000) { received.await() }
            assertTrue(items.any { it.key == sbn.key && it.body == "New message from the shade" })
        } finally {
            received.cancel()
            service.destroy()
        }
    }

    @Test
    fun `unrelated preference changes do not restart session cutoff observation`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PulseRepository.getInstance(context).sessionPreferences
        val initialTime = prefs.resetSessionOpenedAt()
        val initialObserved = CompletableDeferred<Unit>()
        val nextValue = async(start = CoroutineStart.UNDISPATCHED) {
            var first = true
            prefs.sessionOpenedAtFlow.first { value ->
                if (first) {
                    first = false
                    initialObserved.complete(Unit)
                    false
                } else {
                    true
                }
            }
        }
        try {
            withTimeout(5_000) { initialObserved.await() }
            prefs.recordObservedApp("com.example.livefeed.${UUID.randomUUID()}", "Live Feed")
            assertEquals(null, withTimeoutOrNull(300) { nextValue.await() })
            prefs.resetSessionOpenedAt(initialTime + 1)
            assertEquals(initialTime + 1, withTimeout(5_000) { nextValue.await() })
        } finally {
            nextValue.cancel()
        }
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

        // Clean up starred test record so subsequent tests start with a clean DB
        repo.toggleNotificationStar("starred_old_test")
        repo.resetSession()
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
    @Config(sdk = [36])
    fun `native gif replies deliver the selected uri for non-direct-share apps that advertise gif support`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()
        val now = System.currentTimeMillis()
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            210,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = android.app.RemoteInput.Builder("reply")
            .setAllowDataType("image/gif", true)
            .build()

        repo.registerNativeActionHandle(
            "gif_reply_test_key",
            NativeReplyHandle(
                replyPendingIntent = pendingIntent,
                frameworkRemoteInputs = arrayOf(remoteInput)
            )
        )
        ingest(
            repo = repo,
            key = "gif_reply_test_key",
            app = SupportedApp.GROUPME,
            title = "Team Chat",
            body = "Send a GIF",
            postTime = now,
            canReply = true
        )

        assertTrue(repo.canSendGifReply("gif_reply_test_key"))
        assertFalse(repo.shouldDirectShareGif("gif_reply_test_key", SupportedApp.GROUPME.packageName))
        val gifUri = Uri.parse("content://com.example.test/gifs/reaction.gif")
        val result = repo.sendReplyWithResult("gif_reply_test_key", "", gifUri = gifUri)
        assertTrue(result.success)
        assertEquals(GifDeliveryMode.NATIVE_REMOTE_INPUT, result.deliveryMode)

        val sentIntent = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(
            gifUri,
            android.app.RemoteInput.getDataResultsFromIntent(sentIntent, "reply")
                ?.get("image/gif")
        )
        assertTrue(repo.getNotificationByKey("gif_reply_test_key")!!.isDismissed)
    }

    @Test
    @Config(sdk = [36])
    fun `unreadable gif uri fails cleanly without dismissing the alert`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()
        val key = "unreadable_gif_reply_key"
        ingest(
            repo = repo,
            key = key,
            app = SupportedApp.MESSAGES,
            title = "Jordan",
            body = "Text only",
            postTime = System.currentTimeMillis(),
            canReply = true
        )

        assertTrue(repo.shouldDirectShareGif(key, SupportedApp.MESSAGES.packageName))
        val result = repo.sendReplyWithResult(
            key = key,
            replyText = "",
            gifUri = Uri.parse("content://com.example.nonexistent/gifs/missing.gif")
        )
        assertFalse(result.success)
        assertFalse(repo.getNotificationByKey(key)!!.isDismissed)
    }

    @Test
    @Config(sdk = [36])
    fun `gif reply store preserves animated bytes, wraps with file provider, and prunes cache`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val gifBytes = byteArrayOf(
            71, 73, 70, 56, 57, 97, 1, 0, 1, 0, -128, 0, 0, 0, 0, 0, -1, -1, -1,
            33, -1, 11, 78, 69, 84, 83, 67, 65, 80, 69, 50, 46, 48, 3, 1, 0, 0, 0,
            33, -7, 4, 0, 10, 0, 0, 0, 44, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 68, 1, 0,
            33, -7, 4, 0, 10, 0, 0, 0, 44, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 76, 1, 0, 59
        )
        val source = File.createTempFile("ime_keyboard_gif", ".gif", context.cacheDir)
        try {
            source.writeBytes(gifBytes)
            val cached = GifReplyStore.cacheGifFromUri(context, Uri.fromFile(source))
            assertNotNull(cached)
            val cachedFile = File(cached!!.filePath)
            assertTrue(cachedFile.exists())
            assertArrayEquals(gifBytes, cachedFile.readBytes())
            assertEquals("content", cached.shareUri.scheme)
            assertEquals(GifReplyStore.fileProviderAuthority(context), cached.shareUri.authority)

            // Verify pruning keeps at most maxFiles
            val dir = File(context.cacheDir, GifReplyStore.DIRECTORY)
            repeat(5) { idx ->
                val f = File(dir, "old_$idx.gif").apply {
                    writeBytes(gifBytes)
                    setLastModified(1_000L + idx * 100L)
                }
                assertTrue(f.exists())
            }
            GifReplyStore.pruneOldGifs(context, maxFiles = 2, preserveFile = cachedFile)
            val remaining = dir.listFiles()?.filter { it.isFile }.orEmpty()
            assertEquals(2, remaining.size)
            assertTrue(remaining.any { it.absolutePath == cachedFile.absolutePath })
        } finally {
            source.delete()
        }
    }

    @Test
    @Config(sdk = [36])
    fun `messages and x gif replies share directly to target package with caption and persist reply history`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val pm = shadowOf(context.packageManager)
        val messagesShareComponent = ComponentName(SupportedApp.MESSAGES.packageName, "ShareActivity")
        val twitterShareComponent = ComponentName(SupportedApp.TWITTER.packageName, "ComposerActivity")
        val gifShareFilter = IntentFilter(Intent.ACTION_SEND).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataType("image/gif")
        }
        pm.addActivityIfNotPresent(messagesShareComponent)
        pm.addIntentFilterForActivity(messagesShareComponent, gifShareFilter)
        pm.addActivityIfNotPresent(twitterShareComponent)
        pm.addIntentFilterForActivity(twitterShareComponent, gifShareFilter)

        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        val gifBytes = byteArrayOf(71, 73, 70, 56, 57, 97, 1, 0, 1, 0, 0, 0, 0, 59)
        val source = File.createTempFile("reply_gif", ".gif", context.cacheDir)
        try {
            source.writeBytes(gifBytes)
            val staged = repo.stageGifAttachment(Uri.fromFile(source))
            assertNotNull(staged)

            // 1. Google Messages direct share
            ingest(
                repo = repo,
                key = "msg_gif_direct",
                app = SupportedApp.MESSAGES,
                title = "Jordan",
                body = "Did you see that goal?",
                postTime = System.currentTimeMillis(),
                canReply = true
            )
            val msgResult = repo.sendReplyWithResult(
                key = "msg_gif_direct",
                replyText = "Unbelievable!",
                gifAttachment = staged
            )
            assertTrue(msgResult.success)
            assertEquals(GifDeliveryMode.DIRECT_SHARE, msgResult.deliveryMode)
            assertFalse(msgResult.usedChooserFallback)

            val msgIntent = shadowOf(context).nextStartedActivity
            assertEquals(Intent.ACTION_SEND, msgIntent.action)
            assertEquals(PulseRepository.GIF_MIME_TYPE, msgIntent.type)
            assertEquals(SupportedApp.MESSAGES.packageName, msgIntent.`package`)
            assertEquals(staged!!.shareUri, msgIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
            assertEquals("Unbelievable!", msgIntent.getStringExtra(Intent.EXTRA_TEXT))
            assertTrue(msgIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(staged.shareUri, msgIntent.clipData?.getItemAt(0)?.uri)

            val savedMsg = repo.getNotificationByKey("msg_gif_direct")!!
            assertTrue(savedMsg.isDismissed)
            val savedReplies = PulseJsonCodec.decodeReplies(savedMsg.repliesJson)
            assertEquals(1, savedReplies.size)
            assertEquals("Unbelievable!", savedReplies.first().text)
            assertEquals(staged.filePath, savedReplies.first().gifPath)

            // 2. X (Twitter) mention without RemoteInput (canReply = false) still shares GIF directly to X
            ingest(
                repo = repo,
                key = "x_gif_direct",
                app = SupportedApp.TWITTER,
                title = "@alex mentioned you",
                body = "What do you think?",
                postTime = System.currentTimeMillis() + 1,
                canReply = false
            )
            val xResult = repo.sendReplyWithResult(
                key = "x_gif_direct",
                replyText = "",
                gifAttachment = staged
            )
            assertTrue(xResult.success)
            assertEquals(GifDeliveryMode.DIRECT_SHARE, xResult.deliveryMode)
            assertFalse(xResult.usedChooserFallback)

            val xIntent = shadowOf(context).nextStartedActivity
            assertEquals(Intent.ACTION_SEND, xIntent.action)
            assertEquals(SupportedApp.TWITTER.packageName, xIntent.`package`)
            assertEquals(staged.shareUri, xIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
            assertEquals(null, xIntent.getStringExtra(Intent.EXTRA_TEXT))
            assertTrue(repo.getNotificationByKey("x_gif_direct")!!.isDismissed)
        } finally {
            source.delete()
        }
    }

    @Test
    @Config(sdk = [36])
    fun `gif direct share falls back to system chooser when target package cannot resolve send intent`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        val gifBytes = byteArrayOf(71, 73, 70, 56, 57, 97, 1, 0, 1, 0, 0, 0, 0, 59)
        val source = File.createTempFile("chooser_gif", ".gif", context.cacheDir)
        try {
            source.writeBytes(gifBytes)
            val staged = repo.stageGifAttachment(Uri.fromFile(source))!!

            ingest(
                repo = repo,
                key = "x_chooser_fallback",
                app = SupportedApp.TWITTER,
                title = "@sam",
                body = "Reply with a GIF",
                postTime = System.currentTimeMillis(),
                canReply = true
            )
            val result = repo.sendReplyWithResult(
                key = "x_chooser_fallback",
                replyText = "Here you go",
                gifAttachment = staged
            )
            assertTrue(result.success)
            assertEquals(GifDeliveryMode.DIRECT_SHARE, result.deliveryMode)
            assertTrue(result.usedChooserFallback)

            val chooserIntent = shadowOf(context).nextStartedActivity
            assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)
            val wrapped = chooserIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            assertNotNull(wrapped)
            assertEquals(Intent.ACTION_SEND, wrapped!!.action)
            assertEquals(PulseRepository.GIF_MIME_TYPE, wrapped.type)
            assertEquals(null, wrapped.`package`)
            assertEquals(staged.shareUri, wrapped.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
            assertEquals("Here you go", wrapped.getStringExtra(Intent.EXTRA_TEXT))
        } finally {
            source.delete()
        }
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

    @Test
    fun `single notification dismiss with undo restores alert reminder and conversation intent`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        val now = System.currentTimeMillis()
        val futureReminderAt = now + 30 * 60 * 1000L
        ingest(
            repo = repo,
            key = "undo_single_key",
            app = SupportedApp.MESSAGES,
            title = "Alex",
            body = "Call me after 4 PM",
            postTime = now,
            canReply = true
        )
        repo.scheduleReminder("undo_single_key", futureReminderAt, "Call back")
        val contentIntent = PendingIntent.getActivity(
            context,
            303,
            Intent(context, MainActivity::class.java).setData(Uri.parse("test://conversation/undo_single")),
            PendingIntent.FLAG_IMMUTABLE
        )
        repo.registerContentIntent("undo_single_key", contentIntent)

        val batch = repo.dismissNotificationWithUndo("undo_single_key")
        assertNotNull(batch)
        assertEquals(1, batch!!.items.size)
        assertTrue(repo.activeNotificationsFlow.first().none { it.key == "undo_single_key" })

        val restoredCount = repo.restoreDismissedBatch(batch)
        assertEquals(1, restoredCount)

        val restored = repo.activeNotificationsFlow.first().first { it.key == "undo_single_key" }
        assertFalse(restored.isDismissed)
        assertEquals(futureReminderAt, restored.reminderAt)
        assertEquals("Call back", restored.reminderNote)
        assertTrue(repo.openSourceConversation("undo_single_key"))
    }

    @Test
    fun `group clear dismisses only unstarred items for target package and undo restores them`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        val now = System.currentTimeMillis()
        ingest(repo, "ts_u1", SupportedApp.TEAMSNAP, "Schedule", "Practice at 5pm", now)
        ingest(repo, "ts_u2", SupportedApp.TEAMSNAP, "Snack Duty", "Bring oranges", now + 1)
        ingest(repo, "ts_star", SupportedApp.TEAMSNAP, "Coach", "Tournament bracket", now + 2)
        repo.toggleNotificationStar("ts_star")
        ingest(repo, "outlook_u1", SupportedApp.OUTLOOK, "HR", "Benefits reminder", now + 3)

        try {
            val batch = repo.dismissUnstarredForPackageWithUndo(SupportedApp.TEAMSNAP.packageName)
            assertEquals(2, batch.items.size)
            assertEquals(1, batch.keptStarredCount)

            val remainingKeys = repo.activeNotificationsFlow.first().map { it.key }.toSet()
            assertTrue("ts_star" in remainingKeys)
            assertTrue("outlook_u1" in remainingKeys)
            assertFalse("ts_u1" in remainingKeys)
            assertFalse("ts_u2" in remainingKeys)

            repo.restoreDismissedBatch(batch)
            val restoredKeys = repo.activeNotificationsFlow.first().map { it.key }.toSet()
            assertTrue(setOf("ts_u1", "ts_u2", "ts_star", "outlook_u1").all { it in restoredKeys })
        } finally {
            repo.toggleNotificationStar("ts_star")
            repo.resetSession()
        }
    }

    @Test
    fun `mark package as read updates only active alerts in target package`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        val now = System.currentTimeMillis()
        ingest(repo, "msg_1", SupportedApp.MESSAGES, "Sam", "Hello", now)
        ingest(repo, "msg_2", SupportedApp.MESSAGES, "Pat", "On my way", now + 1)
        ingest(repo, "gmail_1", SupportedApp.GMAIL, "Receipts", "Order confirmed", now + 2)

        val updatedCount = repo.markPackageAsRead(SupportedApp.MESSAGES.packageName)
        assertEquals(2, updatedCount)

        val active = repo.activeNotificationsFlow.first().associateBy { it.key }
        assertTrue(active["msg_1"]!!.isRead)
        assertTrue(active["msg_2"]!!.isRead)
        assertFalse(active["gmail_1"]!!.isRead)
    }

    @Test
    fun `self-cancel removal callback does not falsely dismiss newly captured alert`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()
        repo.clearCaptureDiagnostics()

        val app = SupportedApp.MESSAGES
        repo.sessionPreferences.setAppOptedIn(app.packageName, true)
        repo.sessionPreferences.setAppShadeMode(app.packageName, ShadeMode.SILENT)
        val serviceController = Robolectric.buildService(NephoNotificationListener::class.java).create()
        val service = serviceController.get()
        try {
            val postTime = System.currentTimeMillis() + 10
            val notification = Notification.Builder(context, "test")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Taylor")
                .setContentText("Are we still on for lunch?")
                .build()
            val sbn = StatusBarNotification(
                app.packageName,
                app.packageName,
                901,
                null,
                Process.myUid(),
                Process.myPid(),
                0,
                notification,
                Process.myUserHandle(),
                postTime
            )

            service.processPostedNotification(repo, sbn, isCatchUp = false, activeSnapshot = listOf(sbn))
            assertFalse(repo.getNotificationByKey(sbn.key)!!.isDismissed)

            // Simulate an immediate removal callback triggered by Nepho's own cancelNotification()
            service.onNotificationRemoved(sbn, null, NotificationListenerService.REASON_APP_CANCEL)
            withTimeout(5_000) {
                repo.captureDiagnosticsFlow.first { logs ->
                    logs.any { it.outcome == CaptureOutcome.SELF_CANCEL_IGNORED.name }
                }
            }
            // Message must remain active in Nepho
            assertFalse(repo.getNotificationByKey(sbn.key)!!.isDismissed)

            // A subsequent genuine removal outside Nepho (not matching a pending self-cancel) still syncs
            service.onNotificationRemoved(sbn, null, NotificationListenerService.REASON_CLICK)
            withTimeout(5_000) {
                repo.captureDiagnosticsFlow.first { logs ->
                    logs.any { it.outcome == CaptureOutcome.SYNCED_REMOVAL.name }
                }
            }
            assertTrue(repo.getNotificationByKey(sbn.key)!!.isDismissed)
        } finally {
            serviceController.destroy()
        }
    }

    @Test
    fun `stale system removal timestamp never dismisses a newer update on the same key`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        val t1 = System.currentTimeMillis()
        val t2 = t1 + 5_000L
        ingest(repo, "thread_race_key", SupportedApp.MESSAGES, "Chris", "Message 1", t1)
        ingest(repo, "thread_race_key", SupportedApp.MESSAGES, "Chris", "Message 2", t2)

        assertFalse(repo.syncDismissalFromSystem("thread_race_key", removedPostTime = t1))
        assertFalse(repo.getNotificationByKey("thread_race_key")!!.isDismissed)

        assertTrue(repo.syncDismissalFromSystem("thread_race_key", removedPostTime = t2))
        assertTrue(repo.getNotificationByKey("thread_race_key")!!.isDismissed)
    }

    @Test
    fun `messaging style, inbox style, and standalone group summary notifications are extracted cleanly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        // 1. MessagingStyle extraction
        val msgNotif = Notification.Builder(context, "test")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setStyle(
                Notification.MessagingStyle("Me")
                    .setConversationTitle("Family Chat")
                    .addMessage(Notification.MessagingStyle.Message("Picking up pizza", 1, "Mom"))
                    .addMessage(Notification.MessagingStyle.Message("Be there in 10!", 2, "Dad"))
            )
            .build()
        val extractedMsg = NephoNotificationListener.extractNotificationContent(msgNotif, "Messages")
        assertEquals("Family Chat", extractedMsg.title)
        assertTrue(extractedMsg.body.contains("Mom: Picking up pizza"))
        assertTrue(extractedMsg.body.contains("Dad: Be there in 10!"))

        // 2. InboxStyle standalone group summary (no child sibling) is captured, while summary WITH child is skipped
        val summaryNotif = Notification.Builder(context, "test")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setGroup("mail_group")
            .setGroupSummary(true)
            .setContentTitle("2 new emails")
            .setStyle(
                Notification.InboxStyle()
                    .addLine("Alice: Q4 Budget")
                    .addLine("Bob: Lunch today?")
            )
            .build()
        val childNotif = Notification.Builder(context, "test")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setGroup("mail_group")
            .setContentTitle("Alice")
            .setContentText("Q4 Budget")
            .build()

        val now = System.currentTimeMillis() + 10
        val summarySbn = StatusBarNotification(
            SupportedApp.GMAIL.packageName, SupportedApp.GMAIL.packageName, 910, null,
            Process.myUid(), Process.myPid(), 0, summaryNotif, Process.myUserHandle(), now
        )
        val childSbn = StatusBarNotification(
            SupportedApp.GMAIL.packageName, SupportedApp.GMAIL.packageName, 911, null,
            Process.myUid(), Process.myPid(), 0, childNotif, Process.myUserHandle(), now + 1
        )
        repo.sessionPreferences.setAppOptedIn(SupportedApp.GMAIL.packageName, true)
        repo.sessionPreferences.setAppShadeMode(SupportedApp.GMAIL.packageName, ShadeMode.SILENT)

        val serviceController = Robolectric.buildService(NephoNotificationListener::class.java).create()
        val service = serviceController.get()
        try {
            // With child present -> skip summary wrapper
            service.processPostedNotification(repo, summarySbn, isCatchUp = false, activeSnapshot = listOf(summarySbn, childSbn))
            assertEquals(null, repo.getNotificationByKey(summarySbn.key))

            // Standalone summary (no child present) -> capture with InboxStyle lines
            service.processPostedNotification(repo, summarySbn, isCatchUp = false, activeSnapshot = listOf(summarySbn))
            val savedSummary = repo.getNotificationByKey(summarySbn.key)
            assertNotNull(savedSummary)
            assertTrue(savedSummary!!.body.contains("Alice: Q4 Budget"))
            assertTrue(savedSummary.body.contains("Bob: Lunch today?"))
        } finally {
            serviceController.destroy()
        }
    }

    @Test
    fun `thread update on existing key preserves star reminder and replies while resurfacing alert`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        val now = System.currentTimeMillis()
        val reminderAt = now + 60 * 60 * 1000L
        ingest(repo, "preserve_state_key", SupportedApp.OUTLOOK, "Finance", "Draft attached", now, canReply = true)
        repo.toggleNotificationStar("preserve_state_key")
        repo.scheduleReminder("preserve_state_key", reminderAt, "Review numbers")

        try {
            // 1. Update while active preserves star + reminder
            ingest(repo, "preserve_state_key", SupportedApp.OUTLOOK, "Finance", "Updated draft attached", now + 1_000L, canReply = true)
            val activeUpdate = repo.getNotificationByKey("preserve_state_key")!!
            assertFalse(activeUpdate.isDismissed)
            assertTrue(activeUpdate.isStarred)
            assertEquals(reminderAt, activeUpdate.reminderAt)
            assertEquals("Review numbers", activeUpdate.reminderNote)

            // 2. Reply dismisses alert; subsequent message resurfaces it while preserving star and reply history
            repo.sendInlineReply("preserve_state_key", "Got it, checking soon")
            assertTrue(repo.getNotificationByKey("preserve_state_key")!!.isDismissed)

            ingest(repo, "preserve_state_key", SupportedApp.OUTLOOK, "Finance", "Final spreadsheet attached", now + 2_000L, canReply = true)
            val resurfaced = repo.getNotificationByKey("preserve_state_key")!!
            assertFalse(resurfaced.isDismissed)
            assertFalse(resurfaced.isRead)
            assertTrue(resurfaced.isStarred)
            assertTrue(resurfaced.repliesJson.contains("Got it, checking soon"))
            assertEquals("Final spreadsheet attached", resurfaced.body)
        } finally {
            repo.toggleNotificationStar("preserve_state_key")
            repo.resetSession()
        }
    }

    @Test
    fun `reconnect catch-up recovers post-session alerts and skips pre-session alerts`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        val sessionStart = repo.resetSession()
        repo.clearCaptureDiagnostics()

        val app = SupportedApp.GROUPME
        repo.sessionPreferences.setAppOptedIn(app.packageName, true)
        repo.sessionPreferences.setAppShadeMode(app.packageName, ShadeMode.SILENT)

        val preSbn = StatusBarNotification(
            app.packageName, app.packageName, 920, null, Process.myUid(), Process.myPid(), 0,
            Notification.Builder(context, "test")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Old Group")
                .setContentText("Before session")
                .build(),
            Process.myUserHandle(),
            sessionStart - 10_000L
        )
        val postSbn = StatusBarNotification(
            app.packageName, app.packageName, 921, null, Process.myUid(), Process.myPid(), 0,
            Notification.Builder(context, "test")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Soccer Parents")
                .setContentText("Field changed to #4")
                .build(),
            Process.myUserHandle(),
            sessionStart + 5_000L
        )

        val serviceController = Robolectric.buildService(NephoNotificationListener::class.java).create()
        val service = serviceController.get()
        try {
            service.catchUpActiveNotifications(repo, listOf(preSbn, postSbn))
            assertEquals(null, repo.getNotificationByKey(preSbn.key))
            val recovered = repo.getNotificationByKey(postSbn.key)
            assertNotNull(recovered)
            assertEquals("LISTENER_CATCHUP", recovered!!.sourceChannel)
            val logs = repo.captureDiagnosticsFlow.first()
            assertTrue(logs.any { it.outcome == CaptureOutcome.CAPTURED_CATCHUP.name && it.titlePreview == "Soccer Parents" })
        } finally {
            serviceController.destroy()
        }
    }

    @Test
    fun `capture diagnostics enforces 100-row cap and clears cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.clearCaptureDiagnostics()

        val baseTime = System.currentTimeMillis()
        for (i in 1..115) {
            repo.recordCaptureDiagnostic(
                packageName = SupportedApp.MESSAGES.packageName,
                appLabel = "Messages",
                titlePreview = "Event #$i",
                outcome = CaptureOutcome.CAPTURED,
                detail = "Captured item $i",
                timestamp = baseTime + i
            )
        }

        val logs = repo.captureDiagnosticsFlow.first()
        assertEquals(PulseRepository.MAX_DIAGNOSTIC_ROWS, logs.size)
        assertEquals("Event #115", logs.first().titlePreview)

        repo.clearCaptureDiagnostics()
        assertTrue(repo.captureDiagnosticsFlow.first().isEmpty())
    }

    @Test
    fun `widget snapshot ranks per-app unread counts, deduplicates threads, and prioritizes starred alerts`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        val now = System.currentTimeMillis()
        // Messages: 3 unread across 2 threads (Alex thread has 2 messages)
        ingest(repo, "w_msg_1", SupportedApp.MESSAGES, "Alex", "First ping", now + 100)
        ingest(repo, "w_msg_2", SupportedApp.MESSAGES, "Alex", "Latest ping from Alex", now + 300)
        ingest(repo, "w_msg_3", SupportedApp.MESSAGES, "Jordan", "Dinner at 7?", now + 200)
        // Gmail: 1 unread, starred (older timestamp, but starred so it must rank first)
        ingest(repo, "w_gmail_star", SupportedApp.GMAIL, "Flight Confirmation", "Gate B12", now + 50)
        repo.toggleNotificationStar("w_gmail_star")
        // Twitter: 1 read (must NOT count in unread) + 1 unread
        ingest(repo, "w_x_read", SupportedApp.TWITTER, "@news", "Already read", now + 400)
        repo.markAsRead("w_x_read")
        ingest(repo, "w_x_unread", SupportedApp.TWITTER, "@design", "New mention", now + 500)

        try {
            val snapshot = repo.loadWidgetSnapshot()
            assertEquals(5, snapshot.totalUnread)

            // Per-app breakdown: Messages (3) first, then X (1, newer postTime) before Gmail (1, older)
            assertEquals(3, snapshot.perAppCounts.size)
            assertEquals(SupportedApp.MESSAGES.packageName, snapshot.perAppCounts[0].packageName)
            assertEquals(3, snapshot.perAppCounts[0].unreadCount)
            assertEquals("SM", snapshot.perAppCounts[0].shortBadge)
            assertEquals(SupportedApp.TWITTER.packageName, snapshot.perAppCounts[1].packageName)
            assertEquals(1, snapshot.perAppCounts[1].unreadCount)
            assertEquals(SupportedApp.GMAIL.packageName, snapshot.perAppCounts[2].packageName)
            assertEquals(1, snapshot.perAppCounts[2].unreadCount)

            // Deduplicated threads: 4 unique threads, starred Gmail first, then newest unread
            assertEquals(4, snapshot.topUnreadAlerts.size)
            assertEquals("Flight Confirmation", snapshot.topUnreadAlerts[0].title)
            assertTrue(snapshot.topUnreadAlerts[0].isStarred)
            assertEquals("@design", snapshot.topUnreadAlerts[1].title)
            assertEquals("Alex", snapshot.topUnreadAlerts[2].title)
            assertEquals("Latest ping from Alex", snapshot.topUnreadAlerts[2].body)
            assertEquals("Jordan", snapshot.topUnreadAlerts[3].title)
        } finally {
            repo.toggleNotificationStar("w_gmail_star")
            repo.resetSession()
        }
    }

    @Test
    fun `widget RemoteViews adapts to compact and expanded sizes, renders empty state, and launches direct Activity intents`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()

        val now = System.currentTimeMillis()
        ingest(repo, "rv_1", SupportedApp.MESSAGES, "Sam", "See you soon", now + 1_000)
        ingest(repo, "rv_2", SupportedApp.TWITTER, "@androiddev", "Widget guide", now + 2_000)
        ingest(repo, "rv_3", SupportedApp.OUTLOOK, "Boss", "Q3 deck", now + 3_000)
        ingest(repo, "rv_4", SupportedApp.GROUPME, "Crew", "Game at 8", now + 4_000)
        repo.toggleNotificationStar("rv_1")

        try {
            val snapshot = repo.loadWidgetSnapshot().copy(
                isNotificationAccessGranted = true,
                refreshedAt = now + 65_000
            )

            // Compact layout: 1 alert row, 2 app pills
            val compactViews = NephoUnreadWidgetProvider.buildRemoteViews(
                context = context,
                snapshot = snapshot,
                minWidthDp = 190,
                minHeightDp = 120
            )
            val compactRoot = compactViews.apply(context, FrameLayout(context))
            assertEquals(View.VISIBLE, compactRoot.findViewById<View>(R.id.widget_rows_container).visibility)
            assertEquals(View.GONE, compactRoot.findViewById<View>(R.id.widget_empty_container).visibility)
            assertEquals(View.VISIBLE, compactRoot.findViewById<View>(R.id.widget_row_0).visibility)
            assertEquals(View.GONE, compactRoot.findViewById<View>(R.id.widget_row_1).visibility)
            assertEquals(View.VISIBLE, compactRoot.findViewById<View>(R.id.widget_app_pill_0).visibility)
            assertEquals(View.VISIBLE, compactRoot.findViewById<View>(R.id.widget_app_pill_1).visibility)
            assertEquals(View.GONE, compactRoot.findViewById<View>(R.id.widget_app_pill_2).visibility)
            assertEquals("4 unread", compactRoot.findViewById<TextView>(R.id.widget_unread_count_pill).text.toString())

            // Expanded layout: all 4 rows and 4 app pills visible, starred prefix rendered on row 0
            val expandedViews = NephoUnreadWidgetProvider.buildRemoteViews(
                context = context,
                snapshot = snapshot,
                minWidthDp = 300,
                minHeightDp = 300
            )
            val expandedRoot = expandedViews.apply(context, FrameLayout(context))
            assertEquals(View.VISIBLE, expandedRoot.findViewById<View>(R.id.widget_row_3).visibility)
            assertEquals(View.VISIBLE, expandedRoot.findViewById<View>(R.id.widget_app_pill_3).visibility)
            assertEquals("★ Sam", expandedRoot.findViewById<TextView>(R.id.widget_row_0_title).text.toString())

            // Tap row 0 -> direct Activity intent with EXTRA_WIDGET_THREAD_ID
            val shadowApp = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            expandedRoot.findViewById<View>(R.id.widget_row_0).performClick()
            val rowLaunch = shadowApp.nextStartedActivity
            assertNotNull(rowLaunch)
            assertEquals(MainActivity::class.java.name, rowLaunch.component?.className)
            assertEquals(snapshot.topUnreadAlerts[0].threadId, rowLaunch.getStringExtra(NephoUnreadWidgetProvider.EXTRA_WIDGET_THREAD_ID))

            // Tap app pill 0 -> direct Activity intent with EXTRA_WIDGET_APP_PACKAGE & EXTRA_WIDGET_UNREAD_ONLY
            expandedRoot.findViewById<View>(R.id.widget_app_pill_0).performClick()
            val pillLaunch = shadowApp.nextStartedActivity
            assertNotNull(pillLaunch)
            assertEquals(MainActivity::class.java.name, pillLaunch.component?.className)
            assertEquals(snapshot.perAppCounts[0].packageName, pillLaunch.getStringExtra(NephoUnreadWidgetProvider.EXTRA_WIDGET_APP_PACKAGE))
            assertTrue(pillLaunch.getBooleanExtra(NephoUnreadWidgetProvider.EXTRA_WIDGET_UNREAD_ONLY, false))

            // Empty snapshot with Notification Access off
            val emptySnapshot = NephoWidgetSnapshotBuilder.build(
                activeNotifications = emptyList(),
                sessionOpenedAt = now,
                isNotificationAccessGranted = false,
                refreshedAt = now
            )
            val emptyViews = NephoUnreadWidgetProvider.buildRemoteViews(context, emptySnapshot)
            val emptyRoot = emptyViews.apply(context, FrameLayout(context))
            assertEquals(View.GONE, emptyRoot.findViewById<View>(R.id.widget_rows_container).visibility)
            assertEquals(View.VISIBLE, emptyRoot.findViewById<View>(R.id.widget_empty_container).visibility)
            assertEquals(context.getString(R.string.widget_all_clear), emptyRoot.findViewById<TextView>(R.id.widget_unread_count_pill).text.toString())
            assertEquals(context.getString(R.string.widget_access_off), emptyRoot.findViewById<TextView>(R.id.widget_empty_subtitle).text.toString())
        } finally {
            repo.toggleNotificationStar("rv_1")
            repo.resetSession()
        }
    }

    @Test
    fun `PulseViewModel routes widget thread deep links and per-app filters accurately`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = PulseViewModel(app)

        vm.selectTab(PulseTab.LINKS_VAULT)
        vm.applyAppFilterFromWidget(
            packageName = SupportedApp.MESSAGES.packageName,
            appLabel = "Messages",
            unreadOnly = true
        )
        assertEquals(PulseTab.FEED, vm.selectedTab.value)
        assertEquals(SupportedApp.MESSAGES, vm.selectedAppFilter.value)
        assertTrue(vm.unreadOnly.value)
        assertEquals("", vm.searchQuery.value)

        // Third-party app pill falls back to appLabel search query with unreadOnly enabled
        vm.applyAppFilterFromWidget(
            packageName = "com.slack",
            appLabel = "Slack",
            unreadOnly = true
        )
        assertEquals(null, vm.selectedAppFilter.value)
        assertEquals("Slack", vm.searchQuery.value)
        assertTrue(vm.unreadOnly.value)

        // Thread deep link resets filters and publishes pendingWidgetThreadId
        val targetThread = "${SupportedApp.TWITTER.packageName}|@designweekly"
        vm.openThreadFromWidget(targetThread)
        assertEquals(PulseTab.FEED, vm.selectedTab.value)
        assertEquals(null, vm.selectedAppFilter.value)
        assertFalse(vm.unreadOnly.value)
        assertEquals("", vm.searchQuery.value)
        assertEquals(targetThread, vm.pendingWidgetThreadId.value)

        vm.clearPendingWidgetThreadId()
        assertEquals(null, vm.pendingWidgetThreadId.value)
    }

    @Test
    fun `GitHubReleaseUpdater normalizes repo slugs, compares versions, and parses APK release JSON`() {
        assertEquals("octocat/nepho", GitHubReleaseUpdater.normalizeRepoSlug("octocat/nepho"))
        assertEquals("octocat/nepho", GitHubReleaseUpdater.normalizeRepoSlug("https://github.com/octocat/nepho/releases"))
        assertEquals("octocat/nepho", GitHubReleaseUpdater.normalizeRepoSlug("github.com/octocat/nepho.git"))
        assertEquals(null, GitHubReleaseUpdater.normalizeRepoSlug("not a repo"))

        assertTrue(GitHubReleaseUpdater.compareVersions("v1.0.1", "1.0") > 0)
        assertTrue(GitHubReleaseUpdater.compareVersions("v1.0.10", "v1.0.2") > 0)
        assertEquals(0, GitHubReleaseUpdater.compareVersions("v1.0.4", "1.0.4"))
        assertTrue(GitHubReleaseUpdater.compareVersions("v1.0.3", "1.0.4") < 0)

        val releaseJson = """
            {
              "tag_name": "v1.0.5",
              "name": "Nepho 1.0.5",
              "body": "- Added home widget\n- Added web companion",
              "html_url": "https://github.com/octocat/nepho/releases/tag/v1.0.5",
              "published_at": "2026-11-19T18:00:00Z",
              "assets": [
                {
                  "name": "nepho-debug.apk",
                  "browser_download_url": "https://github.com/octocat/nepho/releases/download/v1.0.5/nepho-debug.apk"
                }
              ]
            }
        """.trimIndent()

        val available = GitHubReleaseUpdater.parseLatestReleaseJson(
            jsonText = releaseJson,
            currentVersionName = "1.0",
            repoSlug = "octocat/nepho"
        )
        assertTrue(available is UpdateCheckState.UpdateAvailable)
        val update = available as UpdateCheckState.UpdateAvailable
        assertEquals("v1.0.5", update.latestTag)
        assertEquals(
            "https://github.com/octocat/nepho/releases/download/v1.0.5/nepho-debug.apk",
            update.downloadUrl
        )
        assertTrue(update.releaseNotes.contains("Added web companion"))

        val upToDate = GitHubReleaseUpdater.parseLatestReleaseJson(
            jsonText = releaseJson,
            currentVersionName = "1.0.5",
            repoSlug = "octocat/nepho"
        )
        assertTrue(upToDate is UpdateCheckState.UpToDate)
    }

    @Test
    fun `LauncherAppCatalog filters with prefix priority and groups alphabetically`() {
        val sampleApps = listOf(
            LauncherAppInfo("com.music.app", "Main", "Apple Music", "A"),
            LauncherAppInfo("com.calc.app", "Main", "Calculator", "C"),
            LauncherAppInfo("com.calendar.app", "Main", "Calendar", "C"),
            LauncherAppInfo("com.spotify.music", "Main", "Spotify", "S"),
            LauncherAppInfo("com.num.app", "Main", "1Password", "#")
        )

        val grouped = LauncherAppCatalog.groupAlphabetically(sampleApps)
        assertEquals(listOf("A", "C", "S", "#"), grouped.keys.toList())
        assertEquals(2, grouped["C"]?.size)

        // Prefix match ("Spotify") comes before substring match ("Apple Music" via package/name)
        val musicFiltered = LauncherAppCatalog.filterApps(sampleApps, "music")
        assertEquals(listOf("com.music.app", "com.spotify.music"), musicFiltered.map { it.packageName })

        val calFiltered = LauncherAppCatalog.filterApps(sampleApps, "cal")
        assertEquals(listOf("Calculator", "Calendar"), calFiltered.map { it.label })

        val seeded = LauncherAppCatalog.seedDefaultShortcuts(sampleApps)
        assertEquals(5, seeded.size)
        assertTrue("com.spotify.music" in seeded)
    }

    @Test
    fun `ClockAlarmHelper builds compliant SET_ALARM and SHOW_ALARMS intents`() {
        val setIntent = ClockAlarmHelper.buildSetAlarmIntent(hour = 7, minute = 30, label = "Morning Run")
        assertEquals(AlarmClock.ACTION_SET_ALARM, setIntent.action)
        assertEquals(7, setIntent.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(30, setIntent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertEquals("Morning Run", setIntent.getStringExtra(AlarmClock.EXTRA_MESSAGE))
        assertTrue(setIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)

        val showIntent = ClockAlarmHelper.buildShowAlarmsIntent()
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, showIntent.action)
        assertTrue(showIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `pinned shortcuts persist across SessionPreferences updates`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PulseRepository.getInstance(context).sessionPreferences

        prefs.setPinnedShortcuts(listOf("com.android.chrome", "com.spotify.music", "com.android.chrome"))
        assertEquals(
            listOf("com.android.chrome", "com.spotify.music"),
            prefs.pinnedShortcutsFlow.first()
        )
        assertTrue(prefs.shortcutsCustomizedFlow.first())
    }

    @Test
    fun `WeatherService parses Open-Meteo geocoding, forecast, prioritizes weather apps, and persists shortcut`() = runTest {
        val geocodingJson = """
            {
              "results": [
                { "name": "Seattle", "admin1": "Washington", "country_code": "US", "latitude": 47.6062, "longitude": -122.3321 }
              ]
            }
        """.trimIndent()
        val cities = WeatherService.parseGeocodingJson(geocodingJson)
        assertEquals(1, cities.size)
        assertEquals("Seattle, Washington, US", cities.first().displayName)
        assertEquals(47.6062, cities.first().latitude, 0.001)

        val forecastJson = """
            {
              "current": { "temperature_2m": 20.0, "weather_code": 61, "is_day": 1 },
              "daily": { "temperature_2m_max": [23.0], "temperature_2m_min": [14.0] }
            }
        """.trimIndent()
        val forecast = WeatherService.parseForecastJson(forecastJson)
        assertNotNull(forecast)
        assertEquals(68, WeatherService.convertTemp(forecast!!.tempC, useFahrenheit = true))
        assertEquals(20, WeatherService.convertTemp(forecast.tempC, useFahrenheit = false))
        assertEquals("Rain", WeatherService.describeWeatherCode(forecast.weatherCode))
        assertEquals(WeatherConditionKind.RAIN, WeatherService.mapConditionKind(forecast.weatherCode, true))

        val prioritized = WeatherService.prioritizeWeatherApps(
            listOf(
                LauncherAppInfo("com.android.chrome", "Main", "Chrome", "C"),
                LauncherAppInfo("com.accuweather.android", "Main", "AccuWeather", "A"),
                LauncherAppInfo("com.spotify.music", "Main", "Spotify", "S")
            )
        )
        assertEquals("com.accuweather.android", prioritized.first().packageName)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PulseRepository.getInstance(context).sessionPreferences
        prefs.setWeatherLocation("Seattle, WA", 47.6062, -122.3321)
        prefs.setWeatherShortcutPackage("com.accuweather.android")
        prefs.saveWeatherSnapshot(tempC = 20.0, highC = 23.0, lowC = 14.0, weatherCode = 61, isDay = true)

        val saved = prefs.weatherPrefsFlow.first()
        assertEquals("Seattle, WA", saved.locationName)
        assertEquals("com.accuweather.android", saved.shortcutPackage)
        assertEquals(20.0, saved.cachedTempC!!, 0.001)
    }

    @Test
    fun `home button press clears app drawer search and emits return-to-top event`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = PulseViewModel(context)
        vm.updateAppSearchQuery("spot")
        assertEquals("spot", vm.appSearchQuery.value)

        val homeEvent = async(start = CoroutineStart.UNDISPATCHED) {
            vm.homePressedEvents.first()
        }

        vm.onHomeButtonPressed()
        withTimeout(2_000) { homeEvent.await() }
        assertEquals("", vm.appSearchQuery.value)
    }

    @Test
    fun `CalendarScheduleProvider groups today tomorrow and next day with UTC all-day safety and multi-day spans`() {
        val laTz = java.util.TimeZone.getTimeZone("America/Los_Angeles")
        val utcTz = java.util.TimeZone.getTimeZone("UTC")

        // Reference local time: Thursday, Nov 19, 2026 at 09:00 PST
        val nowLocal = java.util.Calendar.getInstance(laTz).apply {
            clear()
            set(2026, java.util.Calendar.NOVEMBER, 19, 9, 0, 0)
        }.timeInMillis

        fun localTime(day: Int, hour: Int, minute: Int = 0): Long {
            return java.util.Calendar.getInstance(laTz).apply {
                clear()
                set(2026, java.util.Calendar.NOVEMBER, day, hour, minute, 0)
            }.timeInMillis
        }

        fun utcMidnight(day: Int): Long {
            return java.util.Calendar.getInstance(utcTz).apply {
                clear()
                set(2026, java.util.Calendar.NOVEMBER, day, 0, 0, 0)
            }.timeInMillis
        }

        val events = listOf(
            // Timed event Today 10:00–11:00
            com.example.launcher.CalendarEventItem(
                eventId = 1L,
                title = "Standup Sync",
                startMillis = localTime(19, 10),
                endMillis = localTime(19, 11),
                allDay = false,
                location = "Meet"
            ),
            // UTC midnight all-day event Today (Nov 19 UTC -> Nov 20 UTC)
            com.example.launcher.CalendarEventItem(
                eventId = 2L,
                title = "Product Launch Day",
                startMillis = utcMidnight(19),
                endMillis = utcMidnight(20),
                allDay = true
            ),
            // Multi-day timed event spanning Today 22:00 to Tomorrow 02:00
            com.example.launcher.CalendarEventItem(
                eventId = 3L,
                title = "Overnight Maintenance",
                startMillis = localTime(19, 22),
                endMillis = localTime(20, 2),
                allDay = false
            ),
            // Next Day (Saturday Nov 21) timed event
            com.example.launcher.CalendarEventItem(
                eventId = 4L,
                title = "Soccer Match",
                startMillis = localTime(21, 14),
                endMillis = localTime(21, 16),
                allDay = false
            ),
            // Day + 3 (Sunday Nov 22) should be excluded
            com.example.launcher.CalendarEventItem(
                eventId = 5L,
                title = "Too Far Ahead",
                startMillis = localTime(22, 10),
                endMillis = localTime(22, 11),
                allDay = false
            )
        )

        val groups = com.example.launcher.CalendarScheduleProvider.groupEventsIntoThreeDays(
            events = events,
            nowMillis = nowLocal,
            timeZone = laTz,
            locale = java.util.Locale.US
        )

        assertEquals(3, groups.size)
        assertEquals("Today", groups[0].dayTitle)
        assertEquals("Tomorrow", groups[1].dayTitle)
        assertEquals("Saturday", groups[2].dayTitle)

        // Today: All-day event sorted first, then 10:00 Standup, then 22:00 Overnight Maintenance
        assertEquals(listOf(2L, 1L, 3L), groups[0].events.map { it.eventId })
        // Tomorrow: Overnight Maintenance carries over; all-day Nov 19 does NOT leak into Tomorrow
        assertEquals(listOf(3L), groups[1].events.map { it.eventId })
        // Next Day: Soccer Match only (Day + 3 excluded)
        assertEquals(listOf(4L), groups[2].events.map { it.eventId })

        assertEquals(
            "All day",
            com.example.launcher.CalendarScheduleProvider.formatEventTimeRange(
                event = events[1],
                is24Hour = false,
                allDayLabel = "All day",
                timeZone = laTz,
                locale = java.util.Locale.US
            )
        )
        assertEquals(
            "10:00 AM – 11:00 AM",
            com.example.launcher.CalendarScheduleProvider.formatEventTimeRange(
                event = events[0],
                is24Hour = false,
                allDayLabel = "All day",
                timeZone = laTz,
                locale = java.util.Locale.US
            )
        )
    }

    @Test
    fun `preferred music service prioritizes music apps, binds preferred MediaSession, and persists in DataStore`() = runTest {
        val sampleApps = listOf(
            LauncherAppInfo("com.android.chrome", "Main", "Chrome", "C"),
            LauncherAppInfo("com.apple.android.music", "Main", "Apple Music", "A"),
            LauncherAppInfo("com.custom.radio.fm", "Main", "Indie Radio", "I"),
            LauncherAppInfo("com.spotify.music", "Main", "Spotify", "S"),
            LauncherAppInfo("com.google.android.apps.maps", "Main", "Maps", "M")
        )

        val prioritized = LauncherAppCatalog.prioritizeMusicApps(sampleApps)
        assertEquals(
            listOf(
                "com.spotify.music",
                "com.apple.android.music",
                "com.custom.radio.fm",
                "com.android.chrome",
                "com.google.android.apps.maps"
            ),
            prioritized.map { it.packageName }
        )
        assertTrue(LauncherAppCatalog.isKnownMusicApp(sampleApps[1]))
        assertTrue(LauncherAppCatalog.isKnownMusicApp(sampleApps[2]))
        assertFalse(LauncherAppCatalog.isKnownMusicApp(sampleApps[0]))

        val candidates = listOf(
            com.example.launcher.MediaSessionCandidate(
                packageName = "com.google.android.youtube",
                isPlaying = true,
                hasTitle = true
            ),
            com.example.launcher.MediaSessionCandidate(
                packageName = "com.spotify.music",
                isPlaying = false,
                hasTitle = true
            )
        )
        // Auto mode selects the actively playing session
        assertEquals(
            0,
            com.example.launcher.MediaPlaybackController.selectBestControllerIndex(candidates, null)
        )
        // Explicit preference for Spotify prioritizes Spotify's active session
        assertEquals(
            1,
            com.example.launcher.MediaPlaybackController.selectBestControllerIndex(
                candidates,
                "com.spotify.music"
            )
        )
        // When preferred service has no session, falls back to actively playing session
        assertEquals(
            0,
            com.example.launcher.MediaPlaybackController.selectBestControllerIndex(
                candidates,
                "com.apple.android.music"
            )
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PulseRepository.getInstance(context).sessionPreferences
        prefs.setPreferredMusicPackage("com.spotify.music")
        assertEquals("com.spotify.music", prefs.preferredMusicPackageFlow.first())
        prefs.setPreferredMusicPackage(null)
        assertEquals(null, prefs.preferredMusicPackageFlow.first())
    }

    @Test
    fun `HomeGestureActions validates swipe-left geometry, screen lock activation intent, and Google Search fallback chain`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Swipe-left geometry checks
        assertTrue(com.example.launcher.HomeGestureActions.isHorizontalSwipeLeft(-140f, 12f, 72f))
        assertFalse(com.example.launcher.HomeGestureActions.isHorizontalSwipeLeft(140f, 0f, 72f)) // swipe right
        assertFalse(com.example.launcher.HomeGestureActions.isHorizontalSwipeLeft(-40f, 0f, 72f)) // too short
        assertFalse(com.example.launcher.HomeGestureActions.isHorizontalSwipeLeft(-90f, 85f, 72f)) // too vertical
        assertFalse(com.example.launcher.HomeGestureActions.isHorizontalSwipeLeft(-20f, -180f, 72f)) // vertical scroll

        // Device admin screen-off state & intent
        assertFalse(com.example.launcher.HomeGestureActions.isScreenLockEnabled(context))
        assertEquals(
            com.example.launcher.ScreenLockResult.NEEDS_ADMIN_ACTIVATION,
            com.example.launcher.HomeGestureActions.turnScreenOff(context)
        )
        val adminIntent = com.example.launcher.HomeGestureActions.buildActivateScreenLockIntent(context)
        assertEquals(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN, adminIntent.action)
        assertEquals(
            com.example.launcher.HomeGestureActions.adminComponent(context),
            @Suppress("DEPRECATION")
            adminIntent.getParcelableExtra<android.content.ComponentName>(
                android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN
            )
        )
        assertTrue(
            adminIntent.getStringExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION)
                .orEmpty()
                .isNotBlank()
        )

        // Google Search fallback intent chain
        val searchIntents = com.example.launcher.HomeGestureActions.buildGoogleSearchIntents(context)
        assertTrue(searchIntents.size >= 4)
        assertEquals(android.app.SearchManager.INTENT_ACTION_GLOBAL_SEARCH, searchIntents.first().action)
        assertEquals("com.google.android.googlequicksearchbox", searchIntents.first().`package`)
        assertEquals(Intent.ACTION_VIEW, searchIntents.last().action)
        assertEquals("https://www.google.com", searchIntents.last().data.toString())
    }
}





