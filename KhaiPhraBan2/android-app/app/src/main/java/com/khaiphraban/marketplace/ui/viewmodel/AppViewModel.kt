package com.khaiphraban.marketplace.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khaiphraban.marketplace.data.model.*
import com.khaiphraban.marketplace.data.network.ApiClient
import com.khaiphraban.marketplace.data.repository.MarketplaceRepository
import com.khaiphraban.marketplace.data.session.MemberSession
import com.khaiphraban.marketplace.data.session.SessionManager
import com.khaiphraban.marketplace.notifications.PushTokenManager
import com.khaiphraban.marketplace.util.UploadUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MarketplaceRepository(ApiClient.service)
    private val sessionManager = SessionManager(application.applicationContext)
    val session = sessionManager.session.stateIn(
        viewModelScope, SharingStarted.Eagerly, MemberSession()
    )

    val dismissedAnnouncementId = sessionManager.dismissedAnnouncementId.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    var homeState by mutableStateOf<UiState<HomeData>>(UiState.Loading)
        private set
    var announcements by mutableStateOf<List<Announcement>>(emptyList())
        private set
    var homeRefreshing by mutableStateOf(false)
        private set
    var homeRefreshError by mutableStateOf<String?>(null)
        private set
    var detailState by mutableStateOf<UiState<Listing>>(UiState.Idle)
        private set
    var captchaState by mutableStateOf<UiState<CaptchaData>>(UiState.Idle)
        private set
    var authState by mutableStateOf<UiState<AuthData>>(UiState.Idle)
        private set
    var postState by mutableStateOf<UiState<Listing>>(UiState.Idle)
        private set
    var myListingsState by mutableStateOf<UiState<List<Listing>>>(UiState.Idle)
        private set
    var chatThreadsState by mutableStateOf<UiState<List<ChatThread>>>(UiState.Idle)
        private set
    var chatMessagesState by mutableStateOf<UiState<List<ChatMessage>>>(UiState.Idle)
        private set
    var chatSending by mutableStateOf(false)
        private set
    var chatError by mutableStateOf<String?>(null)
        private set
    var favoriteBusy by mutableStateOf(false)
        private set
    var walletState by mutableStateOf<UiState<WalletSummary>>(UiState.Idle)
        private set
    var walletBusy by mutableStateOf(false)
        private set
    var walletMessage by mutableStateOf<String?>(null)
        private set
    var lotteryState by mutableStateOf<UiState<LotteryOverview>>(UiState.Idle)
        private set
    var lotteryBusy by mutableStateOf(false)
        private set
    var lotteryMessage by mutableStateOf<String?>(null)
        private set
    var reportBusy by mutableStateOf(false)
        private set
    var reportMessage by mutableStateOf<String?>(null)
        private set
    var myOrdersState by mutableStateOf<UiState<List<Order>>>(UiState.Idle)
        private set
    var receivedOrdersState by mutableStateOf<UiState<List<Order>>>(UiState.Idle)
        private set
    var orderDetailState by mutableStateOf<UiState<Order>>(UiState.Idle)
        private set
    var orderBusy by mutableStateOf(false)
        private set
    var orderMessage by mutableStateOf<String?>(null)
        private set
    var orderSlipState by mutableStateOf<UiState<ByteArray>>(UiState.Idle)
        private set

    var myProfileState by mutableStateOf<UiState<MyProfile>>(UiState.Idle)
        private set
    var memberProfileState by mutableStateOf<UiState<MemberProfile>>(UiState.Idle)
        private set
    var verificationBusy by mutableStateOf(false)
        private set
    var verificationMessage by mutableStateOf<String?>(null)
        private set
    var displayNameBusy by mutableStateOf(false)
        private set
    var displayNameMessage by mutableStateOf<String?>(null)
        private set
    var chatUnreadCount by mutableStateOf(0)
        private set
    var ratingBusy by mutableStateOf(false)
        private set
    var ratingMessage by mutableStateOf<String?>(null)
        private set

    // V10 Native Admin state. Visibility may use the cached server role, but every
    // operation below is still authorized again by the backend Bearer token.
    var adminDashboardState by mutableStateOf<UiState<AdminDashboard>>(UiState.Idle)
        private set
    var adminNotificationsState by mutableStateOf<UiState<List<AdminNotification>>>(UiState.Idle)
        private set
    var adminTopupsState by mutableStateOf<UiState<List<AdminTopupItem>>>(UiState.Idle)
        private set
    var adminVerificationsState by mutableStateOf<UiState<List<AdminVerificationItem>>>(UiState.Idle)
        private set
    var adminListingsState by mutableStateOf<UiState<List<AdminListingItem>>>(UiState.Idle)
        private set
    var adminReportsState by mutableStateOf<UiState<List<AdminReportItem>>>(UiState.Idle)
        private set
    var adminOrdersState by mutableStateOf<UiState<List<AdminOrderItem>>>(UiState.Idle)
        private set
    var adminUsersState by mutableStateOf<UiState<List<AdminUserItem>>>(UiState.Idle)
        private set
    var adminEvidenceState by mutableStateOf<UiState<ByteArray>>(UiState.Idle)
        private set
    var adminBusy by mutableStateOf(false)
        private set
    var adminMessage by mutableStateOf<String?>(null)
        private set

    // Keep the same idempotency key when the network fails and the user retries.
    // This covers the important "server committed but the response was lost" case
    // so Premium/Boost/Order cannot be charged/created twice on a retry.
    private val premiumRequestKeys = mutableMapOf<String, String>()
    private val boostRequestKeys = mutableMapOf<Int, String>()
    private val lotteryRequestKeys = mutableMapOf<String, String>()
    private val orderRequestKeys = mutableMapOf<Int, String>()

    init {
        loadHome()
        loadAnnouncements()
    }

    fun loadHome() = viewModelScope.launch {
        homeState = UiState.Loading
        homeRefreshError = null
        homeState = repository.home().fold(
            { UiState.Success(it) },
            { UiState.Error(it.message ?: "โหลดข้อมูลไม่ได้") }
        )
    }

    fun loadAnnouncements() = viewModelScope.launch {
        repository.announcements().onSuccess { announcements = it }
    }

    fun refreshHome() = viewModelScope.launch {
        if (homeRefreshing) return@launch
        homeRefreshing = true
        homeRefreshError = null

        val previous = homeState
        repository.home().fold(
            { homeState = UiState.Success(it) },
            {
                if (previous !is UiState.Success) {
                    homeState = UiState.Error(it.message ?: "โหลดข้อมูลไม่ได้")
                } else {
                    homeRefreshError = it.message ?: "รีเฟรชหน้าแรกไม่สำเร็จ"
                }
            }
        )
        repository.announcements()
            .onSuccess { announcements = it }
            .onFailure {
                if (homeRefreshError == null) {
                    homeRefreshError = it.message ?: "รีเฟรชข่าวสารไม่สำเร็จ"
                }
            }
        homeRefreshing = false
    }

    fun clearHomeRefreshError() { homeRefreshError = null }

    fun dismissAnnouncement(id: Int) = viewModelScope.launch {
        sessionManager.dismissAnnouncement(id)
    }

    fun loadListing(id: Int) = viewModelScope.launch {
        detailState = UiState.Loading
        detailState = repository.listing(id, session.value.token).fold({ UiState.Success(it) }, { UiState.Error(it.message ?: "ไม่พบประกาศ") })
    }

    fun refreshCaptcha() = viewModelScope.launch {
        captchaState = UiState.Loading
        captchaState = repository.captcha().fold({ UiState.Success(it) }, { UiState.Error(it.message ?: "โหลดคำถามไม่ได้") })
    }

    fun login(username: String, password: String, onSuccess: () -> Unit) = viewModelScope.launch {
        authState = UiState.Loading
        repository.login(username.trim(), password).fold({ auth ->
            sessionManager.save(auth)
            PushTokenManager.sync(getApplication<Application>())
            authState = UiState.Success(auth)
            onSuccess()
        }, { authState = UiState.Error(it.message ?: "เข้าสู่ระบบไม่สำเร็จ") })
    }

    fun register(
        username: String, email: String, password: String, phone: String, lineId: String,
        province: String, amphoe: String, tambon: String,
        onSuccess: () -> Unit
    ) = viewModelScope.launch {
        authState = UiState.Loading
        if (province.isBlank() || amphoe.isBlank() || tambon.isBlank()) {
            authState = UiState.Error("กรุณาเลือกจังหวัด อำเภอ และตำบล")
            return@launch
        }
        repository.register(
            username.trim(), email.trim(), password, phone.trim(), lineId.trim(),
            province.trim(), amphoe.trim(), tambon.trim()
        ).fold({ auth ->
            sessionManager.save(auth)
            PushTokenManager.sync(getApplication<Application>())
            authState = UiState.Success(auth)
            onSuccess()
        }, { authState = UiState.Error(it.message ?: "สมัครสมาชิกไม่สำเร็จ") })
    }

    fun logout() = viewModelScope.launch {
        val authToken = session.value.token
        PushTokenManager.unregisterCurrentDevice(getApplication<Application>(), authToken)
        sessionManager.clear()
        myListingsState = UiState.Idle
        chatThreadsState = UiState.Idle
        chatMessagesState = UiState.Idle
        walletState = UiState.Idle
        walletMessage = null
        lotteryState = UiState.Idle
        lotteryMessage = null
        lotteryBusy = false
        myOrdersState = UiState.Idle
        receivedOrdersState = UiState.Idle
        orderDetailState = UiState.Idle
        orderSlipState = UiState.Idle
        orderMessage = null
        myProfileState = UiState.Idle
        memberProfileState = UiState.Idle
        verificationMessage = null
        displayNameMessage = null
        displayNameBusy = false
        chatUnreadCount = 0
        ratingMessage = null
        adminDashboardState = UiState.Idle
        adminNotificationsState = UiState.Idle
        adminTopupsState = UiState.Idle
        adminVerificationsState = UiState.Idle
        adminListingsState = UiState.Idle
        adminReportsState = UiState.Idle
        adminOrdersState = UiState.Idle
        adminUsersState = UiState.Idle
        adminEvidenceState = UiState.Idle
        adminMessage = null
        premiumRequestKeys.clear()
        boostRequestKeys.clear()
        lotteryRequestKeys.clear()
        orderRequestKeys.clear()
    }

    fun createListing(
        title: String,
        description: String,
        price: String,
        province: String,
        amphoe: String,
        tambon: String,
        allowMeetup: Boolean,
        allowBuyNow: Boolean,
        allowCod: Boolean,
        chatFirst: Boolean,
        captchaAnswer: String,
        imageUris: List<Uri>,
        onSuccess: () -> Unit
    ) = viewModelScope.launch {
        val token = session.value.token ?: run {
            postState = UiState.Error("กรุณาเข้าสู่ระบบก่อนลงประกาศ")
            return@launch
        }
        val captcha = (captchaState as? UiState.Success)?.data ?: run {
            postState = UiState.Error("กรุณาโหลดคำถามป้องกันสแปมใหม่")
            return@launch
        }
        if (title.trim().length !in 3..160) {
            postState = UiState.Error("หัวข้อต้องมี 3–160 ตัวอักษร")
            return@launch
        }
        if ((price.trim().toDoubleOrNull() ?: 0.0) <= 0.0) {
            postState = UiState.Error("กรุณากรอกราคาให้ถูกต้อง")
            return@launch
        }
        if (province.isBlank() || amphoe.isBlank() || tambon.isBlank()) {
            postState = UiState.Error("กรุณากรอกจังหวัด อำเภอ และตำบล")
            return@launch
        }
        if (!allowMeetup && !allowBuyNow && !chatFirst) {
            postState = UiState.Error("กรุณาเลือกอย่างน้อย 1 วิธีรับสินค้า/ติดต่อ")
            return@launch
        }
        if (captchaAnswer.isBlank()) {
            postState = UiState.Error("กรุณาตอบคำถามป้องกันสแปม")
            return@launch
        }
        if (imageUris.isEmpty()) {
            postState = UiState.Error("กรุณาแนบรูปสินค้าอย่างน้อย 1 รูป")
            return@launch
        }

        postState = UiState.Loading
        runCatching {
            imageUris.take(5).mapIndexed { index, uri ->
                UploadUtils.imagePart(getApplication<Application>(), uri, index)
            }
        }.fold({ parts ->
            repository.createListing(
                token,
                UploadUtils.text(title.trim()),
                UploadUtils.text(description.trim()),
                UploadUtils.text(price.trim()),
                UploadUtils.text(province.trim()),
                UploadUtils.text(amphoe.trim()),
                UploadUtils.text(tambon.trim()),
                UploadUtils.text(if (allowMeetup) "1" else "0"),
                UploadUtils.text(if (allowBuyNow || allowCod) "1" else "0"),
                UploadUtils.text(if (allowCod) "1" else "0"),
                UploadUtils.text(if (chatFirst) "1" else "0"),
                UploadUtils.text(captcha.token),
                UploadUtils.text(captchaAnswer.trim()),
                UploadUtils.text(""),
                parts
            ).fold({ listing ->
                postState = UiState.Success(listing)
                loadHome()
                loadMyListings()
                refreshCaptcha()
                onSuccess()
            }, {
                postState = UiState.Error(it.message ?: "ลงประกาศไม่สำเร็จ")
                refreshCaptcha()
            })
        }, {
            postState = UiState.Error(it.message ?: "เตรียมรูปภาพไม่สำเร็จ")
        })
    }

    fun loadMyListings(silent: Boolean = false) = viewModelScope.launch {
        val token = session.value.token ?: run {
            myListingsState = UiState.Error("กรุณาเข้าสู่ระบบ")
            return@launch
        }
        if (!silent || myListingsState !is UiState.Success) myListingsState = UiState.Loading
        myListingsState = repository.myListings(token).fold(
            { UiState.Success(it) }, { UiState.Error(it.message ?: "โหลดประกาศไม่ได้") }
        )
    }


    fun loadMyProfile(silent: Boolean = false) = viewModelScope.launch {
        val token = session.value.token ?: run {
            myProfileState = UiState.Error("กรุณาเข้าสู่ระบบ")
            return@launch
        }
        if (!silent || myProfileState !is UiState.Success) myProfileState = UiState.Loading
        myProfileState = repository.myProfile(token).fold(
            {
                // Refresh the locally cached role from a server-authenticated payload.
                // The cache only controls UI visibility; Admin APIs still check role server-side.
                sessionManager.updateRole(it.role)
                UiState.Success(it)
            },
            { UiState.Error(it.message ?: "โหลดโปรไฟล์ไม่ได้") }
        )
    }

    fun updateDisplayName(
        displayName: String,
        reason: String = "",
        onSuccess: () -> Unit = {}
    ) = viewModelScope.launch {
        val token = session.value.token ?: run {
            displayNameMessage = "กรุณาเข้าสู่ระบบ"
            return@launch
        }
        if (displayNameBusy) return@launch
        val cleaned = displayName.trim().replace(Regex("\\s+"), " ")
        if (cleaned.length !in 2..40) {
            displayNameMessage = "ชื่อที่แสดงต้องมี 2–40 ตัวอักษร"
            return@launch
        }
        if (!cleaned.matches(Regex("^[\\p{L}\\p{M}\\p{N} ._\\-]+$"))) {
            displayNameMessage = "ชื่อที่แสดงใช้ได้เฉพาะภาษาไทย/ตัวอักษร ตัวเลข เว้นวรรค จุด ขีด และ _"
            return@launch
        }
        val profile = (myProfileState as? UiState.Success)?.data
        if (profile != null && !profile.canChangeDisplayNameDirectly && reason.trim().length < 5) {
            displayNameMessage = "กรุณาระบุเหตุผลอย่างน้อย 5 ตัวอักษรเพื่อส่งให้แอดมินอนุมัติ"
            return@launch
        }

        displayNameBusy = true
        displayNameMessage = null
        repository.updateDisplayName(token, cleaned, reason.trim())
            .onSuccess { result ->
                displayNameMessage = if (result.requiresAdmin) {
                    "ส่งคำขอเปลี่ยนชื่อให้แอดมินแล้ว"
                } else {
                    "เปลี่ยนชื่อที่แสดงแล้ว"
                }
                loadMyProfile(silent = true)
                loadMyListings(silent = true)
                onSuccess()
            }
            .onFailure { displayNameMessage = it.message ?: "เปลี่ยนชื่อไม่สำเร็จ" }
        displayNameBusy = false
    }

    fun clearDisplayNameMessage() {
        displayNameMessage = null
    }

    fun loadMemberProfile(userId: Int) = viewModelScope.launch {
        memberProfileState = UiState.Loading
        memberProfileState = repository.memberProfile(userId).fold(
            { UiState.Success(it) }, { UiState.Error(it.message ?: "โหลดโปรไฟล์สมาชิกไม่ได้") }
        )
    }

    fun submitVerification(
        bankName: String,
        accountName: String,
        accountNumber: String,
        documentUri: Uri,
        onSuccess: () -> Unit = {}
    ) = viewModelScope.launch {
        val token = session.value.token ?: run {
            verificationMessage = "กรุณาเข้าสู่ระบบ"
            return@launch
        }
        if (verificationBusy) return@launch
        if (bankName.trim().length < 2 || accountName.trim().length < 2 || accountNumber.trim().length < 5) {
            verificationMessage = "กรุณากรอกธนาคาร ชื่อบัญชี และเลขบัญชีให้ครบ"
            return@launch
        }
        verificationBusy = true
        verificationMessage = null
        runCatching {
            UploadUtils.identityImagePart(getApplication<Application>(), documentUri)
        }.fold(
            onSuccess = { document ->
                repository.submitVerification(
                    token,
                    UploadUtils.text(bankName.trim()),
                    UploadUtils.text(accountName.trim()),
                    UploadUtils.text(accountNumber.trim()),
                    document
                ).onSuccess {
                    verificationMessage = "ส่งข้อมูลแล้ว รอแอดมินตรวจสอบ"
                    loadMyProfile(silent = true)
                    onSuccess()
                }.onFailure {
                    verificationMessage = it.message ?: "ส่งข้อมูลยืนยันไม่สำเร็จ"
                }
            },
            onFailure = { verificationMessage = it.message ?: "เตรียมรูปหลักฐานไม่สำเร็จ" }
        )
        verificationBusy = false
    }

    fun clearVerificationMessage() { verificationMessage = null }

    fun deleteListing(id: Int) = viewModelScope.launch {
        val token = session.value.token ?: return@launch
        repository.deleteListing(token, id).onSuccess {
            loadMyListings(); loadHome()
        }.onFailure {
            myListingsState = UiState.Error(it.message ?: "ลบประกาศไม่ได้")
        }
    }

    fun markSold(id: Int) = viewModelScope.launch {
        val token = session.value.token ?: return@launch
        repository.markSold(token, id).onSuccess {
            loadMyListings(); loadHome()
        }.onFailure {
            myListingsState = UiState.Error(it.message ?: "แก้สถานะไม่ได้")
        }
    }


    fun loadChatUnreadCount() = viewModelScope.launch {
        val token = session.value.token ?: run {
            chatUnreadCount = 0
            return@launch
        }
        repository.chatUnreadCount(token).onSuccess { chatUnreadCount = it.unreadCount }
    }

    fun loadChatThreads(silent: Boolean = false) = viewModelScope.launch {
        val token = session.value.token ?: run {
            chatThreadsState = UiState.Error("กรุณาเข้าสู่ระบบ")
            return@launch
        }
        if (!silent || chatThreadsState !is UiState.Success) chatThreadsState = UiState.Loading
        repository.chatThreads(token).fold(
            {
                chatThreadsState = UiState.Success(it)
                chatUnreadCount = it.sumOf { thread -> thread.unreadCount }
                chatError = null
            },
            {
                if (!silent || chatThreadsState !is UiState.Success) {
                    chatThreadsState = UiState.Error(it.message ?: "โหลดแชทไม่ได้")
                } else {
                    chatError = it.message ?: "อัปเดตแชทไม่ได้"
                }
            }
        )
    }

    fun loadChatMessages(listingId: Int, buyerId: Int = 0, silent: Boolean = false) = viewModelScope.launch {
        val token = session.value.token ?: run {
            chatMessagesState = UiState.Error("กรุณาเข้าสู่ระบบ")
            return@launch
        }
        if (!silent || chatMessagesState !is UiState.Success) chatMessagesState = UiState.Loading
        repository.chatMessages(token, listingId, buyerId).fold(
            {
                chatMessagesState = UiState.Success(it)
                chatError = null
                loadChatUnreadCount()
                loadChatThreads(silent = true)
            },
            {
                if (!silent || chatMessagesState !is UiState.Success) {
                    chatMessagesState = UiState.Error(it.message ?: "โหลดข้อความไม่ได้")
                } else {
                    chatError = it.message ?: "อัปเดตข้อความไม่ได้"
                }
            }
        )
    }

    fun sendMessage(listingId: Int, buyerId: Int, message: String, onSent: () -> Unit = {}) = viewModelScope.launch {
        val token = session.value.token ?: run {
            chatError = "กรุณาเข้าสู่ระบบ"
            return@launch
        }
        val clean = message.trim()
        if (clean.isBlank()) return@launch
        if (clean.length > 1000) {
            chatError = "ข้อความยาวเกิน 1,000 ตัวอักษร"
            return@launch
        }
        if (chatSending) return@launch

        chatSending = true
        chatError = null
        repository.sendMessage(token, listingId, buyerId, clean)
            .onSuccess {
                onSent()
                loadChatMessages(listingId, buyerId, silent = true)
                loadChatThreads(silent = true)
            }
            .onFailure {
                chatError = it.message ?: "ส่งข้อความไม่สำเร็จ"
            }
        chatSending = false
    }

    fun sendChatImage(
        listingId: Int,
        buyerId: Int,
        imageUri: Uri,
        onSent: () -> Unit = {}
    ) = viewModelScope.launch {
        val token = session.value.token ?: run {
            chatError = "กรุณาเข้าสู่ระบบ"
            return@launch
        }
        if (chatSending) return@launch

        chatSending = true
        chatError = null

        runCatching {
            UploadUtils.chatImagePart(getApplication<Application>(), imageUri)
        }.fold(
            onSuccess = { imagePart ->
                repository.sendChatImage(
                    token = token,
                    listingId = UploadUtils.text(listingId.toString()),
                    buyerId = UploadUtils.text(buyerId.toString()),
                    image = imagePart
                ).onSuccess {
                    onSent()
                    loadChatMessages(listingId, buyerId, silent = true)
                    loadChatThreads(silent = true)
                }.onFailure {
                    chatError = it.message ?: "ส่งรูปไม่สำเร็จ"
                }
            },
            onFailure = {
                chatError = it.message ?: "เตรียมรูปไม่สำเร็จ"
            }
        )
        chatSending = false
    }

    fun toggleFavorite(listingId: Int) = viewModelScope.launch {
        val token = session.value.token ?: return@launch
        if (favoriteBusy) return@launch
        favoriteBusy = true

        repository.toggleFavorite(token, listingId)
            .onSuccess { result ->
                val current = (detailState as? UiState.Success)?.data
                if (current != null && current.id == listingId) {
                    detailState = UiState.Success(current.copy(isFavorite = result.isFavorite))
                }
            }
            .onFailure {
                homeRefreshError = it.message ?: "บันทึกรายการสนใจไม่สำเร็จ"
            }

        favoriteBusy = false
    }

    fun loadWallet(silent: Boolean = false) = viewModelScope.launch {
        val token = session.value.token ?: run {
            walletState = UiState.Error("กรุณาเข้าสู่ระบบ")
            return@launch
        }
        if (!silent || walletState !is UiState.Success) walletState = UiState.Loading
        walletState = repository.walletSummary(token).fold(
            { UiState.Success(it) },
            { UiState.Error(it.message ?: "โหลดข้อมูลแต้มไม่ได้") }
        )
    }

    fun loadLottery(silent: Boolean = false) = viewModelScope.launch {
        val token = session.value.token ?: run {
            lotteryState = UiState.Error("กรุณาเข้าสู่ระบบ")
            return@launch
        }
        if (!silent || lotteryState !is UiState.Success) lotteryState = UiState.Loading
        lotteryState = repository.lotteryOverview(token).fold(
            { UiState.Success(it) },
            { UiState.Error(it.message ?: "โหลดกิจกรรมลุ้นพระไม่ได้") }
        )
    }

    fun buyLotteryNumber(roundId: Int, number: Int) = viewModelScope.launch {
        val token = session.value.token ?: run {
            lotteryMessage = "กรุณาเข้าสู่ระบบ"
            return@launch
        }
        if (lotteryBusy) return@launch
        if (number !in 0..99) {
            lotteryMessage = "กรุณาเลือกเลข 00–99"
            return@launch
        }
        val numberText = number.toString().padStart(2, '0')
        val requestMapKey = "$roundId:$numberText"
        val requestKey = lotteryRequestKeys.getOrPut(requestMapKey) { UUID.randomUUID().toString() }

        lotteryBusy = true
        lotteryMessage = null
        repository.lotteryBuyNumber(token, roundId, numberText, requestKey)
            .onSuccess { result ->
                lotteryRequestKeys.remove(requestMapKey)
                lotteryMessage = "ซื้อเลข ${result.entry.number} สำเร็จ ใช้ ${result.entry.pointsSpent} แต้ม"
                (lotteryState as? UiState.Success)?.data?.let { current ->
                    lotteryState = UiState.Success(
                        current.copy(
                            balance = result.balance,
                            soldNumbers = (current.soldNumbers + number).distinct().sorted(),
                            myEntries = (current.myEntries + result.entry).distinctBy { it.id }.sortedBy { it.number }
                        )
                    )
                }
                (walletState as? UiState.Success)?.data?.let { wallet ->
                    walletState = UiState.Success(wallet.copy(balance = result.balance))
                }
                loadLottery(silent = true)
                loadWallet(silent = true)
            }
            .onFailure {
                lotteryMessage = it.message ?: "ซื้อเลขไม่สำเร็จ"
                // The server may have accepted the number before a lost response.
                // Keep the request key so retrying the same number remains idempotent.
                loadLottery(silent = true)
            }
        lotteryBusy = false
    }

    fun clearLotteryMessage() {
        lotteryMessage = null
    }

    fun requestTopup(
        amount: String,
        note: String,
        slipUri: Uri,
        onSuccess: () -> Unit = {}
    ) = viewModelScope.launch {
        val token = session.value.token ?: return@launch
        if (walletBusy) return@launch
        val numericAmount = amount.trim().replace(",", "").toDoubleOrNull()
        val payment = (walletState as? UiState.Success)?.data?.payment
        if (numericAmount == null || numericAmount <= 0) {
            walletMessage = "กรุณากรอกยอดเติมให้ถูกต้อง"
            return@launch
        }
        if (payment != null && numericAmount < payment.minAmount) {
            walletMessage = "ยอดเติมขั้นต่ำ ${payment.minAmount} บาท"
            return@launch
        }

        walletBusy = true
        walletMessage = null
        runCatching {
            UploadUtils.slipImagePart(getApplication<Application>(), slipUri)
        }.fold(
            onSuccess = { slip ->
                repository.requestTopup(
                    token = token,
                    amount = UploadUtils.text(numericAmount.toString()),
                    note = UploadUtils.text(note.trim()),
                    slip = slip
                ).onSuccess {
                    walletMessage = "ส่งสลิปแล้ว รอแอดมินอนุมัติ"
                    loadWallet(silent = true)
                    onSuccess()
                }.onFailure {
                    walletMessage = it.message ?: "ส่งสลิปไม่สำเร็จ"
                }
            },
            onFailure = {
                walletMessage = it.message ?: "เตรียมรูปสลิปไม่สำเร็จ"
            }
        )
        walletBusy = false
    }

    fun purchasePremium(listingId: Int, planId: Int) = viewModelScope.launch {
        val token = session.value.token ?: return@launch
        if (walletBusy) return@launch
        walletBusy = true
        walletMessage = null
        val requestMapKey = "$listingId:$planId"
        val requestKey = premiumRequestKeys.getOrPut(requestMapKey) { UUID.randomUUID().toString() }
        repository.purchasePremium(token, listingId, planId, requestKey)
            .onSuccess { result ->
                premiumRequestKeys.remove(requestMapKey)
                walletMessage = "เปิดโพสต์พรีเมียมสำเร็จ"

                // Update visible UI immediately; network refresh below verifies server state.
                (walletState as? UiState.Success)?.data?.let { wallet ->
                    walletState = UiState.Success(wallet.copy(balance = result.balance))
                }

                var promotedListing: Listing? = null
                (myListingsState as? UiState.Success)?.data?.let { current ->
                    val updated = current.map { item ->
                        if (item.id == listingId) item.copy(isPremium = true, premiumUntil = result.endsAt).also { promotedListing = it }
                        else item
                    }
                    myListingsState = UiState.Success(updated)
                }

                (detailState as? UiState.Success)?.data?.takeIf { it.id == listingId }?.let {
                    detailState = UiState.Success(it.copy(isPremium = true, premiumUntil = result.endsAt))
                    promotedListing = promotedListing ?: it.copy(isPremium = true, premiumUntil = result.endsAt)
                }

                val currentHome = (homeState as? UiState.Success)?.data
                val promoted = promotedListing
                if (currentHome != null && promoted != null) {
                    homeState = UiState.Success(
                        currentHome.copy(
                            premium = listOf(promoted) + currentHome.premium.filterNot { it.id == listingId },
                            latest = currentHome.latest.filterNot { it.id == listingId }
                        )
                    )
                }

                loadWallet(silent = true)
                loadMyListings(silent = true)
                refreshHome()
                loadListing(listingId)
            }
            .onFailure { walletMessage = it.message ?: "ซื้อพรีเมียมไม่สำเร็จ" }
        walletBusy = false
    }

    fun purchaseBoost(listingId: Int) = viewModelScope.launch {
        val token = session.value.token ?: return@launch
        if (walletBusy) return@launch
        walletBusy = true
        walletMessage = null
        val requestKey = boostRequestKeys.getOrPut(listingId) { UUID.randomUUID().toString() }
        repository.purchaseBoost(token, listingId, requestKey)
            .onSuccess { result ->
                boostRequestKeys.remove(listingId)
                walletMessage = "ดันโพสต์สำเร็จ"
                (walletState as? UiState.Success)?.data?.let { wallet ->
                    walletState = UiState.Success(wallet.copy(balance = result.balance))
                }
                (myListingsState as? UiState.Success)?.data?.let { current ->
                    myListingsState = UiState.Success(current.map {
                        if (it.id == listingId) it.copy(boostedAt = result.boostedAt) else it
                    })
                }
                (detailState as? UiState.Success)?.data?.takeIf { it.id == listingId }?.let {
                    detailState = UiState.Success(it.copy(boostedAt = result.boostedAt))
                }
                loadWallet(silent = true)
                loadMyListings(silent = true)
                refreshHome()
                loadListing(listingId)
            }
            .onFailure { walletMessage = it.message ?: "ดันโพสต์ไม่สำเร็จ" }
        walletBusy = false
    }

    fun loadMyOrders(silent: Boolean = false) = viewModelScope.launch {
        val token = session.value.token ?: run {
            myOrdersState = UiState.Error("กรุณาเข้าสู่ระบบ")
            return@launch
        }
        if (!silent || myOrdersState !is UiState.Success) myOrdersState = UiState.Loading
        myOrdersState = repository.myOrders(token).fold(
            { UiState.Success(it) }, { UiState.Error(it.message ?: "โหลดคำสั่งซื้อไม่ได้") }
        )
    }

    fun loadReceivedOrders(silent: Boolean = false) = viewModelScope.launch {
        val token = session.value.token ?: run {
            receivedOrdersState = UiState.Error("กรุณาเข้าสู่ระบบ")
            return@launch
        }
        if (!silent || receivedOrdersState !is UiState.Success) receivedOrdersState = UiState.Loading
        receivedOrdersState = repository.receivedOrders(token).fold(
            { UiState.Success(it) }, { UiState.Error(it.message ?: "โหลดคำสั่งซื้อที่ได้รับไม่ได้") }
        )
    }

    fun loadOrder(orderId: Int, silent: Boolean = false) = viewModelScope.launch {
        val token = session.value.token ?: run {
            orderDetailState = UiState.Error("กรุณาเข้าสู่ระบบ")
            return@launch
        }
        if (!silent || orderDetailState !is UiState.Success) orderDetailState = UiState.Loading
        orderDetailState = repository.orderDetail(token, orderId).fold(
            { UiState.Success(it) }, { UiState.Error(it.message ?: "โหลดคำสั่งซื้อไม่ได้") }
        )
    }

    fun createOrder(
        listingId: Int,
        recipientName: String,
        phone: String,
        houseNoMoo: String,
        soi: String,
        road: String,
        subdistrict: String,
        district: String,
        province: String,
        postalCode: String,
        paymentMethod: String,
        slipUri: Uri?,
        onSuccess: (Int) -> Unit
    ) = viewModelScope.launch {
        val token = session.value.token ?: run {
            orderMessage = "กรุณาเข้าสู่ระบบ"
            return@launch
        }
        if (orderBusy) return@launch
        if (recipientName.trim().length < 2 || phone.trim().length < 6 ||
            houseNoMoo.isBlank() || subdistrict.isBlank() || district.isBlank() || province.isBlank() ||
            !postalCode.trim().matches(Regex("\\d{5}"))
        ) {
            orderMessage = "กรุณากรอกชื่อ เบอร์โทร ที่อยู่ และรหัสไปรษณีย์ให้ครบ"
            return@launch
        }
        if (paymentMethod !in setOf("bank_transfer", "cod")) {
            orderMessage = "กรุณาเลือกวิธีชำระเงิน"
            return@launch
        }
        if (paymentMethod == "bank_transfer" && slipUri == null) {
            orderMessage = "กรุณาแนบสลิปก่อนยืนยันคำสั่งซื้อ"
            return@launch
        }

        orderBusy = true
        orderMessage = null
        val slipPart = if (paymentMethod == "bank_transfer") {
            runCatching {
                UploadUtils.slipImagePart(getApplication<Application>(), requireNotNull(slipUri))
            }.getOrElse {
                orderMessage = it.message ?: "เตรียมสลิปไม่สำเร็จ"
                orderBusy = false
                return@launch
            }
        } else {
            null
        }

        repository.createOrder(
            token, listingId, recipientName.trim(), phone.trim(), houseNoMoo.trim(),
            soi.trim(), road.trim(), subdistrict.trim(), district.trim(), province.trim(),
            postalCode.trim(), paymentMethod,
            orderRequestKeys.getOrPut(listingId) { UUID.randomUUID().toString() },
            slipPart
        ).onSuccess { order ->
            orderRequestKeys.remove(listingId)
            orderDetailState = UiState.Success(order)
            orderMessage = "สร้างคำสั่งซื้อแล้ว"
            loadMyOrders(silent = true)
            refreshHome()
            loadListing(listingId)
            onSuccess(order.orderId)
        }.onFailure {
            orderMessage = it.message ?: "สร้างคำสั่งซื้อไม่สำเร็จ"
        }
        orderBusy = false
    }

    fun loadOrderSlip(orderId: Int) = viewModelScope.launch {
        val token = session.value.token ?: run {
            orderSlipState = UiState.Error("กรุณาเข้าสู่ระบบ")
            return@launch
        }
        orderSlipState = UiState.Loading
        orderSlipState = repository.orderSlip(token, orderId).fold(
            { UiState.Success(it) },
            { UiState.Error(it.message ?: "เปิดสลิปไม่ได้") }
        )
    }

    fun clearOrderSlip() {
        orderSlipState = UiState.Idle
    }

    fun updateOrder(orderId: Int, action: String, trackingNumber: String = "") = viewModelScope.launch {
        val token = session.value.token ?: return@launch
        if (orderBusy) return@launch
        orderBusy = true
        orderMessage = null
        repository.orderAction(token, orderId, action, trackingNumber.trim())
            .onSuccess { order ->
                orderDetailState = UiState.Success(order)
                orderMessage = when (action) {
                    "confirm" -> "ยืนยันคำสั่งซื้อแล้ว"
                    "reject", "cancel" -> "ยกเลิกคำสั่งซื้อแล้ว"
                    "ship" -> "บันทึกการจัดส่งแล้ว"
                    "received" -> "ยืนยันรับสินค้าแล้ว"
                    else -> "อัปเดตคำสั่งซื้อแล้ว"
                }
                (myOrdersState as? UiState.Success)?.data?.let { list ->
                    myOrdersState = UiState.Success(list.map { if (it.orderId == orderId) order else it })
                }
                (receivedOrdersState as? UiState.Success)?.data?.let { list ->
                    receivedOrdersState = UiState.Success(list.map { if (it.orderId == orderId) order else it })
                }
                refreshHome()
                loadMyListings(silent = true)
            }
            .onFailure { orderMessage = it.message ?: "อัปเดตคำสั่งซื้อไม่สำเร็จ" }
        orderBusy = false
    }

    fun submitRating(orderId: Int, rating: Int, reviewText: String = "") = viewModelScope.launch {
        val token = session.value.token ?: run {
            ratingMessage = "กรุณาเข้าสู่ระบบ"
            return@launch
        }
        if (ratingBusy) return@launch
        if (rating !in 1..5) {
            ratingMessage = "กรุณาเลือกคะแนน 1–5 ดาว"
            return@launch
        }
        ratingBusy = true
        ratingMessage = null
        repository.submitRating(token, orderId, rating, reviewText.trim())
            .onSuccess { order ->
                orderDetailState = UiState.Success(order)
                ratingMessage = "บันทึกคะแนนแล้ว"
                loadMyOrders(silent = true)
                loadReceivedOrders(silent = true)
            }
            .onFailure { ratingMessage = it.message ?: "บันทึกคะแนนไม่สำเร็จ" }
        ratingBusy = false
    }

    fun clearRatingMessage() { ratingMessage = null }

    fun clearOrderMessage() { orderMessage = null }


    private fun adminTokenOrError(): String? {
        val token = session.value.token
        if (token.isNullOrBlank()) {
            adminMessage = "กรุณาเข้าสู่ระบบ"
            return null
        }
        return token
    }

    fun loadAdminDashboard(silent: Boolean = false) = viewModelScope.launch {
        val token = adminTokenOrError() ?: return@launch
        if (!silent || adminDashboardState !is UiState.Success) adminDashboardState = UiState.Loading
        repository.adminDashboard(token).fold(
            { adminDashboardState = UiState.Success(it) },
            { adminDashboardState = UiState.Error(it.message ?: "โหลดข้อมูลผู้ดูแลไม่ได้") }
        )
    }

    fun loadAdminNotifications(unreadOnly: Boolean = false, silent: Boolean = false) = viewModelScope.launch {
        val token = adminTokenOrError() ?: return@launch
        if (!silent || adminNotificationsState !is UiState.Success) adminNotificationsState = UiState.Loading
        repository.adminNotifications(token, unreadOnly).fold(
            { adminNotificationsState = UiState.Success(it) },
            { adminNotificationsState = UiState.Error(it.message ?: "โหลดแจ้งเตือนไม่ได้") }
        )
    }

    fun markAdminNotificationRead(notificationId: Int, onDone: () -> Unit = {}) = viewModelScope.launch {
        val token = adminTokenOrError() ?: return@launch
        repository.adminNotificationRead(token, notificationId)
            .onSuccess {
                loadAdminNotifications(silent = true)
                loadAdminDashboard(silent = true)
                onDone()
            }
            .onFailure { adminMessage = it.message ?: "อัปเดตแจ้งเตือนไม่สำเร็จ" }
    }

    fun markAllAdminNotificationsRead() = viewModelScope.launch {
        val token = adminTokenOrError() ?: return@launch
        if (adminBusy) return@launch
        adminBusy = true
        repository.adminNotificationReadAll(token)
            .onSuccess {
                adminMessage = "อ่านแจ้งเตือนทั้งหมดแล้ว"
                loadAdminNotifications(silent = true)
                loadAdminDashboard(silent = true)
            }
            .onFailure { adminMessage = it.message ?: "อัปเดตแจ้งเตือนไม่สำเร็จ" }
        adminBusy = false
    }

    fun loadAdminTopups(status: String = "pending", silent: Boolean = false) = viewModelScope.launch {
        val token = adminTokenOrError() ?: return@launch
        if (!silent || adminTopupsState !is UiState.Success) adminTopupsState = UiState.Loading
        adminTopupsState = repository.adminTopups(token, status).fold(
            { UiState.Success(it) }, { UiState.Error(it.message ?: "โหลดรายการเติมแต้มไม่ได้") }
        )
    }

    fun reviewAdminTopup(id: Int, decision: String) = viewModelScope.launch {
        val token = adminTokenOrError() ?: return@launch
        if (adminBusy) return@launch
        adminBusy = true
        adminMessage = null
        repository.adminReviewTopup(token, id, decision)
            .onSuccess {
                adminMessage = if (decision == "approved") "อนุมัติเติมแต้มแล้ว" else "ปฏิเสธคำขอเติมแต้มแล้ว"
                loadAdminTopups(silent = true)
                refreshAdminAfterAction()
            }
            .onFailure { adminMessage = it.message ?: "ดำเนินการเติมแต้มไม่สำเร็จ" }
        adminBusy = false
    }

    fun loadAdminVerifications(status: String = "pending", silent: Boolean = false) = viewModelScope.launch {
        val token = adminTokenOrError() ?: return@launch
        if (!silent || adminVerificationsState !is UiState.Success) adminVerificationsState = UiState.Loading
        adminVerificationsState = repository.adminVerifications(token, status).fold(
            { UiState.Success(it) }, { UiState.Error(it.message ?: "โหลดการยืนยันตัวตนไม่ได้") }
        )
    }

    fun reviewAdminVerification(userId: Int, decision: String, reason: String = "") = viewModelScope.launch {
        val token = adminTokenOrError() ?: return@launch
        if (adminBusy) return@launch
        if (decision == "rejected" && reason.trim().length < 3) {
            adminMessage = "กรุณาระบุเหตุผลที่ปฏิเสธ"
            return@launch
        }
        adminBusy = true
        adminMessage = null
        repository.adminReviewVerification(token, userId, decision, reason.trim())
            .onSuccess {
                adminMessage = if (decision == "approved") "อนุมัติการยืนยันตัวตนแล้ว" else "ปฏิเสธการยืนยันตัวตนแล้ว"
                loadAdminVerifications(silent = true)
                refreshAdminAfterAction()
            }
            .onFailure { adminMessage = it.message ?: "ดำเนินการยืนยันตัวตนไม่สำเร็จ" }
        adminBusy = false
    }

    fun loadAdminListings(status: String = "pending", silent: Boolean = false) = viewModelScope.launch {
        val token = adminTokenOrError() ?: return@launch
        if (!silent || adminListingsState !is UiState.Success) adminListingsState = UiState.Loading
        adminListingsState = repository.adminListings(token, status).fold(
            { UiState.Success(it) }, { UiState.Error(it.message ?: "โหลดประกาศรอตรวจไม่ได้") }
        )
    }

    fun updateAdminListing(id: Int, status: String) = viewModelScope.launch {
        val token = adminTokenOrError() ?: return@launch
        if (adminBusy) return@launch
        adminBusy = true
        adminMessage = null
        repository.adminUpdateListing(token, id, status)
            .onSuccess {
                adminMessage = if (status == "approved") "อนุมัติประกาศแล้ว" else "อัปเดตสถานะประกาศแล้ว"
                loadAdminListings(silent = true)
                refreshAdminAfterAction()
                refreshHome()
            }
            .onFailure { adminMessage = it.message ?: "อัปเดตประกาศไม่สำเร็จ" }
        adminBusy = false
    }

    fun loadAdminReports(status: String = "open", silent: Boolean = false) = viewModelScope.launch {
        val token = adminTokenOrError() ?: return@launch
        if (!silent || adminReportsState !is UiState.Success) adminReportsState = UiState.Loading
        adminReportsState = repository.adminReports(token, status).fold(
            { UiState.Success(it) }, { UiState.Error(it.message ?: "โหลดรายงานไม่ได้") }
        )
    }

    fun updateAdminReport(id: Int, action: String, note: String = "") = viewModelScope.launch {
        val token = adminTokenOrError() ?: return@launch
        if (adminBusy) return@launch
        adminBusy = true
        adminMessage = null
        repository.adminUpdateReport(token, id, action, note.trim())
            .onSuccess {
                adminMessage = "อัปเดตรายงานแล้ว"
                loadAdminReports(silent = true)
                refreshAdminAfterAction()
            }
            .onFailure { adminMessage = it.message ?: "อัปเดตรายงานไม่สำเร็จ" }
        adminBusy = false
    }

    fun loadAdminOrders(status: String = "", silent: Boolean = false) = viewModelScope.launch {
        val token = adminTokenOrError() ?: return@launch
        if (!silent || adminOrdersState !is UiState.Success) adminOrdersState = UiState.Loading
        adminOrdersState = repository.adminOrders(token, status).fold(
            { UiState.Success(it) }, { UiState.Error(it.message ?: "โหลดคำสั่งซื้อไม่ได้") }
        )
    }

    fun loadAdminUsers(query: String = "", silent: Boolean = false) = viewModelScope.launch {
        val token = adminTokenOrError() ?: return@launch
        if (!silent || adminUsersState !is UiState.Success) adminUsersState = UiState.Loading
        adminUsersState = repository.adminUsers(token, query.trim()).fold(
            { UiState.Success(it) }, { UiState.Error(it.message ?: "โหลดสมาชิกไม่ได้") }
        )
    }

    fun updateAdminUser(
        id: Int,
        displayName: String,
        adminStars: Int,
        specialIcon: String,
        pointsDelta: Int,
        role: String,
        status: String
    ) = viewModelScope.launch {
        val token = adminTokenOrError() ?: return@launch
        if (adminBusy) return@launch
        if (adminStars !in 0..5) {
            adminMessage = "ดาวจากแอดมินต้องอยู่ระหว่าง 0–5"
            return@launch
        }
        adminBusy = true
        adminMessage = null
        repository.adminUpdateUser(
            token, id, displayName.trim(), adminStars, specialIcon.trim(),
            pointsDelta, role, status
        ).onSuccess {
            adminMessage = "บันทึกข้อมูลสมาชิกแล้ว"
            loadAdminUsers(silent = true)
            refreshAdminAfterAction()
            loadMyProfile(silent = true)
        }.onFailure {
            adminMessage = it.message ?: "บันทึกข้อมูลสมาชิกไม่สำเร็จ"
        }
        adminBusy = false
    }

    fun reviewAdminDisplayName(
        requestId: Int,
        decision: String,
        adminNote: String = ""
    ) = viewModelScope.launch {
        val token = adminTokenOrError() ?: return@launch
        if (adminBusy) return@launch
        adminBusy = true
        adminMessage = null
        repository.adminReviewDisplayName(token, requestId, decision, adminNote.trim())
            .onSuccess {
                adminMessage = if (decision == "approved") {
                    "อนุมัติชื่อใหม่แล้ว"
                } else {
                    "ปฏิเสธคำขอเปลี่ยนชื่อแล้ว"
                }
                loadAdminUsers(silent = true)
                refreshAdminAfterAction()
            }
            .onFailure { adminMessage = it.message ?: "ดำเนินการคำขอเปลี่ยนชื่อไม่สำเร็จ" }
        adminBusy = false
    }

    fun loadAdminEvidence(kind: String, id: Int? = null, userId: Int? = null) = viewModelScope.launch {
        val token = adminTokenOrError() ?: return@launch
        adminEvidenceState = UiState.Loading
        adminEvidenceState = repository.adminMedia(token, kind, id, userId).fold(
            { UiState.Success(it) }, { UiState.Error(it.message ?: "เปิดรูปหลักฐานไม่ได้") }
        )
    }

    fun clearAdminEvidence() {
        adminEvidenceState = UiState.Idle
    }

    fun clearAdminMessage() {
        adminMessage = null
    }

    private fun refreshAdminAfterAction() {
        loadAdminDashboard(silent = true)
        loadAdminNotifications(silent = true)
    }

    fun reportUser(
        listingId: Int,
        reportedUserId: Int,
        category: String,
        details: String,
        onSuccess: () -> Unit = {}
    ) = viewModelScope.launch {
        val token = session.value.token ?: run {
            reportMessage = "กรุณาเข้าสู่ระบบก่อนแจ้งปัญหา"
            return@launch
        }
        if (reportBusy) return@launch
        if (details.trim().length < 5) {
            reportMessage = "กรุณาระบุรายละเอียดอย่างน้อย 5 ตัวอักษร"
            return@launch
        }
        reportBusy = true
        reportMessage = null
        repository.reportUser(token, listingId, reportedUserId, category, details.trim())
            .onSuccess {
                reportMessage = "ส่งเรื่องให้แอดมินตรวจสอบแล้ว"
                onSuccess()
            }
            .onFailure { reportMessage = it.message ?: "ส่งรายงานไม่สำเร็จ" }
        reportBusy = false
    }

    fun clearReportMessage() { reportMessage = null }

    fun clearWalletMessage() { walletMessage = null }

    fun clearChatError() { chatError = null }

    fun clearAuthMessage() { authState = UiState.Idle }
    fun clearPostMessage() { postState = UiState.Idle }
}
