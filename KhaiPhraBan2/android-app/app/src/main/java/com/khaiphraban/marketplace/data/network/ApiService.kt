package com.khaiphraban.marketplace.data.network

import com.khaiphraban.marketplace.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @GET("index.php")
    suspend fun home(@Query("action") action: String = "home"): Response<ApiResponse<HomeData>>

    @FormUrlEncoded
    @POST("index.php?action=heartbeat")
    suspend fun heartbeat(
        @Header("Authorization") authorization: String? = null,
        @Field("client_id") clientId: String
    ): Response<ApiResponse<HeartbeatResult>>

    @GET("index.php")
    suspend fun listing(
        @Header("Authorization") authorization: String? = null,
        @Query("action") action: String = "listing",
        @Query("id") id: Int
    ): Response<ApiResponse<Listing>>

    @GET("index.php")
    suspend fun captcha(@Query("action") action: String = "captcha"): Response<ApiResponse<CaptchaData>>

    @GET("index.php")
    suspend fun announcements(@Query("action") action: String = "announcements"): Response<ApiResponse<List<Announcement>>>

    @FormUrlEncoded
    @POST("index.php?action=login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<ApiResponse<AuthData>>

    @FormUrlEncoded
    @POST("index.php?action=register")
    suspend fun register(
        @Field("username") username: String,
        @Field("email") email: String,
        @Field("password") password: String,
        @Field("phone") phone: String,
        @Field("line_id") lineId: String,
        @Field("province") province: String,
        @Field("amphoe") amphoe: String,
        @Field("tambon") tambon: String
    ): Response<ApiResponse<AuthData>>

    @Multipart
    @POST("index.php?action=create_listing")
    suspend fun createListing(
        @Header("Authorization") authorization: String,
        @Part("title") title: RequestBody,
        @Part("description") description: RequestBody,
        @Part("price") price: RequestBody,
        @Part("province") province: RequestBody,
        @Part("amphoe") amphoe: RequestBody,
        @Part("tambon") tambon: RequestBody,
        @Part("allow_meetup") allowMeetup: RequestBody,
        @Part("allow_buy_now") allowBuyNow: RequestBody,
        @Part("allow_cod") allowCod: RequestBody,
        @Part("chat_first") chatFirst: RequestBody,
        @Part("captcha_token") captchaToken: RequestBody,
        @Part("captcha_answer") captchaAnswer: RequestBody,
        @Part("website") honeypot: RequestBody,
        @Part images: List<MultipartBody.Part>
    ): Response<ApiResponse<Listing>>

    @GET("index.php")
    suspend fun myListings(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "my_listings"
    ): Response<ApiResponse<List<Listing>>>

    @GET("index.php")
    suspend fun myProfile(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "my_profile"
    ): Response<ApiResponse<MyProfile>>

    @FormUrlEncoded
    @POST("index.php?action=update_display_name")
    suspend fun updateDisplayName(
        @Header("Authorization") authorization: String,
        @Field("display_name") displayName: String,
        @Field("reason") reason: String = ""
    ): Response<ApiResponse<DisplayNameUpdateResult>>

    @GET("index.php")
    suspend fun memberProfile(
        @Query("action") action: String = "member_profile",
        @Query("user_id") userId: Int
    ): Response<ApiResponse<MemberProfile>>

    @Multipart
    @POST("index.php?action=submit_verification")
    suspend fun submitVerification(
        @Header("Authorization") authorization: String,
        @Part("bank_name") bankName: RequestBody,
        @Part("account_name") accountName: RequestBody,
        @Part("account_number") accountNumber: RequestBody,
        @Part document: MultipartBody.Part
    ): Response<ApiResponse<VerificationInfo>>


    @FormUrlEncoded
    @POST("index.php?action=delete_listing")
    suspend fun deleteListing(
        @Header("Authorization") authorization: String,
        @Field("id") id: Int
    ): Response<ApiResponse<ActionData>>

    @FormUrlEncoded
    @POST("index.php?action=mark_sold")
    suspend fun markSold(
        @Header("Authorization") authorization: String,
        @Field("id") id: Int
    ): Response<ApiResponse<ActionData>>
    @GET("index.php")
    suspend fun chatThreads(@Header("Authorization") authorization: String, @Query("action") action: String = "chat_threads"): Response<ApiResponse<List<ChatThread>>>


    @GET("index.php")
    suspend fun chatUnreadCount(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "chat_unread_count"
    ): Response<ApiResponse<UnreadCount>>

    @GET("index.php")
    suspend fun chatMessages(@Header("Authorization") authorization: String, @Query("action") action: String = "chat_messages", @Query("listing_id") listingId: Int, @Query("buyer_id") buyerId: Int = 0): Response<ApiResponse<List<ChatMessage>>>


    @GET("index.php")
    suspend fun latestChatId(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "chat_latest_id"
    ): Response<ApiResponse<LatestChatId>>

    @GET("index.php")
    suspend fun chatUpdates(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "chat_updates",
        @Query("after_id") afterId: Long
    ): Response<ApiResponse<List<ChatUpdate>>>

    @FormUrlEncoded
    @POST("index.php?action=send_message")
    suspend fun sendMessage(@Header("Authorization") authorization: String, @Field("listing_id") listingId: Int, @Field("buyer_id") buyerId: Int, @Field("message") message: String): Response<ApiResponse<ActionData>>

    @Multipart
    @POST("index.php?action=send_chat_image")
    suspend fun sendChatImage(
        @Header("Authorization") authorization: String,
        @Part("listing_id") listingId: RequestBody,
        @Part("buyer_id") buyerId: RequestBody,
        @Part image: MultipartBody.Part
    ): Response<ApiResponse<ActionData>>

    @FormUrlEncoded
    @POST("index.php?action=register_push_token")
    suspend fun registerPushToken(
        @Header("Authorization") authorization: String,
        @Field("token") token: String
    ): Response<ApiResponse<ActionData>>

    @FormUrlEncoded
    @POST("index.php?action=unregister_push_token")
    suspend fun unregisterPushToken(
        @Header("Authorization") authorization: String,
        @Field("token") token: String
    ): Response<ApiResponse<ActionData>>


    @GET("index.php")
    suspend fun walletSummary(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "wallet_summary"
    ): Response<ApiResponse<WalletSummary>>

    @GET("index.php")
    suspend fun lotteryOverview(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "lottery_overview"
    ): Response<ApiResponse<LotteryOverview>>

    @FormUrlEncoded
    @POST("index.php?action=lottery_buy_number")
    suspend fun lotteryBuyNumber(
        @Header("Authorization") authorization: String,
        @Field("round_id") roundId: Int,
        @Field("number") number: String,
        @Field("request_key") requestKey: String
    ): Response<ApiResponse<LotteryPurchaseResult>>

    @Multipart
    @POST("index.php?action=request_topup")
    suspend fun requestTopup(
        @Header("Authorization") authorization: String,
        @Part("amount") amount: RequestBody,
        @Part("note") note: RequestBody,
        @Part slip: MultipartBody.Part
    ): Response<ApiResponse<ActionData>>

    @FormUrlEncoded
    @POST("index.php?action=purchase_premium")
    suspend fun purchasePremium(
        @Header("Authorization") authorization: String,
        @Field("listing_id") listingId: Int,
        @Field("plan_id") planId: Int,
        @Field("request_key") requestKey: String
    ): Response<ApiResponse<PurchasePremiumResult>>

    @FormUrlEncoded
    @POST("index.php?action=purchase_boost")
    suspend fun purchaseBoost(
        @Header("Authorization") authorization: String,
        @Field("listing_id") listingId: Int,
        @Field("request_key") requestKey: String
    ): Response<ApiResponse<PurchaseBoostResult>>

    @Multipart
    @POST("index.php?action=create_order")
    suspend fun createOrder(
        @Header("Authorization") authorization: String,
        @Part("listing_id") listingId: RequestBody,
        @Part("recipient_name") recipientName: RequestBody,
        @Part("phone") phone: RequestBody,
        @Part("house_no_moo") houseNoMoo: RequestBody,
        @Part("soi") soi: RequestBody,
        @Part("road") road: RequestBody,
        @Part("subdistrict") subdistrict: RequestBody,
        @Part("district") district: RequestBody,
        @Part("province") province: RequestBody,
        @Part("postal_code") postalCode: RequestBody,
        @Part("payment_method") paymentMethod: RequestBody,
        @Part("request_key") requestKey: RequestBody,
        @Part slip: MultipartBody.Part? = null
    ): Response<ApiResponse<Order>>

    @GET("index.php")
    suspend fun myOrders(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "my_orders"
    ): Response<ApiResponse<List<Order>>>

    @GET("index.php")
    suspend fun receivedOrders(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "received_orders"
    ): Response<ApiResponse<List<Order>>>

    @GET("index.php")
    suspend fun orderDetail(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "order_detail",
        @Query("order_id") orderId: Int
    ): Response<ApiResponse<Order>>

    @GET("index.php")
    @Streaming
    suspend fun orderSlip(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "order_slip",
        @Query("order_id") orderId: Int
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("index.php?action=order_action")
    suspend fun orderAction(
        @Header("Authorization") authorization: String,
        @Field("order_id") orderId: Int,
        @Field("order_action") orderAction: String,
        @Field("tracking_number") trackingNumber: String = ""
    ): Response<ApiResponse<Order>>


    @FormUrlEncoded
    @POST("index.php?action=submit_rating")
    suspend fun submitRating(
        @Header("Authorization") authorization: String,
        @Field("order_id") orderId: Int,
        @Field("rating") rating: Int,
        @Field("review_text") reviewText: String
    ): Response<ApiResponse<Order>>

    @FormUrlEncoded
    @POST("index.php?action=report_user")
    suspend fun reportUser(
        @Header("Authorization") authorization: String,
        @Field("listing_id") listingId: Int,
        @Field("reported_user_id") reportedUserId: Int,
        @Field("category") category: String,
        @Field("details") details: String
    ): Response<ApiResponse<ActionData>>

    @GET("index.php")
    suspend fun adminDashboard(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "admin_dashboard"
    ): Response<ApiResponse<AdminDashboard>>

    @GET("index.php")
    suspend fun adminNotifications(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "admin_notifications",
        @Query("unread") unread: Int = 0
    ): Response<ApiResponse<List<AdminNotification>>>

    @FormUrlEncoded
    @POST("index.php?action=admin_notification_read")
    suspend fun adminNotificationRead(
        @Header("Authorization") authorization: String,
        @Field("notification_id") notificationId: Int
    ): Response<ApiResponse<AdminActionResult>>

    @FormUrlEncoded
    @POST("index.php?action=admin_notification_read_all")
    suspend fun adminNotificationReadAll(
        @Header("Authorization") authorization: String,
        @Field("_") unused: String = ""
    ): Response<ApiResponse<AdminActionResult>>

    @GET("index.php")
    suspend fun adminTopups(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "admin_topups",
        @Query("status") status: String = "pending"
    ): Response<ApiResponse<List<AdminTopupItem>>>

    @FormUrlEncoded
    @POST("index.php?action=admin_review_topup")
    suspend fun adminReviewTopup(
        @Header("Authorization") authorization: String,
        @Field("id") id: Int,
        @Field("decision") decision: String
    ): Response<ApiResponse<AdminActionResult>>

    @GET("index.php")
    suspend fun adminVerifications(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "admin_verifications",
        @Query("status") status: String = "pending"
    ): Response<ApiResponse<List<AdminVerificationItem>>>

    @FormUrlEncoded
    @POST("index.php?action=admin_review_verification")
    suspend fun adminReviewVerification(
        @Header("Authorization") authorization: String,
        @Field("user_id") userId: Int,
        @Field("decision") decision: String,
        @Field("rejection_reason") rejectionReason: String = ""
    ): Response<ApiResponse<AdminActionResult>>

    @GET("index.php")
    suspend fun adminListings(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "admin_listings",
        @Query("status") status: String = "pending"
    ): Response<ApiResponse<List<AdminListingItem>>>

    @FormUrlEncoded
    @POST("index.php?action=admin_update_listing")
    suspend fun adminUpdateListing(
        @Header("Authorization") authorization: String,
        @Field("id") id: Int,
        @Field("status") status: String
    ): Response<ApiResponse<AdminActionResult>>

    @GET("index.php")
    suspend fun adminReports(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "admin_reports",
        @Query("status") status: String = "open"
    ): Response<ApiResponse<List<AdminReportItem>>>

    @FormUrlEncoded
    @POST("index.php?action=admin_update_report")
    suspend fun adminUpdateReport(
        @Header("Authorization") authorization: String,
        @Field("id") id: Int,
        @Field("report_action") reportAction: String,
        @Field("admin_note") adminNote: String = ""
    ): Response<ApiResponse<AdminActionResult>>

    @GET("index.php")
    suspend fun adminOrders(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "admin_orders",
        @Query("status") status: String = ""
    ): Response<ApiResponse<List<AdminOrderItem>>>

    @GET("index.php")
    suspend fun adminUsers(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "admin_users",
        @Query("q") query: String = ""
    ): Response<ApiResponse<List<AdminUserItem>>>

    @FormUrlEncoded
    @POST("index.php?action=admin_update_user")
    suspend fun adminUpdateUser(
        @Header("Authorization") authorization: String,
        @Field("id") id: Int,
        @Field("display_name") displayName: String,
        @Field("admin_stars") adminStars: Int,
        @Field("special_icon") specialIcon: String,
        @Field("points_delta") pointsDelta: Int,
        @Field("role") role: String,
        @Field("status") status: String
    ): Response<ApiResponse<AdminActionResult>>

    @FormUrlEncoded
    @POST("index.php?action=admin_review_display_name")
    suspend fun adminReviewDisplayName(
        @Header("Authorization") authorization: String,
        @Field("request_id") requestId: Int,
        @Field("decision") decision: String,
        @Field("admin_note") adminNote: String = ""
    ): Response<ApiResponse<AdminActionResult>>

    @GET("admin_media.php")
    @Streaming
    suspend fun adminMedia(
        @Header("Authorization") authorization: String,
        @Query("kind") kind: String,
        @Query("id") id: Int? = null,
        @Query("user_id") userId: Int? = null
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("index.php?action=toggle_favorite")
    suspend fun toggleFavorite(
        @Header("Authorization") authorization: String,
        @Field("listing_id") listingId: Int
    ): Response<ApiResponse<FavoriteState>>
}
