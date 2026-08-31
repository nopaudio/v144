package com.khaiphraban.marketplace.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.khaiphraban.marketplace.MainActivity
import com.khaiphraban.marketplace.R

object NotificationHelper {
    const val REMINDER_CHANNEL_ID = "khaiphraban_reminders_v2"

    // V2 channel intentionally gets a new id so users upgrading from V4 receive
    // the new HIGH importance + sound settings even if the old channel was quiet.
    const val CHAT_CHANNEL_ID = "khaiphraban_chat_v2"
    // V12 uses a fresh channel id because Android does not apply sound changes
    // to a notification channel that was already created on the device.
    const val ORDER_CHANNEL_ID = "khaiphraban_orders_v12"
    const val ADMIN_CHANNEL_ID = "khaiphraban_admin_v1"

    const val EXTRA_OPEN_CHATS = "open_chats"
    const val EXTRA_LISTING_ID = "chat_listing_id"
    const val EXTRA_BUYER_ID = "chat_buyer_id"
    const val EXTRA_ORDER_ID = "order_id"
    const val EXTRA_ADMIN_ROUTE = "admin_route"

    private fun rawSoundUri(context: Context, soundResId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$soundResId")

    private fun createChannel(
        context: Context,
        id: String,
        name: String,
        description: String,
        soundResId: Int? = null
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        runCatching {
            if (manager.getNotificationChannel(id) != null) return

            val soundUri = soundResId?.let { rawSoundUri(context, it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(
                id,
                name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                this.description = description
                enableVibration(true)
                setSound(soundUri, audioAttributes)
            }
            manager.createNotificationChannel(channel)
        }.onFailure {
            // Some devices/ROMs can reject a custom resource sound while a
            // notification channel is being created. Keep V12 usable and retain
            // order notifications by falling back to the system notification sound.
            if (soundResId != null) {
                runCatching {
                    if (manager.getNotificationChannel(id) == null) {
                        val fallback = NotificationChannel(
                            id,
                            name,
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            this.description = description
                            enableVibration(true)
                            setSound(
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                    .build()
                            )
                        }
                        manager.createNotificationChannel(fallback)
                    }
                }
            }
        }
    }

    fun ensureChannels(context: Context) {
        createChannel(
            context,
            REMINDER_CHANNEL_ID,
            "แจ้งเตือนตลาดพระออนไลน์",
            "ข่าวพระใหม่และคำเตือนให้กลับมาใช้งานแอป"
        )
        createChannel(
            context,
            CHAT_CHANNEL_ID,
            "ข้อความแชท",
            "แจ้งเตือนทันทีเมื่อมีสมาชิกส่งข้อความหรือรูปภาพใหม่"
        )
        createChannel(
            context,
            ORDER_CHANNEL_ID,
            "คำสั่งซื้อ",
            "คำสั่งซื้อใหม่ สถานะคำสั่งซื้อ การยืนยัน และการจัดส่ง",
            R.raw.money_in
        )
        createChannel(
            context,
            ADMIN_CHANNEL_ID,
            "งานผู้ดูแลระบบ",
            "แจ้งเตือนเฉพาะงานที่ผู้ดูแลต้องตรวจหรืออนุมัติ"
        )
    }

    private fun openAppPendingIntent(
        context: Context,
        requestCode: Int,
        openChats: Boolean,
        listingId: Int? = null,
        buyerId: Int? = null,
        orderId: Int? = null,
        adminRoute: String? = null
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_CHATS, openChats)
            listingId?.takeIf { it > 0 }?.let { putExtra(EXTRA_LISTING_ID, it) }
            buyerId?.takeIf { it > 0 }?.let { putExtra(EXTRA_BUYER_ID, it) }
            orderId?.takeIf { it > 0 }?.let { putExtra(EXTRA_ORDER_ID, it) }
            adminRoute?.takeIf { it == "admin" || it.startsWith("admin/") }
                ?.let { putExtra(EXTRA_ADMIN_ROUTE, it) }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notify(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        message: String,
        openChats: Boolean,
        listingId: Int? = null,
        buyerId: Int? = null,
        orderId: Int? = null,
        adminRoute: String? = null,
        soundResId: Int? = null
    ) {
        ensureChannels(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(
                openAppPendingIntent(
                    context = context,
                    requestCode = notificationId,
                    openChats = openChats,
                    listingId = listingId,
                    buyerId = buyerId,
                    orderId = orderId,
                    adminRoute = adminRoute
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(
                if (openChats) NotificationCompat.CATEGORY_MESSAGE
                else NotificationCompat.CATEGORY_RECOMMENDATION
            )

        if (soundResId != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder
                .setSound(rawSoundUri(context, soundResId))
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
        } else {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        val notification = builder.build()

        runCatching { manager.notify(notificationId, notification) }
    }

    fun showReminder(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        notify(
            context = context,
            channelId = REMINDER_CHANNEL_ID,
            notificationId = notificationId,
            title = title,
            message = message,
            openChats = false
        )
    }

    /**
     * Firebase push version: opens the exact conversation when tapped.
     */
    fun showChat(
        context: Context,
        messageId: Long,
        title: String,
        message: String,
        listingId: Int,
        buyerId: Int
    ) {
        notify(
            context = context,
            channelId = CHAT_CHANNEL_ID,
            notificationId = 20_000 + (messageId % 10_000L).toInt(),
            title = title,
            message = message,
            openChats = true,
            listingId = listingId,
            buyerId = buyerId
        )
    }

    /**
     * Legacy polling fallback overload. Kept so already-enqueued V4 WorkManager
     * jobs can finish safely while V5 cancels future polling.
     */
    fun showChat(
        context: Context,
        messageId: Long,
        senderName: String,
        message: String
    ) {
        notify(
            context = context,
            channelId = CHAT_CHANNEL_ID,
            notificationId = 20_000 + (messageId % 10_000L).toInt(),
            title = "ข้อความใหม่จาก $senderName",
            message = message,
            openChats = true
        )
    }

    fun showChatSummary(context: Context, newestMessageId: Long, count: Int) {
        notify(
            context = context,
            channelId = CHAT_CHANNEL_ID,
            notificationId = 29_999,
            title = "คุณมีข้อความแชทใหม่ $count ข้อความ",
            message = "แตะเพื่อเปิดดูข้อความใหม่ในตลาดพระออนไลน์",
            openChats = true
        )
    }

    fun showOrder(
        context: Context,
        orderId: Int,
        title: String,
        message: String
    ) {
        notify(
            context = context,
            channelId = ORDER_CHANNEL_ID,
            notificationId = 40_000 + (orderId % 10_000),
            title = title,
            message = message,
            openChats = false,
            orderId = orderId,
            soundResId = R.raw.money_in
        )
    }

    fun showAdminTask(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        route: String
    ) {
        notify(
            context = context,
            channelId = ADMIN_CHANNEL_ID,
            notificationId = 50_000 + (notificationId % 10_000),
            title = title,
            message = message,
            openChats = false,
            adminRoute = route.takeIf { it == "admin" || it.startsWith("admin/") } ?: "admin"
        )
    }

}
