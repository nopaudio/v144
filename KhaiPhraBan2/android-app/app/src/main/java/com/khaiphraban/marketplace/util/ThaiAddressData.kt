package com.khaiphraban.marketplace.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class ThaiAddressRow(
    val province: String,
    val district: String,
    val subdistrict: String
)

object ThaiAddressData {
    private const val DATA_URL = "https://cdn.jsdelivr.net/gh/thailand-geography-data/thailand-geography-json@main/src/geography.json"
    @Volatile private var cache: List<ThaiAddressRow>? = null

    suspend fun load(): List<ThaiAddressRow> = cache ?: withContext(Dispatchers.IO) {
        val connection = (URL(DATA_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
        }
        val text = try {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
        val json = JSONArray(text)
        val rows = ArrayList<ThaiAddressRow>(json.length())
        for (i in 0 until json.length()) {
            val item = json.getJSONObject(i)
            rows += ThaiAddressRow(
                province = item.optString("provinceNameTh"),
                district = item.optString("districtNameTh"),
                subdistrict = item.optString("subdistrictNameTh")
            )
        }
        rows.filter { it.province.isNotBlank() && it.district.isNotBlank() && it.subdistrict.isNotBlank() }
            .also { cache = it }
    }
}
