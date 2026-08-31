package com.khaiphraban.marketplace.notifications

import android.content.Context

class ChatNotificationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "chat_notification_cursor",
        Context.MODE_PRIVATE
    )

    fun isInitialized(userId: Int): Boolean =
        prefs.getBoolean(initKey(userId), false)

    fun get(userId: Int): Long =
        prefs.getLong(idKey(userId), 0L)

    fun initialize(userId: Int, messageId: Long) {
        if (userId <= 0 || messageId < 0L) return
        synchronized(lock) {
            prefs.edit()
                .putLong(idKey(userId), messageId)
                .putBoolean(initKey(userId), true)
                .apply()
        }
    }


    fun claim(userId: Int, messageId: Long): Boolean {
        if (userId <= 0 || messageId <= 0L) return false
        synchronized(lock) {
            val current = prefs.getLong(idKey(userId), 0L)
            if (messageId <= current) return false
            prefs.edit()
                .putLong(idKey(userId), messageId)
                .putBoolean(initKey(userId), true)
                .commit()
            return true
        }
    }

    fun set(userId: Int, messageId: Long) {
        if (userId <= 0 || messageId < 0L) return
        synchronized(lock) {
            val current = prefs.getLong(idKey(userId), 0L)
            val editor = prefs.edit().putBoolean(initKey(userId), true)
            if (messageId > current) editor.putLong(idKey(userId), messageId)
            editor.apply()
        }
    }

    private fun idKey(userId: Int) = "last_message_id_$userId"
    private fun initKey(userId: Int) = "initialized_$userId"

    companion object {
        private val lock = Any()
    }
}
