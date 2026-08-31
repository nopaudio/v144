package com.khaiphraban.marketplace.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import kotlin.math.max

object UploadUtils {
    fun text(value: String) = value.toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull())

    fun imagePart(context: Context, uri: Uri, index: Int): MultipartBody.Part {
        val bitmap = decodeForUpload(context, uri, 2048)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out)
        bitmap.recycle()
        val body = out.toByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(
            "images[]",
            "listing_${System.currentTimeMillis()}_${index + 1}.jpg",
            body
        )
    }

    fun chatImagePart(context: Context, uri: Uri): MultipartBody.Part {
        val bitmap = decodeForUpload(context, uri, 1600)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        bitmap.recycle()
        val body = out.toByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(
            "image",
            "chat_${System.currentTimeMillis()}.jpg",
            body
        )
    }

    fun identityImagePart(context: Context, uri: Uri): MultipartBody.Part {
        val bitmap = decodeForUpload(context, uri, 2000)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        bitmap.recycle()
        val body = out.toByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(
            "document",
            "identity_${System.currentTimeMillis()}.jpg",
            body
        )
    }

    fun slipImagePart(context: Context, uri: Uri): MultipartBody.Part {
        // Decode with sampling before allocating a full bitmap. This is important
        // for very large camera photos/screenshots and older Android devices.
        val bitmap = decodeForUpload(context, uri, 2000)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        bitmap.recycle()
        val body = out.toByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(
            "slip",
            "slip_${System.currentTimeMillis()}.jpg",
            body
        )
    }

    private fun decodeForUpload(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return android.graphics.ImageDecoder.decodeBitmap(
                android.graphics.ImageDecoder.createSource(resolver, uri)
            ) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > maxSide) {
                    val ratio = maxSide.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * ratio).toInt().coerceAtLeast(1),
                        (info.size.height * ratio).toInt().coerceAtLeast(1)
                    )
                }
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: error("เปิดรูปไม่ได้")

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("ไฟล์รูปไม่ถูกต้อง")

        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxSide) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("เปิดรูปไม่ได้")

        return scaleDown(decoded, maxSide)
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
