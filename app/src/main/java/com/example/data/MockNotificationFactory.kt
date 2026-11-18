package com.example.data

import java.util.UUID

data class SimulatedNotificationSpec(
    val key: String,
    val notificationId: Int,
    val app: SupportedApp,
    val title: String,
    val body: String,
    val postTime: Long,
    val canReply: Boolean,
    val secondaryActions: List<String>
)

object MockNotificationFactory {

    fun createRealisticBatch(now: Long = System.currentTimeMillis()): List<SimulatedNotificationSpec> {
        val batchToken = UUID.randomUUID().toString().take(6)
        return listOf(
            SimulatedNotificationSpec(
                key = "sim_outlook_$batchToken",
                notificationId = (now % 100000).toInt() + 101,
                app = SupportedApp.OUTLOOK,
                title = "Maya Patel • Q4 Budget & Vendor Invoice",
                body = "Urgent: Finance needs your sign-off on Invoice #INV-4092 before 4:00 PM today. Review the updated sheet at https://contoso.sharepoint.com/sites/finance/inv-4092 and reply with approval.",
                postTime = now - 120_000L,
                canReply = true,
                secondaryActions = listOf("Archive", "Delete")
            ),
            SimulatedNotificationSpec(
                key = "sim_teamsnap_$batchToken",
                notificationId = (now % 100000).toInt() + 102,
                app = SupportedApp.TEAMSNAP,
                title = "Coach Ramirez • U14 Soccer Practice Update",
                body = "Field 3 is wet, so tonight's practice moves to Turf B at 5:30 PM. Please confirm availability for Saturday's game schedule at https://go.teamsnap.com/events/u14-saturday-turf",
                postTime = now - 420_000L,
                canReply = true,
                secondaryActions = listOf("Mark Read", "Mute")
            ),
            SimulatedNotificationSpec(
                key = "sim_gmail_$batchToken",
                notificationId = (now % 100000).toInt() + 103,
                app = SupportedApp.GMAIL,
                title = "Stripe Billing • Action Required: Receipt & Renewal",
                body = "Your workspace invoice #8841 is ready for review. Download PDF receipt and update billing contacts here: https://dashboard.stripe.com/invoices/8841-workspace",
                postTime = now - 900_000L,
                canReply = false,
                secondaryActions = listOf("Archive", "Delete")
            ),
            SimulatedNotificationSpec(
                key = "sim_groupme_$batchToken",
                notificationId = (now % 100000).toInt() + 104,
                app = SupportedApp.GROUPME,
                title = "Weekend Carpool Crew",
                body = "Alex: Hey team! I posted the Saturday game carpool sheet and snack rotation here https://go.teamsnap.com/events/u14-saturday-turf — reply if you can drive morning leg!",
                postTime = now - 1_500_000L,
                canReply = true,
                secondaryActions = listOf("Mark Read", "Mute")
            ),
            SimulatedNotificationSpec(
                key = "sim_sms_$batchToken",
                notificationId = (now % 100000).toInt() + 105,
                app = SupportedApp.MESSAGES,
                title = "Jordan (Mobile)",
                body = "Running 10 mins behind for the 10am sync—can you share the Figma deck link before we start? Here is my draft notes doc: https://docs.google.com/document/d/nepho-sync-notes",
                postTime = now - 2_100_000L,
                canReply = true,
                secondaryActions = listOf("Mark Read")
            )
        )
    }

    fun createExpiredTestNotification(now: Long = System.currentTimeMillis()): SimulatedNotificationSpec {
        val token = UUID.randomUUID().toString().take(6)
        val twentyFiveHoursAgo = now - (25 * 60 * 60 * 1000L)
        return SimulatedNotificationSpec(
            key = "sim_expired_$token",
            notificationId = (now % 100000).toInt() + 999,
            app = SupportedApp.GMAIL,
            title = "Expired Archive Alert (25h Old)",
            body = "This alert is 25 hours old with https://expired.example.com/old-report and must be automatically purged by the 24h rolling retention policy.",
            postTime = twentyFiveHoursAgo,
            canReply = false,
            secondaryActions = listOf("Archive")
        )
    }
}
