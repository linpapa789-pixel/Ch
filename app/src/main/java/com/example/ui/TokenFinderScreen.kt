package com.example.ui

import android.content.Context
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LogEntry
import com.example.data.LogType
import com.example.data.ValidToken
import com.example.data.ServerResponseLog
import com.example.data.TokenStatus
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Surface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TokenFinderScreen(
    viewModel: TokenFinderViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    // Collecting states from ViewModel
    val gatewayUrl by viewModel.gatewayUrl.collectAsState()
    val sessionId by viewModel.sessionId.collectAsState()
    val imageUrlTemplate by viewModel.imageUrlTemplate.collectAsState()
    val postUrl by viewModel.postUrl.collectAsState()
    
    val keySession by viewModel.keySession.collectAsState()
    val keyCaptcha by viewModel.keyCaptcha.collectAsState()
    val keyToken by viewModel.keyToken.collectAsState()
    
    val scanDelayMs by viewModel.scanDelayMs.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val workerCount by viewModel.workerCount.collectAsState()
    val activeWorkers by viewModel.activeWorkers.collectAsState()
    
    val currentChallengeImage by viewModel.currentChallengeImage.collectAsState()
    val currentCaptchaText by viewModel.currentCaptchaText.collectAsState()
    val currentTestingToken by viewModel.currentTestingToken.collectAsState()
    
    val logs by viewModel.logs.collectAsState()
    val serverResponseLogs by viewModel.serverResponseLogs.collectAsState()
    val attemptedCount by viewModel.attemptedCount.collectAsState()
    val validCount by viewModel.validCount.collectAsState()
    val usedCount by viewModel.usedCount.collectAsState()
    val invalidCount by viewModel.invalidCount.collectAsState()
    val limitedCount by viewModel.limitedCount.collectAsState()
    val errorCount by viewModel.errorCount.collectAsState()
    val unknownCount by viewModel.unknownCount.collectAsState()
    
    val tokenMode by viewModel.tokenMode.collectAsState()
    val numberSearchMode by viewModel.numberSearchMode.collectAsState()
    val tokenLength by viewModel.tokenLength.collectAsState()

    val savedTokens by viewModel.savedTokens.collectAsState()
    
    var showAdvancedSettings by remember { mutableStateOf(false) }

    // Safe area window insets
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentWindowInsets = WindowInsets.navigationBars
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = statusBarPadding + 12.dp,
                    bottom = navBarPadding + 12.dp,
                    start = 16.dp,
                    end = 16.dp
                )
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header Banner
            HeaderSection()

            // 2. Gateway URL Entry Card
            GatewayConfigCard(
                gatewayUrl = gatewayUrl,
                sessionId = sessionId,
                workerCount = workerCount,
                onWorkerCountChange = { viewModel.updateWorkerCount(it) },
                isProcessing = isProcessing,
                onUrlChange = { viewModel.updateGatewayUrl(it) },
                onRotateMacAndFetch = { viewModel.rotateMacAndFetchSession() },
                onFetchDirectly = { viewModel.fetchSessionIdDirectly() }
            )

            // 2.5 Token Generator Strategy Configuration Panel
            TokenGenerationCard(
                tokenMode = tokenMode,
                onTokenModeChange = { viewModel.updateTokenMode(it) },
                numberSearchMode = numberSearchMode,
                onNumberSearchModeChange = { viewModel.updateNumberSearchMode(it) },
                tokenLength = tokenLength,
                onTokenLengthChange = { viewModel.updateTokenLength(it) },
                isProcessing = isProcessing
            )

            // 3. Expandable Advanced Endpoints Settings
            AdvancedSettingsCard(
                showAdvancedSettings = showAdvancedSettings,
                onToggle = { showAdvancedSettings = !showAdvancedSettings },
                imageUrlTemplate = imageUrlTemplate,
                postUrl = postUrl,
                keySession = keySession,
                keyCaptcha = keyCaptcha,
                keyToken = keyToken,
                scanDelayMs = scanDelayMs,
                onImageUrlChange = { viewModel.updateImageUrlTemplate(it) },
                onPostUrlChange = { viewModel.updatePostUrl(it) },
                onJsonKeysChange = { s, c, t -> viewModel.updateJsonKeys(s, c, t) },
                onDelayChange = { viewModel.updateScanDelay(it) }
            )

            // 4. Execution Controller & Simulator Testing
            ExecutionCard(
                isProcessing = isProcessing,
                onStart = { viewModel.startScanning() },
                onStop = { viewModel.stopScanning() },
                onReset = { viewModel.resetStats() },
                onInjectSuccess = { viewModel.injectMockSuccess() },
                onInjectUsed = { viewModel.injectMockUsed() },
                onInjectInvalid = { viewModel.injectMockInvalid() },
                onInjectLimited = { viewModel.injectMockLimited() }
            )

            // 5. Statistics Metrics
            StatisticsCard(
                attempted = attemptedCount,
                valid = validCount,
                used = usedCount,
                invalid = invalidCount,
                limited = limitedCount,
                errors = errorCount,
                unknown = unknownCount,
                activeWorkers = activeWorkers,
                foundCount = savedTokens.size
            )

            // 6. Live Crawler Monitor Box (CAPTCHA + OCR + Token)
            LiveMonitorCard(
                isProcessing = isProcessing,
                currentImage = currentChallengeImage,
                extractedText = currentCaptchaText,
                testingToken = currentTestingToken
            )

            // 7. CLI / Logs Console
            LogsConsoleCard(
                logs = logs,
                serverResponseLogs = serverResponseLogs,
                onClear = { viewModel.clearLogs() },
                onClearServerResponses = { viewModel.clearServerResponseLogs() }
            )

            // 8. Saved SQLite Room DB Results List
            DiscoveredTokensCard(
                tokens = savedTokens,
                onDelete = { viewModel.deleteSavedToken(it) },
                onClearAll = { viewModel.clearAllSavedTokens() }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// 1. Header Section Composable
@Composable
fun HeaderSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = "Diagnostic Console",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "TOKEN DISCOVERY HUB",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 1.sp
            )
            Text(
                text = "Client-Server Session Authentication Diagnostic Crawler",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// 2. Gateway URL Entry Card Composable
@Composable
fun GatewayConfigCard(
    gatewayUrl: String,
    sessionId: String,
    workerCount: Int,
    onWorkerCountChange: (Int) -> Unit,
    isProcessing: Boolean,
    onUrlChange: (String) -> Unit,
    onRotateMacAndFetch: () -> Unit,
    onFetchDirectly: () -> Unit
) {
    var workerInputText by remember(workerCount) { mutableStateOf(workerCount.toString()) }
    val parsedInt = workerInputText.filter { it.isDigit() }.toIntOrNull()
    val isError = parsedInt == null || parsedInt !in 1..999

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Session Entrance Configuration",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = gatewayUrl,
                    onValueChange = onUrlChange,
                    label = { Text("Gateway Session URL") },
                    placeholder = { Text("https://example.com/gateway?sessionId=...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(2f)
                        .testTag("gateway_url_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                // Workers input next to the URL input (1-999)
                OutlinedTextField(
                    value = workerInputText,
                    onValueChange = { newValue ->
                        val digitsOnly = newValue.filter { it.isDigit() }
                        if (digitsOnly.length <= 3) {
                            workerInputText = digitsOnly
                            val parsed = digitsOnly.toIntOrNull()
                            if (parsed != null && parsed in 1..999) {
                                onWorkerCountChange(parsed)
                            }
                        }
                    },
                    label = { Text("Workers:") },
                    singleLine = true,
                    isError = isError,
                    enabled = !isProcessing,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("worker_count_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    supportingText = if (isError) {
                        { Text("1-999", color = MaterialTheme.colorScheme.error) }
                    } else null
                )
            }

            // Dual action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Fetch directly (As-Is) button
                Button(
                    onClick = onFetchDirectly,
                    enabled = !isProcessing && gatewayUrl.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fetch (As-Is)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                // MAC Address rotation trigger button
                Button(
                    onClick = onRotateMacAndFetch,
                    enabled = !isProcessing && gatewayUrl.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1.1f)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rotate MAC & Fetch", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            // Dynamic Session ID Badge
            AnimatedVisibility(
                visible = sessionId.isNotBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Session Key",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Active Session ID:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = sessionId,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// 2.5 Token Generation Configuration Card Composable
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TokenGenerationCard(
    tokenMode: TokenMode,
    onTokenModeChange: (TokenMode) -> Unit,
    numberSearchMode: NumberSearchMode,
    onNumberSearchModeChange: (NumberSearchMode) -> Unit,
    tokenLength: Int,
    onTokenLengthChange: (Int) -> Unit,
    isProcessing: Boolean
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Token Generator Configuration",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Mode Selector Row
            Text(
                text = "Generation Mode",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(TokenMode.ALPHANUMERIC, TokenMode.NUMBER, TokenMode.LETTERS).forEach { mode ->
                    val selected = tokenMode == mode
                    val containerColor by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    val contentColor by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(containerColor)
                            .clickable(enabled = !isProcessing) { onTokenModeChange(mode) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = contentColor
                        )
                    }
                }
            }

            // Search strategy selector (Number Mode only)
            AnimatedVisibility(visible = tokenMode == TokenMode.NUMBER) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Number Search Strategy",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(NumberSearchMode.SEQUENTIAL, NumberSearchMode.RANDOM).forEach { mode ->
                            val selected = numberSearchMode == mode
                            val containerColor by animateColorAsState(
                                if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                              )
                            val contentColor by animateColorAsState(
                                if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(containerColor)
                                    .clickable(enabled = !isProcessing) { onNumberSearchModeChange(mode) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = contentColor
                                )
                            }
                        }
                    }
                }
            }

            // Token Length Slider
            Column {
                Text(
                    text = "Token Length: $tokenLength characters",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = tokenLength.toFloat(),
                    onValueChange = { onTokenLengthChange(it.toInt()) },
                    valueRange = 1f..12f,
                    steps = 10,
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// 3. Advanced Configuration Expandable Card
@Composable
fun AdvancedSettingsCard(
    showAdvancedSettings: Boolean,
    onToggle: () -> Unit,
    imageUrlTemplate: String,
    postUrl: String,
    keySession: String,
    keyCaptcha: String,
    keyToken: String,
    scanDelayMs: Long,
    onImageUrlChange: (String) -> Unit,
    onPostUrlChange: (String) -> Unit,
    onJsonKeysChange: (String, String, String) -> Unit,
    onDelayChange: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Column {
            // Header Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Advanced Server Integration Specs",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (showAdvancedSettings) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle Specs",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = showAdvancedSettings,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // Image Challenge URL
                    OutlinedTextField(
                        value = imageUrlTemplate,
                        onValueChange = onImageUrlChange,
                        label = { Text("CAPTCHA Challenge URL Template") },
                        placeholder = { Text("https://example.com/captcha?sessionId={sessionId}") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )
                    Text(
                        text = "Use '{sessionId}' placeholder to insert dynamic session identifier.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )

                    // POST Validation Endpoint
                    OutlinedTextField(
                        value = postUrl,
                        onValueChange = onPostUrlChange,
                        label = { Text("Token POST Endpoint") },
                        placeholder = { Text("https://example.com/api/verify") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )

                    // JSON Key custom overrides
                    Text(
                        text = "JSON Payload Request Schema Configuration",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = keySession,
                            onValueChange = { onJsonKeysChange(it, keyCaptcha, keyToken) },
                            label = { Text("Sess Key") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )
                        OutlinedTextField(
                            value = keyCaptcha,
                            onValueChange = { onJsonKeysChange(keySession, it, keyToken) },
                            label = { Text("Cap Key") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )
                        OutlinedTextField(
                            value = keyToken,
                            onValueChange = { onJsonKeysChange(keySession, keyCaptcha, it) },
                            label = { Text("Tok Key") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )
                    }

                    // Delay Controller
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Crawler Delay Rate: ${scanDelayMs}ms",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Slider(
                        value = scanDelayMs.toFloat(),
                        onValueChange = { onDelayChange(it.toLong()) },
                        valueRange = 500f..5000f,
                        steps = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delay_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }
        }
    }
}

// 4. Execution controller and Simulation Card
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExecutionCard(
    isProcessing: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onInjectSuccess: () -> Unit,
    onInjectUsed: () -> Unit,
    onInjectInvalid: () -> Unit,
    onInjectLimited: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Operational Controller",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Start / Stop operational triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val startBtnBg by animateColorAsState(
                    targetValue = if (isProcessing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                )
                
                Button(
                    onClick = onStart,
                    enabled = !isProcessing,
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp)
                        .testTag("start_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = startBtnBg,
                        contentColor = if (isProcessing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Scanner", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onStop,
                    enabled = isProcessing,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("stop_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isProcessing) Color(0xFFE53935) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isProcessing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop", fontWeight = FontWeight.Bold)
                    }
                }

                IconButton(
                    onClick = onReset,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .testTag("reset_stats_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Stats",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))

            // Simulation Section for Offline testing
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Diagnostics & Offline Simulation Triggers",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = "Simulate API responses offline to verify Room persistence logs & UI counters.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 4
                ) {
                    Button(
                        onClick = onInjectSuccess,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f), contentColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .height(34.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sim Valid", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onInjectUsed,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300).copy(alpha = 0.15f), contentColor = Color(0xFFF57F17)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .height(34.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sim Used", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onInjectInvalid,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935).copy(alpha = 0.15f), contentColor = Color(0xFFC62828)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .height(34.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Error, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sim Invalid", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onInjectLimited,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2).copy(alpha = 0.15f), contentColor = Color(0xFF7B1FA2)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .height(34.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sim Limited", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// 5. Statistics Metrics Panel
@Composable
fun StatisticsCard(
    attempted: Int,
    valid: Int,
    used: Int,
    invalid: Int,
    limited: Int,
    errors: Int,
    unknown: Int,
    activeWorkers: Int,
    foundCount: Int
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Real-Time Diagnostic Metrics",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Top row: Active Workers, Total Checked, Found in DB
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricWidget(
                    title = "Active Workers",
                    value = activeWorkers.toString(),
                    color = Color(0xFF00E5FF),
                    backgroundColor = Color(0xFFE0F7FA),
                    modifier = Modifier.weight(1f)
                )
                MetricWidget(
                    title = "Total Checked",
                    value = attempted.toString(),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                MetricWidget(
                    title = "Found (Saved)",
                    value = foundCount.toString(),
                    color = Color(0xFF2E7D32),
                    backgroundColor = Color(0xFFE8F5E9),
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: Valid, Used, Invalid breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricWidget(
                    title = "Found Codes",
                    value = valid.toString(),
                    color = Color(0xFF2E7D32),
                    backgroundColor = Color(0xFFE8F5E9),
                    modifier = Modifier.weight(1f)
                )
                MetricWidget(
                    title = "Used Codes",
                    value = used.toString(),
                    color = Color(0xFFE65100),
                    backgroundColor = Color(0xFFFFF3E0),
                    modifier = Modifier.weight(1f)
                )
                MetricWidget(
                    title = "Invalid Codes",
                    value = invalid.toString(),
                    color = Color(0xFFC62828),
                    backgroundColor = Color(0xFFFFEBEE),
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 3: Limited, Errors, Unknown breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricWidget(
                    title = "Limited",
                    value = limited.toString(),
                    color = Color(0xFF7B1FA2),
                    backgroundColor = Color(0xFFF3E5F5),
                    modifier = Modifier.weight(1f)
                )
                MetricWidget(
                    title = "Net Errors",
                    value = errors.toString(),
                    color = Color(0xFFD32F2F),
                    backgroundColor = Color(0xFFFFEBEE),
                    modifier = Modifier.weight(1f)
                )
                MetricWidget(
                    title = "Unknown",
                    value = unknown.toString(),
                    color = Color(0xFF455A64),
                    backgroundColor = Color(0xFFECEFF1),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun MetricWidget(
    title: String,
    value: String,
    color: Color,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(color = backgroundColor, shape = RoundedCornerShape(12.dp))
            .border(width = 1.dp, color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// 6. Live monitor Card (CAPTCHA + OCR + Active testing)
@Composable
fun LiveMonitorCard(
    isProcessing: Boolean,
    currentImage: Bitmap?,
    extractedText: String?,
    testingToken: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Live CAPTCHA Feed & OCR Monitor",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (isProcessing) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse"
                    )
                    Box(
                        modifier = Modifier
                            .alpha(alpha)
                            .size(10.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Part: Image Preview Panel
                Box(
                    modifier = Modifier
                        .size(height = 100.dp, width = 150.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentImage != null) {
                        Image(
                            bitmap = currentImage.asImageBitmap(),
                            contentDescription = "Active CAPTCHA Challenge",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Feed Idle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Right Part: Active extraction and Testing info
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Extracted Captcha
                    Column {
                        Text(
                            text = "OCR Read Captcha:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = extractedText ?: "Waiting...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (extractedText != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }

                    // Active Token Submitted
                    Column {
                        Text(
                            text = "Testing Random Token:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = testingToken ?: "Waiting...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (testingToken != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// 7. CLI console terminal log widget with Raw Server Response Tab
@Composable
fun LogsConsoleCard(
    logs: List<LogEntry>,
    serverResponseLogs: List<ServerResponseLog>,
    onClear: () -> Unit,
    onClearServerResponses: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val logsListState = rememberLazyListState()
    val responsesListState = rememberLazyListState()
    
    var selectedTab by remember { mutableStateOf(0) } // 0 = Activity Console, 1 = Raw Server Responses

    // Autoscroll logs to bottom when new logs are added
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty() && selectedTab == 0) {
            logsListState.animateScrollToItem(logs.size - 1)
        }
    }
    LaunchedEffect(serverResponseLogs.size) {
        if (serverResponseLogs.isNotEmpty() && selectedTab == 1) {
            responsesListState.animateScrollToItem(serverResponseLogs.size - 1)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E) // Premium JetBlack Console
        ),
        border = BorderStroke(1.dp, Color(0xFF333333))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Title and Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = Color(0xFF00FF66) // Classic Console Green
                    )
                    Text(
                        text = "Real-Time Activity Console",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (selectedTab == 0) {
                                val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                                val logString = logs.joinToString("\n") { log ->
                                    val formattedTime = formatter.format(Date(log.timestamp))
                                    "[$formattedTime] [${log.type}] ${log.message}"
                                }
                                clipboardManager.setText(AnnotatedString(logString))
                                Toast.makeText(context, "Activity logs copied to clipboard", Toast.LENGTH_SHORT).show()
                            } else {
                                val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                                val logString = serverResponseLogs.joinToString("\n") { log ->
                                    val formattedTime = formatter.format(Date(log.timestamp))
                                    "[$formattedTime] [Worker-${log.workerId}] Token: ${log.token} Status: ${log.status} HTTP: ${log.httpCode}\nResponse: ${log.rawResponse}\n"
                                }
                                clipboardManager.setText(AnnotatedString(logString))
                                Toast.makeText(context, "Server responses copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF2A2A2A), RoundedCornerShape(8.dp))
                            .testTag("copy_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy logs",
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (selectedTab == 0) {
                                onClear()
                            } else {
                                onClearServerResponses()
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF2A2A2A), RoundedCornerShape(8.dp))
                            .testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear logs",
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Tab Selection Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414), RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tab 0 button: Activity Logs
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (selectedTab == 0) Color(0xFF2D2D2D) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Activity Logs (${logs.size})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 0) Color(0xFF00FF66) else Color.Gray
                    )
                }

                // Tab 1 button: Server Responses
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (selectedTab == 1) Color(0xFF2D2D2D) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Server Responses (${serverResponseLogs.size})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 1) Color(0xFF00FF66) else Color.Gray
                    )
                }
            }

            // Scrolling terminal logs
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(Color(0xFF121212), RoundedCornerShape(8.dp))
                    .border(width = 1.dp, color = Color(0xFF252525), shape = RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (selectedTab == 0) {
                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Terminal inactive. Click 'Start' to observe network diagnostic events.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            state = logsListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(logs) { log ->
                                LogItemRow(log = log)
                            }
                        }
                    }
                } else {
                    if (serverResponseLogs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No server responses recorded yet. Activate scanner to display raw server responses.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            state = responsesListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(serverResponseLogs) { responseLog ->
                                ServerResponseLogItemRow(log = responseLog)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServerResponseLogItemRow(log: ServerResponseLog) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val formattedTime = formatter.format(Date(log.timestamp))

    val statusColor = when (log.status) {
        TokenStatus.FOUND -> Color(0xFF00FF66) // Green
        TokenStatus.USED -> Color(0xFFFFA000)  // Amber
        TokenStatus.INVALID -> Color(0xFFFF3D00) // OrangeRed
        TokenStatus.LIMITED -> Color(0xFFD500F9) // Purple / Magenta
        TokenStatus.ERROR -> Color(0xFFFF1744)  // Red
        TokenStatus.UNKNOWN -> Color(0xFFECEFF1) // Whiteish
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161616)
        ),
        border = BorderStroke(1.dp, Color(0xFF2B2B2B)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Worker-${log.workerId}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp
                    )
                }
                
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "${log.status} (HTTP ${log.httpCode})",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TOKEN Tested:",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
                Text(
                    text = "'${log.token}'",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 11.sp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F0F), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "RAW RESPONSE BODY:",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = log.rawResponse.ifBlank { "(Empty Response)" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00FF66),
                    fontSize = 11.sp,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
fun LogItemRow(log: LogEntry) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val formattedTime = formatter.format(Date(log.timestamp))

    val color = when (log.type) {
        LogType.SUCCESS -> Color(0xFF00FF66) // Green
        LogType.OCR -> Color(0xFF00E5FF)    // Cyan
        LogType.WARNING -> Color(0xFFFFA000)  // Amber
        LogType.ERROR -> Color(0xFFFF3D00)    // OrangeRed
        LogType.INFO -> Color(0xFFECEFF1)     // Whiteish
    }

    val typePrefix = when (log.type) {
        LogType.SUCCESS -> "[ OK ]"
        LogType.OCR -> "[OCR ]"
        LogType.WARNING -> "[WARN]"
        LogType.ERROR -> "[ERR ]"
        LogType.INFO -> "[INFO]"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$formattedTime  ",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = Color.Gray,
            fontSize = 11.sp
        )
        Text(
            text = "$typePrefix ",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color,
            fontSize = 11.sp
        )
        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color.copy(alpha = 0.9f),
            fontSize = 11.sp
        )
    }
}

// 8. Saved results SQLite Room Database panel
@Composable
fun DiscoveredTokensCard(
    tokens: List<ValidToken>,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Discovered Tokens (Room SQLite)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (tokens.isNotEmpty()) {
                    IconButton(
                        onClick = onClearAll,
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Room Database",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (tokens.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No valid tokens discovered yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Saved tokens will persist in your on-device Room DB.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tokens) { token ->
                        TokenItemRow(
                            tokenEntity = token,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(token.token))
                                Toast.makeText(context, "Copied token to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = { onDelete(token.token) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TokenItemRow(
    tokenEntity: ValidToken,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss yyyy-MM-dd", Locale.US) }
    val timeString = formatter.format(Date(tokenEntity.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f),
                shape = RoundedCornerShape(10.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = tokenEntity.token,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Session: ${tokenEntity.sessionId} • Captcha: ${tokenEntity.captchaText}",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!tokenEntity.plan.isNullOrBlank() || !tokenEntity.time.isNullOrBlank()) {
                val planText = tokenEntity.plan ?: "Default Plan"
                val timeText = tokenEntity.time ?: "N/A"
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Plan: $planText • Time Remaining: $timeText",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "Logged: $timeString",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Token",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("delete_token_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Token",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
