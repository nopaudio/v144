package com.khaiphraban.marketplace.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val (title, message) = reminders.random()
        val id = 9_000 + (System.currentTimeMillis() % 900L).toInt()
        NotificationHelper.showReminder(applicationContext, id, title, message)
        return Result.success()
    }

    companion object {
        private val reminders = listOf(
            "มีคนลงพระใหม่ เผื่อคุณสนใจ" to "แวะดูประกาศใหม่ล่าสุดได้เลย อาจมีองค์ที่คุณกำลังตามหา",
            "วันนี้คุณลงขายพระหรือยัง?" to "มีพระที่อยากปล่อยอยู่ไหม ลงประกาศได้ง่าย ๆ ในไม่กี่ขั้นตอน",
            "พระใหม่เข้ามาแล้ว" to "เปิดดูรายการล่าสุดจากสมาชิกในตลาดพระออนไลน์",
            "เผื่อเจอองค์ที่ถูกใจ" to "วันนี้มีพระหลายรายการน่าสนใจ ลองแวะเข้ามาดูสักนิด",
            "อย่าพลาดประกาศใหม่" to "สมาชิกกำลังลงพระเพิ่มเรื่อย ๆ แตะเพื่อดูรายการล่าสุด",
            "มีพระอยู่ในมือ อย่าเก็บไว้เฉย ๆ" to "ลองลงขายวันนี้ เพิ่มโอกาสให้คนที่กำลังตามหาได้เห็น",
            "แวะมาดูพระกันสักหน่อย" to "ตลาดมีรายการใหม่ให้ชม แตะเพื่อเปิดแอป",
            "องค์ที่คุณตามหาอาจมาแล้ว" to "ลองเช็กพระมาใหม่วันนี้ เผื่อเจอรุ่นหรือพิมพ์ที่กำลังหา",
            "ลงประกาศฟรี ใช้เวลาไม่นาน" to "ถ้ามีพระอยากขาย วันนี้เป็นอีกวันที่ดีสำหรับการลงประกาศ",
            "มีอะไรใหม่ในตลาดพระออนไลน์?" to "แตะเข้ามาดูประกาศล่าสุดจากสมาชิกได้เลย",
            "วันนี้แวะเข้าตลาดหรือยัง?" to "พระใหม่ ๆ กำลังรอให้คุณเข้ามาชม",
            "โอกาสดี ๆ อาจอยู่ในประกาศล่าสุด" to "ลองเปิดดูรายการใหม่ เผื่อเจอพระที่เหมาะกับคุณ"
        )
    }
}
