package com.khaiphraban.marketplace.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.khaiphraban.marketplace.data.model.*
import com.khaiphraban.marketplace.data.network.ApiService
import com.khaiphraban.marketplace.util.UploadUtils
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import kotlinx.coroutines.CancellationException

class MarketplaceRepository(private val api: ApiService) {
    private val gson = Gson()

    private suspend fun <T> execute(call: suspend () -> Response<ApiResponse<T>>): Result<T> {
        return try {
            val response = call()
            val body = response.body()
            if (response.isSuccessful && body?.success == true && body.data != null) {
                Result.success(body.data)
            } else {
                val errorMessage = body?.message ?: runCatching {
                    response.errorBody()?.string()?.let {
                        gson.fromJson(it, JsonObject::class.java)
                            ?.get("message")
                            ?.takeUnless { value -> value.isJsonNull }
                            ?.asString
                    }
                }.getOrNull() ?: "เซิร์ฟเวอร์ตอบกลับผิดพลาด (${response.code()})"
                Result.failure(IllegalStateException(errorMessage))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "เชื่อมต่อเซิร์ฟเวอร์ไม่ได้", e))
        }
    }

    suspend fun home() = execute { api.home() }
    suspend fun heartbeat(token: String?, clientId: String) =
        execute { api.heartbeat(token?.let { "Bearer $it" }, clientId) }
    suspend fun listing(id: Int, token: String?) = execute {
        api.listing(authorization = token?.let { "Bearer $it" }, id = id)
    }
    suspend fun captcha() = execute { api.captcha() }
    suspend fun announcements() = execute { api.announcements() }
    suspend fun login(username: String, password: String) = execute { api.login(username, password) }
    suspend fun register(
        username: String, email: String, password: String, phone: String, lineId: String,
        province: String, amphoe: String, tambon: String
    ) = execute { api.register(username, email, password, phone, lineId, province, amphoe, tambon) }

    suspend fun createListing(
        token: String,
        title: RequestBody,
        description: RequestBody,
        price: RequestBody,
        province: RequestBody,
        amphoe: RequestBody,
        tambon: RequestBody,
        allowMeetup: RequestBody,
        allowBuyNow: RequestBody,
        allowCod: RequestBody,
        chatFirst: RequestBody,
        captchaToken: RequestBody,
        captchaAnswer: RequestBody,
        honeypot: RequestBody,
        images: List<MultipartBody.Part>
    ) = execute {
        api.createListing(
            "Bearer $token", title, description, price, province, amphoe, tambon,
            allowMeetup, allowBuyNow, allowCod, chatFirst,
            captchaToken, captchaAnswer, honeypot, images
        )
    }

    suspend fun myListings(token: String) = execute { api.myListings("Bearer $token") }
    suspend fun myProfile(token: String) = execute { api.myProfile("Bearer $token") }
    suspend fun updateDisplayName(token: String, displayName: String, reason: String = "") =
        execute { api.updateDisplayName("Bearer $token", displayName, reason) }

    suspend fun memberProfile(userId: Int) = execute { api.memberProfile(userId = userId) }
    suspend fun submitVerification(
        token: String,
        bankName: RequestBody,
        accountName: RequestBody,
        accountNumber: RequestBody,
        document: MultipartBody.Part
    ) = execute {
        api.submitVerification("Bearer $token", bankName, accountName, accountNumber, document)
    }

    suspend fun deleteListing(token: String, id: Int) = execute { api.deleteListing("Bearer $token", id) }
    suspend fun markSold(token: String, id: Int) = execute { api.markSold("Bearer $token", id) }
    suspend fun chatThreads(token: String) = execute { api.chatThreads("Bearer $token") }
    suspend fun chatUnreadCount(token: String) = execute { api.chatUnreadCount("Bearer $token") }
    suspend fun chatMessages(token: String, listingId: Int, buyerId: Int) = execute { api.chatMessages("Bearer $token", listingId = listingId, buyerId = buyerId) }
    suspend fun latestChatId(token: String) = execute { api.latestChatId("Bearer $token") }
    suspend fun chatUpdates(token: String, afterId: Long) = execute { api.chatUpdates("Bearer $token", afterId = afterId) }
    suspend fun sendMessage(token: String, listingId: Int, buyerId: Int, message: String) =
        execute { api.sendMessage("Bearer $token", listingId, buyerId, message) }

    suspend fun sendChatImage(
        token: String,
        listingId: RequestBody,
        buyerId: RequestBody,
        image: MultipartBody.Part
    ) = execute { api.sendChatImage("Bearer $token", listingId, buyerId, image) }

    suspend fun registerPushToken(token: String, deviceToken: String) =
        execute { api.registerPushToken("Bearer $token", deviceToken) }

    suspend fun unregisterPushToken(token: String, deviceToken: String) =
        execute { api.unregisterPushToken("Bearer $token", deviceToken) }

    suspend fun walletSummary(token: String) =
        execute { api.walletSummary("Bearer $token") }

    suspend fun lotteryOverview(token: String) =
        execute { api.lotteryOverview("Bearer $token") }

    suspend fun lotteryBuyNumber(token: String, roundId: Int, number: String, requestKey: String) =
        execute { api.lotteryBuyNumber("Bearer $token", roundId, number, requestKey) }

    suspend fun requestTopup(
        token: String,
        amount: RequestBody,
        note: RequestBody,
        slip: MultipartBody.Part
    ) = execute { api.requestTopup("Bearer $token", amount, note, slip) }

    suspend fun purchasePremium(token: String, listingId: Int, planId: Int, requestKey: String) =
        execute { api.purchasePremium("Bearer $token", listingId, planId, requestKey) }

    suspend fun purchaseBoost(token: String, listingId: Int, requestKey: String) =
        execute { api.purchaseBoost("Bearer $token", listingId, requestKey) }

    suspend fun createOrder(
        token: String,
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
        requestKey: String,
        slip: MultipartBody.Part?
    ) = execute {
        api.createOrder(
            "Bearer $token",
            UploadUtils.text(listingId.toString()),
            UploadUtils.text(recipientName),
            UploadUtils.text(phone),
            UploadUtils.text(houseNoMoo),
            UploadUtils.text(soi),
            UploadUtils.text(road),
            UploadUtils.text(subdistrict),
            UploadUtils.text(district),
            UploadUtils.text(province),
            UploadUtils.text(postalCode),
            UploadUtils.text(paymentMethod),
            UploadUtils.text(requestKey),
            slip
        )
    }

    suspend fun myOrders(token: String) = execute { api.myOrders("Bearer $token") }
    suspend fun receivedOrders(token: String) = execute { api.receivedOrders("Bearer $token") }
    suspend fun orderDetail(token: String, orderId: Int) =
        execute { api.orderDetail("Bearer $token", orderId = orderId) }
    suspend fun orderSlip(token: String, orderId: Int): Result<ByteArray> {
        return try {
            val response = api.orderSlip("Bearer $token", orderId = orderId)
            if (response.isSuccessful) {
                val bytes = response.body()?.bytes()
                if (bytes != null && bytes.isNotEmpty()) Result.success(bytes)
                else Result.failure(IllegalStateException("ไม่พบสลิปการชำระเงิน"))
            } else {
                Result.failure(IllegalStateException("เปิดสลิปไม่ได้ (${response.code()})"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "โหลดสลิปไม่ได้", e))
        }
    }

    suspend fun orderAction(token: String, orderId: Int, action: String, trackingNumber: String = "") =
        execute { api.orderAction("Bearer $token", orderId, action, trackingNumber) }
    suspend fun submitRating(token: String, orderId: Int, rating: Int, reviewText: String) =
        execute { api.submitRating("Bearer $token", orderId, rating, reviewText) }


    suspend fun adminDashboard(token: String) =
        execute { api.adminDashboard("Bearer $token") }

    suspend fun adminNotifications(token: String, unreadOnly: Boolean = false) =
        execute { api.adminNotifications("Bearer $token", unread = if (unreadOnly) 1 else 0) }

    suspend fun adminNotificationRead(token: String, notificationId: Int) =
        execute { api.adminNotificationRead("Bearer $token", notificationId) }

    suspend fun adminNotificationReadAll(token: String) =
        execute { api.adminNotificationReadAll("Bearer $token") }

    suspend fun adminTopups(token: String, status: String = "pending") =
        execute { api.adminTopups("Bearer $token", status = status) }

    suspend fun adminReviewTopup(token: String, id: Int, decision: String) =
        execute { api.adminReviewTopup("Bearer $token", id, decision) }

    suspend fun adminVerifications(token: String, status: String = "pending") =
        execute { api.adminVerifications("Bearer $token", status = status) }

    suspend fun adminReviewVerification(token: String, userId: Int, decision: String, reason: String = "") =
        execute { api.adminReviewVerification("Bearer $token", userId, decision, reason) }

    suspend fun adminListings(token: String, status: String = "pending") =
        execute { api.adminListings("Bearer $token", status = status) }

    suspend fun adminUpdateListing(token: String, id: Int, status: String) =
        execute { api.adminUpdateListing("Bearer $token", id, status) }

    suspend fun adminReports(token: String, status: String = "open") =
        execute { api.adminReports("Bearer $token", status = status) }

    suspend fun adminUpdateReport(token: String, id: Int, action: String, note: String = "") =
        execute { api.adminUpdateReport("Bearer $token", id, action, note) }

    suspend fun adminOrders(token: String, status: String = "") =
        execute { api.adminOrders("Bearer $token", status = status) }

    suspend fun adminUsers(token: String, query: String = "") =
        execute { api.adminUsers("Bearer $token", query = query) }

    suspend fun adminUpdateUser(
        token: String,
        id: Int,
        displayName: String,
        adminStars: Int,
        specialIcon: String,
        pointsDelta: Int,
        role: String,
        status: String
    ) = execute {
        api.adminUpdateUser(
            "Bearer $token", id, displayName, adminStars, specialIcon,
            pointsDelta, role, status
        )
    }

    suspend fun adminReviewDisplayName(
        token: String,
        requestId: Int,
        decision: String,
        adminNote: String = ""
    ) = execute {
        api.adminReviewDisplayName("Bearer $token", requestId, decision, adminNote)
    }

    suspend fun adminMedia(token: String, kind: String, id: Int? = null, userId: Int? = null): Result<ByteArray> {
        return try {
            val response = api.adminMedia("Bearer $token", kind, id, userId)
            if (response.isSuccessful) {
                val bytes = response.body()?.bytes()
                if (bytes != null && bytes.isNotEmpty()) Result.success(bytes)
                else Result.failure(IllegalStateException("ไม่พบรูปหลักฐาน"))
            } else {
                Result.failure(IllegalStateException("เปิดรูปหลักฐานไม่ได้ (${response.code()})"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "โหลดรูปหลักฐานไม่ได้", e))
        }
    }

    suspend fun reportUser(token: String, listingId: Int, reportedUserId: Int, category: String, details: String) =
        execute { api.reportUser("Bearer $token", listingId, reportedUserId, category, details) }

    suspend fun toggleFavorite(token: String, listingId: Int) =
        execute { api.toggleFavorite("Bearer $token", listingId) }
}
