package com.khaiphraban.marketplace.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * วางแผนแจ้งเตือน 1-3 ครั้งต่อวัน โดยสุ่มเวลาเฉพาะช่วงประมาณ 09:00-21:00
 * WorkManager อาจขยับเวลาเล็กน้อยตามการประหยัดแบตเตอรี่ของ Android
 */
class ReminderDispatchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val workManager = WorkManager.getInstance(applicationContext)
        workManager.cancelAllWorkByTag(TAG_DAILY_REMINDER)

        val count = Random.nextInt(1, 4)
        randomDaytimeDelays(count).forEach { delayMinutes ->
            val request = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .addTag(TAG_DAILY_REMINDER)
                .build()
            workManager.enqueue(request)
        }
        return Result.success()
    }

    private fun randomDaytimeDelays(count: Int): List<Long> {
        val now = System.currentTimeMillis()
        val start = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            val hour = get(Calendar.HOUR_OF_DAY)
            when {
                hour < 9 -> {
                    set(Calendar.HOUR_OF_DAY, 9)
                    set(Calendar.MINUTE, 0)
                }
                hour < 20 -> {
                    add(Calendar.MINUTE, 30)
                }
                else -> {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 9)
                    set(Calendar.MINUTE, 0)
                }
            }
        }

        val end = (start.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
            if (timeInMillis <= start.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 21)
            }
        }

        val minDelay = ((start.timeInMillis - now) / 60_000L).coerceAtLeast(1L)
        val maxDelay = ((end.timeInMillis - now) / 60_000L).coerceAtLeast(minDelay + 1L)

        val picked = mutableSetOf<Long>()
        var guard = 0
        while (picked.size < count && guard < 100) {
            picked += Random.nextLong(minDelay, maxDelay + 1L)
            guard++
        }
        return picked.sorted()
    }

    companion object {
        const val TAG_DAILY_REMINDER = "daily_random_reminder_notification"
    }
}
