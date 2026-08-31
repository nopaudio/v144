package com.khaiphraban.marketplace.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.khaiphraban.marketplace.data.model.AuthData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "member_session")

data class MemberSession(
    val token: String? = null,
    val username: String? = null,
    val userId: Int = 0,
    val role: String = "member"
) {
    val isAdmin: Boolean get() = role == "admin"
    val isLoggedIn: Boolean get() = !token.isNullOrBlank()
}

class SessionManager(private val context: Context) {
    private object Keys {
        val token = stringPreferencesKey("token")
        val username = stringPreferencesKey("username")
        val userId = intPreferencesKey("user_id")
        val role = stringPreferencesKey("role")
        val dismissedAnnouncementId = intPreferencesKey("dismissed_announcement_id")
    }

    val session: Flow<MemberSession> = context.sessionDataStore.data.map { prefs ->
        MemberSession(
            token = prefs[Keys.token],
            username = prefs[Keys.username],
            userId = prefs[Keys.userId] ?: 0,
            role = prefs[Keys.role] ?: "member"
        )
    }

    val dismissedAnnouncementId: Flow<Int?> = context.sessionDataStore.data.map { prefs ->
        prefs[Keys.dismissedAnnouncementId]
    }

    suspend fun dismissAnnouncement(id: Int) {
        context.sessionDataStore.edit { it[Keys.dismissedAnnouncementId] = id }
    }

    suspend fun save(auth: AuthData) {
        context.sessionDataStore.edit {
            it[Keys.token] = auth.token
            it[Keys.username] = auth.user.username
            it[Keys.userId] = auth.user.id
            it[Keys.role] = auth.user.role
        }
    }

    suspend fun updateRole(role: String) {
        context.sessionDataStore.edit { it[Keys.role] = role.ifBlank { "member" } }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
