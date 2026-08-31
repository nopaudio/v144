package com.khaiphraban.marketplace.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.khaiphraban.marketplace.data.network.ApiClient
import com.khaiphraban.marketplace.data.repository.MarketplaceRepository
import com.khaiphraban.marketplace.data.session.SessionManager
import kotlinx.coroutines.flow.first

/**
 * สำรองการตรวจข้อความตอนแอปไม่ได้เปิดอยู่
 * Android กำหนด Periodic Work ขั้นต่ำประมาณ 15 นาที จึงไม่ใช่ push แบบ LINE 100%
 * แต่ช่วยให้ยังมีเสียงแจ้งเตือนโดยไม่ต้องตั้ง Firebase เพิ่ม
 */
class ChatBackgroundWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val session = SessionManager(applicationContext).session.first()
        val token = session.token ?: return Result.success()
        if (session.userId <= 0) return Result.success()

        val repository = MarketplaceRepository(ApiClient.service)
        val store = ChatNotificationStore(applicationContext)
        val cursor = store.get(session.userId)

        if (!store.isInitialized(session.userId)) {
            repository.latestChatId(token).onSuccess {
                store.initialize(session.userId, it.latestId)
            }
            return Result.success()
        }

        repository.chatUpdates(token, cursor).fold(
            { updates ->
                if (updates.isNotEmpty()) {
                    val newestId = updates.maxOf { it.id }
                    if (store.claim(session.userId, newestId)) {
                        if (updates.size == 1) {
                            val item = updates.first()
                            NotificationHelper.showChat(
                                applicationContext,
                                item.id,
                                item.senderUsername,
                                item.message
                            )
                        } else {
                            NotificationHelper.showChatSummary(
                                applicationContext,
                                newestId,
                                updates.size
                            )
                        }
                    }
                }
            },
            {
                // ไม่ retry รัว ๆ เพื่อประหยัดแบตเตอรี่/เน็ต
            }
        )
        return Result.success()
    }
}
