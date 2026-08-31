package com.khaiphraban.marketplace.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.khaiphraban.marketplace.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MarketplaceFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushTokenManager.registerToken(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val type = data["type"].orEmpty()

        if (type == "admin_task") {
            val recipientUserId = data["recipient_user_id"]?.toIntOrNull() ?: return
            val notificationId = data["admin_notification_id"]?.toIntOrNull()
                ?: (System.currentTimeMillis() % 10_000L).toInt()
            val title = data["title"].orEmpty().ifBlank { "งานผู้ดูแลระบบ" }
            val body = data["body"].orEmpty().ifBlank { "มีรายการใหม่ที่ต้องตรวจสอบ" }
            val route = data["mobile_route"].orEmpty()
                .takeIf { it == "admin" || it.startsWith("admin/") }
                ?: "admin"

            serviceScope.launch {
                // Admin push is displayed only when this device is currently
                // signed in as the exact Admin recipient selected by the server.
                val session = SessionManager(applicationContext).session.first()
                if (!session.isLoggedIn || !session.isAdmin || session.userId != recipientUserId) {
                    return@launch
                }
                NotificationHelper.showAdminTask(
                    context = applicationContext,
                    notificationId = notificationId,
                    title = title,
                    message = body,
                    route = route
                )
            }
            return
        }

        if (type == "admin_notification") {
            val title = data["title"].orEmpty().ifBlank { "ตลาดพระออนไลน์" }
            val body = data["body"].orEmpty().ifBlank { "มีการแจ้งเตือนใหม่" }
            NotificationHelper.showReminder(
                applicationContext,
                30_000 + (System.currentTimeMillis() % 9_000L).toInt(),
                title,
                body
            )
            return
        }

        if (type == "order") {
            val recipientUserId = data["recipient_user_id"]?.toIntOrNull() ?: return
            val orderId = data["order_id"]?.toIntOrNull() ?: return
            val title = data["title"].orEmpty().ifBlank { "อัปเดตคำสั่งซื้อ" }
            val body = data["body"].orEmpty().ifBlank { "มีการเปลี่ยนแปลงคำสั่งซื้อในตลาดพระออนไลน์" }

            serviceScope.launch {
                val session = SessionManager(applicationContext).session.first()
                if (!session.isLoggedIn || session.userId != recipientUserId) return@launch
                NotificationHelper.showOrder(
                    context = applicationContext,
                    orderId = orderId,
                    title = title,
                    message = body
                )
            }
            return
        }

        if (type != "chat") return

        val recipientUserId = data["recipient_user_id"]?.toIntOrNull() ?: return
        val listingId = data["listing_id"]?.toIntOrNull() ?: return
        val buyerId = data["buyer_id"]?.toIntOrNull() ?: return
        val messageId = data["message_id"]?.toLongOrNull() ?: System.currentTimeMillis()
        val title = data["title"].orEmpty().ifBlank { "ข้อความแชทใหม่" }
        val body = data["body"].orEmpty().ifBlank { "มีข้อความใหม่ในตลาดพระออนไลน์" }

        serviceScope.launch {
            // Data-only push lets us check the active member before showing it.
            // This avoids leaking an old account's chat after that member logs out.
            val session = SessionManager(applicationContext).session.first()
            if (!session.isLoggedIn || session.userId != recipientUserId) return@launch

            NotificationHelper.showChat(
                context = applicationContext,
                messageId = messageId,
                title = title,
                message = body,
                listingId = listingId,
                buyerId = buyerId
            )
        }
    }
}
