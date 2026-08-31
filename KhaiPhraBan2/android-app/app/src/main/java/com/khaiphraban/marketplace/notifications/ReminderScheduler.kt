package com.khaiphraban.marketplace.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val WORK_DAILY_PLANNER = "daily_random_reminder_planner_v2"

    fun scheduleDaily(context: Context) {
        NotificationHelper.ensureChannels(context)
        val workManager = WorkManager.getInstance(context)
        // ล้างงานแจ้งเตือน V3 เดิม เพื่อไม่ให้เด้งซ้ำเกิน 1-3 ครั้งต่อวัน
        workManager.cancelUniqueWork("daily_sell_reminder_dispatcher")
        workManager.cancelUniqueWork("daily_engage_reminder_dispatcher")

        val request = PeriodicWorkRequestBuilder<ReminderDispatchWorker>(1, TimeUnit.DAYS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_DAILY_PLANNER,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelAll(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(WORK_DAILY_PLANNER)
        workManager.cancelAllWorkByTag(ReminderDispatchWorker.TAG_DAILY_REMINDER)
    }
}
