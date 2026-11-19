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
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.NativeReplyHandle
import com.example.data.NotificationImageStore
import com.example.data.PulseRepository
import com.example.data.ShadeMode
import com.example.data.SupportedApp
import com.example.data.UrlExtractor
import com.example.notifications.NephoNotificationListener
import com.example.ui.PulseViewModel
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
    fun `gif replies require destination support and deliver the selected uri`() = runTest {
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
            app = SupportedApp.MESSAGES,
            title = "Jordan",
            body = "Send a GIF",
            postTime = now,
            canReply = true
        )

        assertTrue(repo.canSendGifReply("gif_reply_test_key"))
        val gifUri = Uri.parse("content://com.example.test/gifs/reaction.gif")
        assertTrue(repo.sendInlineReply("gif_reply_test_key", "", gifUri))

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
    fun `gif replies are rejected when destination only supports text`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PulseRepository.getInstance(context)
        repo.resetSession()
        val key = "text_only_reply_test_key"
        val pendingIntent = PendingIntent.getActivity(
            context,
            211,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        repo.registerNativeActionHandle(
            key,
            NativeReplyHandle(
                replyPendingIntent = pendingIntent,
                frameworkRemoteInputs = arrayOf(
                    android.app.RemoteInput.Builder("reply").build()
                )
            )
        )
        ingest(
            repo = repo,
            key = key,
            app = SupportedApp.MESSAGES,
            title = "Jordan",
            body = "Text only",
            postTime = System.currentTimeMillis(),
            canReply = true
        )

        assertFalse(repo.canSendGifReply(key))
        assertFalse(
            repo.sendInlineReply(
                key,
                "",
                Uri.parse("content://com.example.test/gifs/reaction.gif")
            )
        )
        assertFalse(repo.getNotificationByKey(key)!!.isDismissed)
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
}

