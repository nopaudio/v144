package com.khaiphraban.marketplace.data.model

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    val success: Boolean = false,
    val message: String? = null,
    val data: T? = null
)

data class HomeData(
    val premium: List<Listing> = emptyList(),
    val latest: List<Listing> = emptyList(),
    val random: List<Listing> = emptyList(),
    val hero: HomeHero? = null,
    val banners: List<HomeBanner> = emptyList(),
    @SerializedName("online_count") val onlineCount: Int = 0
)

data class HomeBanner(
    val id: Int,
    @SerializedName("image_url") val imageUrl: String,
    @SerializedName("sort_order") val sortOrder: Int = 0
)

data class HomeHero(
    @SerializedName("brand_title") val brandTitle: String = "ตลาดพระออนไลน์",
    val headline: String = "ตลาดพระเครื่องสำหรับคนรักพระ",
    val subheadline: String = "ลงขายง่าย • ดูรูปชัด • ติดต่อผู้ขายโดยตรง",
    @SerializedName("trust_title") val trustTitle: String = "",
    @SerializedName("trust_text") val trustText: String = "ประกาศใหม่ผ่านการตรวจจากแอดมินก่อนเผยแพร่",
    val enabled: Boolean = true,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class AuthData(
    val token: String,
    val user: User
)

data class User(
    val id: Int,
    val username: String,
    val email: String,
    val phone: String? = null,
    @SerializedName("line_id") val lineId: String? = null,
    val role: String = "member",
    val status: String = "active"
)

data class Seller(
    val id: Int = 0,
    val username: String,
    val phone: String? = null,
    @SerializedName("line_id") val lineId: String? = null,
    @SerializedName("member_since") val memberSince: String? = null,
    val role: String = "member",
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("admin_stars") val adminStars: Int = 0,
    @SerializedName("special_icon") val specialIcon: String? = null,
    @SerializedName("is_verified") val isVerified: Boolean = false,
    @SerializedName("rating_average") val ratingAverage: Double = 0.0,
    @SerializedName("rating_count") val ratingCount: Int = 0
)

data class ListingImage(
    val id: Int,
    val url: String,
    @SerializedName("sort_order") val sortOrder: Int = 0
)

data class Listing(
    val id: Int,
    val title: String,
    val description: String? = null,
    val price: Double,
    val province: String,
    val amphoe: String,
    val tambon: String,
    val status: String,
    @SerializedName("created_at") val createdAt: String,
    val seller: Seller? = null,
    val images: List<ListingImage> = emptyList(),
    @SerializedName("share_url") val shareUrl: String? = null,
    @SerializedName("is_favorite") val isFavorite: Boolean = false,
    @SerializedName("is_premium") val isPremium: Boolean = false,
    @SerializedName("premium_until") val premiumUntil: String? = null,
    @SerializedName("boosted_at") val boostedAt: String? = null,
    @SerializedName("allow_meetup") val allowMeetup: Boolean = false,
    @SerializedName("allow_buy_now") val allowBuyNow: Boolean = true,
    @SerializedName("allow_cod") val allowCod: Boolean = false,
    @SerializedName("chat_first") val chatFirst: Boolean = true,
    @SerializedName("has_active_order") val hasActiveOrder: Boolean = false,
    @SerializedName("can_buy") val canBuy: Boolean = true,
    @SerializedName("seller_payment") val sellerPayment: VerifiedBankAccount? = null
)

data class CaptchaData(
    val token: String,
    val question: String,
    @SerializedName("expires_at") val expiresAt: String
)

data class ActionData(
    val id: Int? = null
)

data class Announcement(
    val id: Int,
    val title: String,
    val body: String,
    @SerializedName("created_at") val createdAt: String
)

data class ChatThread(
    @SerializedName("listing_id") val listingId: Int,
    @SerializedName("buyer_id") val buyerId: Int,
    val title: String,
    @SerializedName("other_username") val otherUsername: String,
    @SerializedName("other_role") val otherRole: String = "member",
    @SerializedName("last_message") val lastMessage: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("unread_count") val unreadCount: Int = 0
)

data class ChatMessage(
    val id: Int,
    @SerializedName("sender_id") val senderId: Int,
    val message: String = "",
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("read_at") val readAt: String? = null,
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("created_at") val createdAt: String
)


data class LatestChatId(
    @SerializedName("latest_id") val latestId: Long = 0
)

data class ChatUpdate(
    val id: Long,
    @SerializedName("listing_id") val listingId: Int,
    @SerializedName("buyer_id") val buyerId: Int,
    @SerializedName("sender_id") val senderId: Int,
    @SerializedName("sender_username") val senderUsername: String,
    val title: String,
    val message: String,
    @SerializedName("created_at") val createdAt: String
)


data class FavoriteState(
    @SerializedName("is_favorite") val isFavorite: Boolean = false
)



data class UnreadCount(
    @SerializedName("unread_count") val unreadCount: Int = 0
)

data class VerifiedBankAccount(
    @SerializedName("is_verified") val isVerified: Boolean = false,
    @SerializedName("bank_name") val bankName: String = "",
    @SerializedName("account_name") val accountName: String = "",
    @SerializedName("account_number") val accountNumber: String = "",
    @SerializedName("verified_at") val verifiedAt: String? = null
)

data class VerificationInfo(
    val status: String = "unverified",
    @SerializedName("status_label") val statusLabel: String = "ยังไม่ยืนยัน",
    @SerializedName("is_verified") val isVerified: Boolean = false,
    @SerializedName("bank_name") val bankName: String = "",
    @SerializedName("account_name") val accountName: String = "",
    @SerializedName("account_number") val accountNumber: String = "",
    @SerializedName("rejection_reason") val rejectionReason: String? = null,
    @SerializedName("submitted_at") val submittedAt: String? = null,
    @SerializedName("reviewed_at") val reviewedAt: String? = null,
    @SerializedName("verified_at") val verifiedAt: String? = null
)

data class DisplayNameChangeRequest(
    val id: Int,
    @SerializedName("requested_name") val requestedName: String,
    val reason: String,
    val status: String,
    @SerializedName("admin_note") val adminNote: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("reviewed_at") val reviewedAt: String? = null
)

data class DisplayNameUpdateResult(
    @SerializedName("display_name") val displayName: String,
    val status: String,
    @SerializedName("requires_admin") val requiresAdmin: Boolean = false,
    @SerializedName("requested_name") val requestedName: String? = null
)

data class MyProfile(
    val id: Int,
    val username: String,
    @SerializedName("display_name") val displayName: String = username,
    @SerializedName("display_name_change_count") val displayNameChangeCount: Int = 0,
    @SerializedName("can_change_display_name_directly") val canChangeDisplayNameDirectly: Boolean = true,
    @SerializedName("pending_display_name_request") val pendingDisplayNameRequest: DisplayNameChangeRequest? = null,
    @SerializedName("admin_stars") val adminStars: Int = 0,
    @SerializedName("special_icon") val specialIcon: String? = null,
    val email: String = "",
    val phone: String? = null,
    @SerializedName("line_id") val lineId: String? = null,
    val province: String? = null,
    val amphoe: String? = null,
    val tambon: String? = null,
    @SerializedName("member_since") val memberSince: String? = null,
    val role: String = "member",
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("is_verified") val isVerified: Boolean = false,
    @SerializedName("rating_average") val ratingAverage: Double = 0.0,
    @SerializedName("rating_count") val ratingCount: Int = 0,
    val verification: VerificationInfo = VerificationInfo()
)

data class MemberProfile(
    val id: Int,
    val username: String,
    @SerializedName("display_name") val displayName: String = username,
    @SerializedName("admin_stars") val adminStars: Int = 0,
    @SerializedName("special_icon") val specialIcon: String? = null,
    @SerializedName("member_since") val memberSince: String? = null,
    val role: String = "member",
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("is_verified") val isVerified: Boolean = false,
    @SerializedName("verification_label") val verificationLabel: String = "ยังไม่ยืนยันตัวตน",
    @SerializedName("rating_average") val ratingAverage: Double = 0.0,
    @SerializedName("rating_count") val ratingCount: Int = 0,
    val listings: List<Listing> = emptyList()
)

data class TopupPackage(
    val id: Int,
    val name: String,
    val points: Int,
    val price: Double
)

data class PremiumPlan(
    val id: Int,
    val name: String,
    @SerializedName("points_cost") val pointsCost: Int,
    @SerializedName("duration_days") val durationDays: Int
)

data class TopupRequest(
    val id: Int,
    @SerializedName("package_id") val packageId: Int? = null,
    val points: Int,
    val amount: Double,
    val note: String? = null,
    val status: String,
    @SerializedName("package_name") val packageName: String = "เติมแต้มตามจำนวน",
    @SerializedName("slip_url") val slipUrl: String? = null,
    @SerializedName("created_at") val createdAt: String
)

data class PremiumPromotion(
    val id: Int,
    @SerializedName("listing_id") val listingId: Int,
    val title: String,
    @SerializedName("plan_name") val planName: String,
    @SerializedName("points_spent") val pointsSpent: Int,
    @SerializedName("starts_at") val startsAt: String,
    @SerializedName("ends_at") val endsAt: String,
    val status: String
)

data class PointTransaction(
    val id: Int,
    val amount: Int,
    val type: String,
    val description: String,
    @SerializedName("listing_id") val listingId: Int? = null,
    @SerializedName("created_at") val createdAt: String
)

data class PaymentSettings(
    @SerializedName("bank_name") val bankName: String = "",
    @SerializedName("account_name") val accountName: String = "",
    @SerializedName("account_number") val accountNumber: String = "",
    @SerializedName("points_per_baht") val pointsPerBaht: Double = 1.0,
    @SerializedName("min_amount") val minAmount: Double = 20.0,
    @SerializedName("is_active") val isActive: Boolean = true
)

data class HeartbeatResult(
    @SerializedName("online_count") val onlineCount: Int = 0
)

data class BoostSettings(
    @SerializedName("points_cost") val pointsCost: Int = 20,
    @SerializedName("cooldown_minutes") val cooldownMinutes: Int = 10,
    @SerializedName("is_active") val isActive: Boolean = true
)

data class ListingBoost(
    val id: Int,
    @SerializedName("listing_id") val listingId: Int,
    val title: String,
    @SerializedName("points_spent") val pointsSpent: Int,
    @SerializedName("boosted_at") val boostedAt: String,
    @SerializedName("created_at") val createdAt: String
)

data class WalletSummary(
    val balance: Int = 0,
    val payment: PaymentSettings = PaymentSettings(),
    val boost: BoostSettings = BoostSettings(),
    val packages: List<TopupPackage> = emptyList(),
    val plans: List<PremiumPlan> = emptyList(),
    @SerializedName("topup_requests") val topupRequests: List<TopupRequest> = emptyList(),
    val promotions: List<PremiumPromotion> = emptyList(),
    val boosts: List<ListingBoost> = emptyList(),
    val transactions: List<PointTransaction> = emptyList()
)

data class LotteryWinner(
    @SerializedName("entry_id") val entryId: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("display_name") val displayName: String
)

data class LotteryRound(
    val id: Int,
    val title: String,
    @SerializedName("prize_name") val prizeName: String,
    @SerializedName("prize_description") val prizeDescription: String = "",
    @SerializedName("prize_image_url") val prizeImageUrl: String? = null,
    @SerializedName("draw_date") val drawDate: String,
    @SerializedName("points_cost") val pointsCost: Int,
    val status: String,
    @SerializedName("winning_number") val winningNumber: String? = null,
    @SerializedName("announced_at") val announcedAt: String? = null,
    val winner: LotteryWinner? = null
)

data class LotteryEntry(
    val id: Int,
    val number: String,
    @SerializedName("points_spent") val pointsSpent: Int,
    @SerializedName("created_at") val createdAt: String
)

data class LotteryOverview(
    val balance: Int = 0,
    val round: LotteryRound? = null,
    @SerializedName("sold_numbers") val soldNumbers: List<Int> = emptyList(),
    @SerializedName("my_entries") val myEntries: List<LotteryEntry> = emptyList(),
    @SerializedName("recent_results") val recentResults: List<LotteryRound> = emptyList()
)

data class LotteryPurchaseResult(
    val balance: Int,
    val entry: LotteryEntry
)

data class PurchasePremiumResult(
    val id: Int,
    val balance: Int,
    @SerializedName("starts_at") val startsAt: String? = null,
    @SerializedName("ends_at") val endsAt: String? = null
)

data class PurchaseBoostResult(
    val id: Int,
    val balance: Int,
    @SerializedName("boosted_at") val boostedAt: String
)

data class Order(
    @SerializedName("order_id") val orderId: Int,
    @SerializedName("listing_id") val listingId: Int,
    @SerializedName("buyer_id") val buyerId: Int,
    @SerializedName("seller_id") val sellerId: Int,
    @SerializedName("viewer_role") val viewerRole: String? = null,
    @SerializedName("buyer_username") val buyerUsername: String = "",
    @SerializedName("seller_username") val sellerUsername: String = "",
    val price: Double,
    val title: String,
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("seller_verified") val sellerVerified: Boolean = false,
    @SerializedName("seller_bank_name") val sellerBankName: String? = null,
    @SerializedName("seller_account_name") val sellerAccountName: String? = null,
    @SerializedName("seller_account_number") val sellerAccountNumber: String? = null,
    @SerializedName("seller_verified_at") val sellerVerifiedAt: String? = null,
    @SerializedName("payment_method") val paymentMethod: String? = null,
    @SerializedName("payment_method_label") val paymentMethodLabel: String = "การชำระเงินแบบเดิม",
    @SerializedName("has_payment_slip") val hasPaymentSlip: Boolean = false,
    @SerializedName("seller_rating_average") val sellerRatingAverage: Double = 0.0,
    @SerializedName("seller_rating_count") val sellerRatingCount: Int = 0,
    @SerializedName("can_rate") val canRate: Boolean = false,
    @SerializedName("review_rating") val reviewRating: Int? = null,
    @SerializedName("review_text") val reviewText: String? = null,
    @SerializedName("reviewed_at") val reviewedAt: String? = null,
    @SerializedName("recipient_name") val recipientName: String,
    val phone: String,
    @SerializedName("house_no_moo") val houseNoMoo: String,
    val soi: String? = null,
    val road: String? = null,
    val subdistrict: String,
    val district: String,
    val province: String,
    @SerializedName("postal_code") val postalCode: String,
    val note: String? = null,
    val status: String,
    @SerializedName("status_label") val statusLabel: String,
    @SerializedName("tracking_number") val trackingNumber: String? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("confirmed_at") val confirmedAt: String? = null,
    @SerializedName("shipped_at") val shippedAt: String? = null,
    @SerializedName("completed_at") val completedAt: String? = null
)


// V10 Native Admin models. These payloads are returned only after the server
// validates the Bearer token against users.role='admin'.
data class AdminDashboard(
    val users: Int = 0,
    @SerializedName("pending_listings") val pendingListings: Int = 0,
    @SerializedName("pending_topups") val pendingTopups: Int = 0,
    @SerializedName("pending_verifications") val pendingVerifications: Int = 0,
    @SerializedName("open_reports") val openReports: Int = 0,
    @SerializedName("orders_need_admin") val ordersNeedAdmin: Int = 0,
    @SerializedName("active_orders") val activeOrders: Int = 0,
    @SerializedName("unread_notifications") val unreadNotifications: Int = 0,
    @SerializedName("pending_total") val pendingTotal: Int = 0
)

data class AdminNotification(
    val id: Int,
    val type: String,
    val title: String,
    val message: String,
    @SerializedName("related_user_id") val relatedUserId: Int? = null,
    @SerializedName("related_username") val relatedUsername: String? = null,
    @SerializedName("entity_type") val entityType: String? = null,
    @SerializedName("entity_id") val entityId: Int? = null,
    @SerializedName("action_path") val actionPath: String? = null,
    @SerializedName("mobile_route") val mobileRoute: String? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("read_at") val readAt: String? = null
)

data class AdminTopupItem(
    val id: Int,
    @SerializedName("user_id") val userId: Int,
    val username: String,
    val email: String = "",
    val points: Int,
    val amount: Double,
    val note: String? = null,
    val status: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("reviewed_at") val reviewedAt: String? = null,
    @SerializedName("reviewed_by") val reviewedBy: Int? = null,
    @SerializedName("has_slip") val hasSlip: Boolean = false
)

data class AdminVerificationItem(
    @SerializedName("user_id") val userId: Int,
    val username: String,
    val email: String = "",
    @SerializedName("bank_name") val bankName: String,
    @SerializedName("account_name") val accountName: String,
    @SerializedName("account_number") val accountNumber: String,
    val status: String,
    @SerializedName("rejection_reason") val rejectionReason: String? = null,
    @SerializedName("submitted_at") val submittedAt: String,
    @SerializedName("reviewed_at") val reviewedAt: String? = null,
    @SerializedName("verified_at") val verifiedAt: String? = null,
    @SerializedName("has_document") val hasDocument: Boolean = false
)

data class AdminListingItem(
    val id: Int,
    @SerializedName("user_id") val userId: Int,
    val username: String,
    val title: String,
    val description: String? = null,
    val price: Double,
    val province: String,
    val amphoe: String,
    val tambon: String,
    val status: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("approved_at") val approvedAt: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null
)

data class AdminReportItem(
    val id: Int,
    @SerializedName("reporter_user_id") val reporterUserId: Int,
    @SerializedName("reported_user_id") val reportedUserId: Int? = null,
    @SerializedName("listing_id") val listingId: Int? = null,
    val category: String,
    val details: String,
    val status: String,
    @SerializedName("admin_note") val adminNote: String? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("reporter_name") val reporterName: String,
    @SerializedName("reported_name") val reportedName: String? = null,
    @SerializedName("reported_status") val reportedStatus: String? = null,
    @SerializedName("listing_title") val listingTitle: String? = null
)

data class AdminOrderItem(
    @SerializedName("order_id") val orderId: Int,
    @SerializedName("listing_id") val listingId: Int,
    @SerializedName("buyer_id") val buyerId: Int,
    @SerializedName("seller_id") val sellerId: Int,
    val price: Double,
    val title: String,
    @SerializedName("recipient_name") val recipientName: String,
    val phone: String,
    @SerializedName("house_no_moo") val houseNoMoo: String,
    val soi: String? = null,
    val road: String? = null,
    val subdistrict: String,
    val district: String,
    val province: String,
    @SerializedName("postal_code") val postalCode: String,
    val note: String? = null,
    val status: String,
    @SerializedName("tracking_number") val trackingNumber: String? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("buyer_username") val buyerUsername: String,
    @SerializedName("seller_username") val sellerUsername: String
)

data class AdminUserItem(
    val id: Int,
    val username: String,
    @SerializedName("display_name") val displayName: String = username,
    @SerializedName("admin_stars") val adminStars: Int = 0,
    @SerializedName("special_icon") val specialIcon: String? = null,
    val email: String,
    val phone: String? = null,
    @SerializedName("line_id") val lineId: String? = null,
    val role: String,
    val status: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("listing_count") val listingCount: Int = 0,
    @SerializedName("points_balance") val pointsBalance: Int = 0,
    @SerializedName("pending_display_name_request_id") val pendingDisplayNameRequestId: Int? = null,
    @SerializedName("pending_display_name") val pendingDisplayName: String? = null,
    @SerializedName("pending_display_name_reason") val pendingDisplayNameReason: String? = null,
    @SerializedName("is_admin") val isAdmin: Boolean = false
)

data class AdminActionResult(
    val id: Int? = null,
    val status: String? = null,
    @SerializedName("unread_count") val unreadCount: Int = 0
)
