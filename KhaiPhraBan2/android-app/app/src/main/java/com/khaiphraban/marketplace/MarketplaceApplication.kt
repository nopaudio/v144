package com.khaiphraban.marketplace

import android.app.Application
import com.khaiphraban.marketplace.notifications.ChatNotificationScheduler
import com.khaiphraban.marketplace.notifications.NotificationHelper
import com.khaiphraban.marketplace.notifications.ReminderScheduler
import com.khaiphraban.marketplace.notifications.PushTokenManager

class MarketplaceApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // V12 hotfix: notification/background setup is useful but must never
        // prevent the main Activity from opening if a device rejects one setup step.
        runCatching { NotificationHelper.ensureChannels(this) }
        // V13: member reminder timing is controlled centrally by Admin/Cron Push.
        // Cancel V12 local random reminders so members do not receive duplicates.
        runCatching { ReminderScheduler.cancelAll(this) }
        runCatching { ChatNotificationScheduler.cancel(this) }
        runCatching { PushTokenManager.sync(this) }
    }
}
