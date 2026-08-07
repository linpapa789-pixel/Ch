package com.example.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class TokenStatus {
    FOUND,
    USED,
    INVALID,
    LIMITED,
    ERROR,
    UNKNOWN
}

data class ValidationResult(
    val status: TokenStatus,
    val rawResponse: String,
    val isSuccessfulHttp: Boolean,
    val httpCode: Int
)

data class BalanceDetails(
    val plan: String,
    val time: String
)

// ============================================================
// ✅ Cookie Jar - like Python's requests.Session()
// ============================================================
class PersistentCookieJar : CookieJar {
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        cookieStore[host] = cookies.toMutableList()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore[url.host] ?: emptyList()
    }
}

class NetworkClient {

    // ============================================================
    // ✅ Connection pooling + Cookie Jar
    // ============================================================
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(100, 5, TimeUnit.MINUTES))  // ✅ Connection Pool
        .cookieJar(PersistentCookieJar())  // ✅ Cookie Jar
        .build()

    private val redirectHandlingClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun generateRandomMac(): String {
        val chars = "0123456789ABCDEF"
        return (1..6).joinToString(":") {
            "" + chars[kotlin.random.Random.nextInt(16)] + chars[kotlin.random.Random.nextInt(16)]
        }
    }

    fun replaceMacInUrl(url: String, newMac: String): String {
        if (url.isBlank()) return url
        return try {
            val uri = Uri.parse(url)
            val builder = uri.buildUpon()
            val queryNames = uri.queryParameterNames
            if (queryNames.isNotEmpty()) {
                builder.clearQuery()
                var replaced = false
                for (name in queryNames) {
                    if (name.lowercase() == "mac") {
                        builder.appendQueryParameter(name, newMac)
                        replaced = true
                    } else {
                        val values = uri.getQueryParameters(name)
                        for (value in values) {
                            builder.appendQueryParameter(name, value)
                        }
                    }
                }
                if (!replaced) {
                    builder.appendQueryParameter("mac", newMac)
                }
            } else {
                builder.appendQueryParameter("mac", newMac)
            }
            builder.build().toString()
        } catch (e: Exception) {
            if (url.contains("mac=", ignoreCase = true)) {
                val regex = Regex("(?i)mac=[^&]*")
                url.replace(regex, "mac=$newMac")
            } else {
                if (url.contains("?")) "$url&mac=$newMac" else "$url?mac=$newMac"
            }
        }
    }

    // ============================================================
    // Step 1: Get Session ID (with previous session reuse)
    // ============================================================
    suspend fun fetchSessionIdFromGateway(
        gatewayUrlWithMac: String,
        previousSessionId: String? = null
    ): String? = withContext(Dispatchers.IO) {
        var currentUrl = gatewayUrlWithMac
        var redirectCount = 0
        val maxRedirects = 20

        // ✅ If previous session exists, try to extract from URL first
        if (previousSessionId != null) {
            val extracted = extractSessionId(gatewayUrlWithMac)
            if (extracted != null) {
                return@withContext extracted
            }
        }

        while (redirectCount < maxRedirects) {
            var sessId = extractSessionId(currentUrl)
            if (sessId != null) {
                return@withContext sessId
            }

            try {
                val request = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

                redirectHandlingClient.newCall(request).execute().use { response ->
                    val finalUrl = response.request.url.toString()
                    sessId = extractSessionId(finalUrl)
                    if (sessId != null) return@withContext sessId

                    val bodyText = response.body?.string() ?: ""
                    sessId = extractSessionIdFromBody(bodyText)
                    if (sessId != null) return@withContext sessId

                    val statusCode = response.code
                    if (statusCode in 300..399) {
                        val location = response.header("Location")
                        if (!location.isNullOrBlank()) {
                            val nextHttpUrl = response.request.url.resolve(location)
                            val nextUrl = nextHttpUrl?.toString() ?: location
                            sessId = extractSessionId(nextUrl)
                            if (sessId != null) return@withContext sessId
                            currentUrl = nextUrl
                            redirectCount++
                        } else {
                            break
                        }
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                break
            }
        }
        // ✅ Return previous session if failed (like Python)
        previousSessionId
    }

    private fun extractSessionIdFromBody(body: String): String? {
        val regexes = listOf(
            Regex("(?i)sessionId\\s*=\\s*[\"']([^\"']+)[\"']"),
            Regex("(?i)session_id\\s*=\\s*[\"']([^\"']+)[\"']"),
            Regex("(?i)sessionKey\\s*=\\s*[\"']([^\"']+)[\"']"),
            Regex("(?i)\"sessionId\"\\s*:\\s*\"([^\"]+)\""),
            Regex("(?i)\"session_id\"\\s*:\\s*\"([^\"]+)\""),
            Regex("(?i)\"sessionKey\"\\s*:\\s*\"([^\"]+)\""),
            Regex("(?i)sessionId\\s*:\\s*\"([^\"]+)\""),
            Regex("(?i)session_id\\s*:\\s*\"([^\"]+)\""),
            Regex("(?i)index\\.ps\\?([^\"']+)"),
            Regex("(?i)index\\.ps\\?sessionId=([^\"'&]+)")
        )
        for (regex in regexes) {
            val match = regex.find(body)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }
        return null
    }

    // ============================================================
    // Step 6: Get Balance Details
    // ============================================================
    suspend fun getBalanceDetails(sessionId: String): BalanceDetails = withContext(Dispatchers.IO) {
        val url = "https://portal-as.ruijienetworks.com/api/auth/balance/getBalance/$sessionId"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://portal-as.ruijienetworks.com/download/static/maccauth/src/balance.html?sessionId=$sessionId")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val resultObj = json.optJSONObject("result") ?: json
                    val profileName = resultObj.optString("profileName", "Unknown")
                    val totalMinutes = resultObj.optString("totalMinutes", "Unknown")
                    
                    val timeDisplay = if (totalMinutes != "Unknown") {
                        try {
                            val mins = totalMinutes.toInt()
                            val hours = mins / 60
                            val remMins = mins % 60
                            when {
                                hours > 0 && remMins > 0 -> "${hours}h ${remMins}m"
                                hours > 0 -> "${hours}h"
                                else -> "${remMins}m"
                            }
                        } catch (e: Exception) {
                            "$totalMinutes min"
                        }
                    } else {
                        "Unknown"
                    }
                    
                    BalanceDetails(plan = profileName, time = timeDisplay)
                } else {
                    BalanceDetails(plan = "Unknown", time = "Unknown")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            BalanceDetails(plan = "Unknown", time = "Unknown")
        }
    }

    fun extractSessionId(url: String): String? {
        if (url.isBlank()) return null
        return try {
            val uri = Uri.parse(url)
            val queryNames = uri.queryParameterNames
            val keysToCheck = listOf("sessionid", "session_id", "session", "id", "sid", "sessionkey")
            for (name in queryNames) {
                if (keysToCheck.contains(name.lowercase())) {
                    val value = uri.getQueryParameter(name)
                    if (!value.isNullOrBlank()) return value
                }
            }
            null
        } catch (e: Exception) {
            if (url.contains("sessionId=", ignoreCase = true)) {
                val match = Regex("(?i)sessionId=([^&]+)").find(url)
                match?.groupValues?.get(1)
            } else {
                null
            }
        }
    }

    // ============================================================
    // Step 2: Download CAPTCHA Image
    // ============================================================
    suspend fun downloadImage(imageUrl: String, sessionId: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36")
                .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .header("Referer", "https://portal-as.ruijienetworks.com/download/static/maccauth/src/index.html?sessionId=$sessionId")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body ?: return@withContext null
                    val bytes = body.bytes()
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ============================================================
    // Step 4: Verify CAPTCHA
    // ============================================================
    suspend fun verifyCaptcha(sessionId: String, authCode: String): Boolean = withContext(Dispatchers.IO) {
        val url = "https://portal-as.ruijienetworks.com/api/auth/captcha/verify"
        try {
            val jsonObject = JSONObject().apply {
                put("sessionId", sessionId)
                put("authCode", authCode)
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonObject.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36")
                .header("Referer", "https://portal-as.ruijienetworks.com/download/static/maccauth/src/index.html?sessionId=$sessionId")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "{}"
                    val json = JSONObject(bodyStr)
                    json.optBoolean("success", false)
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ============================================================
    // Step 5: Check Voucher
    // ============================================================
    suspend fun validateToken(
        postUrl: String,
        sessionId: String,
        authCode: String,
        accessCode: String
    ): ValidationResult = withContext(Dispatchers.IO) {
        try {
            val jsonObject = JSONObject().apply {
                put("sessionId", sessionId)
                put("authCode", authCode)
                put("accessCode", accessCode)
                put("apiVersion", 1)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonObject.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(postUrl)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36")
                .header("Referer", "https://portal-as.ruijienetworks.com/download/static/maccauth/src/index.html?sessionId=$sessionId")
                .build()

            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string() ?: ""
                val httpCode = response.code
                val isSuccessHttp = response.isSuccessful

                val status = parseStatus(rawBody)

                ValidationResult(
                    status = status,
                    rawResponse = rawBody,
                    isSuccessfulHttp = isSuccessHttp,
                    httpCode = httpCode
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ValidationResult(
                status = TokenStatus.ERROR,
                rawResponse = e.message ?: "Network error occurred",
                isSuccessfulHttp = false,
                httpCode = 0
            )
        }
    }

    // ============================================================
    // ✅ parseStatus() - EXACTLY like Python
    // ============================================================
    fun parseStatus(json: String): TokenStatus {
        val normalized = json.trim()
        if (normalized.isEmpty()) return TokenStatus.UNKNOWN

        // 1. FOUND
        if (normalized.contains("logonUrl", ignoreCase = true)) {
            return TokenStatus.FOUND
        }
        
        // 2. INVALID (check before USED)
        if (normalized.contains("Authentication failed", ignoreCase = true)) {
            return TokenStatus.INVALID
        }
        
        // 3. USED
        if (normalized.contains("STA", ignoreCase = true)) {
            return TokenStatus.USED
        }
        
        // 4. LIMITED
        if (normalized.contains("request limited", ignoreCase = true)) {
            return TokenStatus.LIMITED
        }

        // 5. JSON fallback
        try {
            val obj = JSONObject(json)
            val message = obj.optString("message", "").lowercase()
            
            if (message.contains("authentication failed")) {
                return TokenStatus.INVALID
            }
            if (message.contains("request limited")) {
                return TokenStatus.LIMITED
            }
            if (message.contains("sta")) {
                return TokenStatus.USED
            }
            if (message.contains("logonurl")) {
                return TokenStatus.FOUND
            }
        } catch (e: Exception) {
            // Fall through
        }

        return TokenStatus.UNKNOWN
    }
}
