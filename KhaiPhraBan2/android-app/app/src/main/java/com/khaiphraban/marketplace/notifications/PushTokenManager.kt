package com.khaiphraban.marketplace.notifications

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.khaiphraban.marketplace.data.network.ApiClient
import com.khaiphraban.marketplace.data.repository.MarketplaceRepository
import com.khaiphraban.marketplace.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the Firebase device token linked to the currently logged-in member.
 * The server table uses the FCM token as a unique key, so logging in as another
 * account on the same phone safely moves the token to that account.
 */
object PushTokenManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sync(context: Context) {
        val appContext = context.applicationContext
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                registerToken(appContext, token)
            }
    }

    fun registerToken(context: Context, deviceToken: String) {
        if (deviceToken.isBlank()) return
        val appContext = context.applicationContext
        scope.launch {
            val session = SessionManager(appContext).session.first()
            val authToken = session.token ?: return@launch
            MarketplaceRepository(ApiClient.service)
                .registerPushToken(authToken, deviceToken)
        }
    }

    fun unregisterCurrentDevice(context: Context, authToken: String?) {
        if (authToken.isNullOrBlank()) return
        val appContext = context.applicationContext
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { deviceToken ->
                scope.launch {
                    MarketplaceRepository(ApiClient.service)
                        .unregisterPushToken(authToken, deviceToken)
                }
            }
    }
}
