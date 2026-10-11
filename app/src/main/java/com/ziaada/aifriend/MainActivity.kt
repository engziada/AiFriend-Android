package com.ziaada.aifriend

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ziaada.aifriend.byd.BydConnectionState
import com.ziaada.aifriend.domain.CaptionSpeaker
import com.ziaada.aifriend.pilot.PilotLogger
import com.ziaada.aifriend.ui.MainViewModel
import com.ziaada.aifriend.ui.face.AiFace
import com.ziaada.aifriend.ui.theme.AiFriendTheme
import com.ziaada.aifriend.voice.ModelManager
import com.ziaada.aifriend.voice.VoiceManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val requestAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.beginListening()
    }
    private lateinit var insetsController: WindowInsetsControllerCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hideSystemBars()
        setContent {
            AiFriendTheme {
                AiFriendScreen(viewModel = vm, onMic = ::toggleListening, onStop = ::stopAll)
            }
        }
    }

    private fun hideSystemBars() = insetsController.hide(WindowInsets.Type.systemBars())

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun toggleListening() {
        if (vm.state.value.isListening) {
            vm.stopListening()
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.beginListening()
        } else {
            requestAudio.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun stopAll() {
        if (vm.state.value.isListening) vm.stopListening()
        else if (vm.state.value.isSpeaking) vm.stopSpeaking()
        else vm.resetToIdle()
    }
}

@Composable
private fun AiFriendScreen(viewModel: MainViewModel, onMic: () -> Unit, onStop: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val voiceError by viewModel.voiceError.collectAsStateWithLifecycle()
    val bydState by viewModel.bydState.collectAsStateWithLifecycle()
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    val hasOpenRouterKey by viewModel.hasOpenRouterKey.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showPanel by remember { mutableStateOf(false) }
    var highContrast by remember { mutableStateOf(false) }

    val bgGradient = if (highContrast) {
        Brush.verticalGradient(listOf(Color(0xFF0C2E42), Color(0xFF144058), Color(0xFF0C2E42)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF081522), Color(0xFF0C1D2C), Color(0xFF081522)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusBar(
                bydState = bydState,
                voiceState = voiceState,
                modelStatus = modelStatus,
                highContrast = highContrast,
                onTogglePanel = { showPanel = !showPanel },
                onToggleContrast = { highContrast = !highContrast },
                onShowSettings = { showSettings = true },
            )

            AiFace(
                state = state.faceState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.2f)
                    .padding(vertical = 8.dp),
            )

            BottomSection(
                viewModel = viewModel,
                state = state,
                voiceState = voiceState,
                voiceError = voiceError,
                highContrast = highContrast,
                onMic = onMic,
                onStop = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.9f),
            )
        }

        AnimatedVisibility(
            visible = showPanel,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            PilotPanel(
                viewModel = viewModel,
                highContrast = highContrast,
                onClosePanel = { showPanel = false },
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.48f),
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            hasOpenRouterKey = hasOpenRouterKey,
            onSaveKey = { viewModel.saveOpenRouterKey(it) },
            onClearKey = { viewModel.clearOpenRouterKey() },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun BottomSection(
    viewModel: MainViewModel,
    state: com.ziaada.aifriend.domain.AiFriendUiState,
    voiceState: VoiceManager.VoiceState,
    voiceError: String?,
    highContrast: Boolean,
    onMic: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val captionBg = if (highContrast) Color(0xFF1A3E55) else Color(0xFF132A3A)
    val captionTextSize = if (highContrast) 23.sp else 21.sp
    val msgTextSize = if (highContrast) 16.sp else 14.sp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val buttonSize = (maxWidth * 0.28f).coerceIn(120.dp, 190.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                ListenButton(
                    state = voiceState,
                    listening = state.isListening,
                    buttonSize = buttonSize,
                    highContrast = highContrast,
                    onClick = onMic,
                )
                Spacer(Modifier.width(28.dp))
                StopButton(
                    buttonSize = buttonSize * 0.55f,
                    highContrast = highContrast,
                    onClick = onStop,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CaptionBar(
                speaker = if (voiceError != null) null else state.captionSpeaker,
                text = voiceError ?: state.caption,
                background = captionBg,
                textSize = captionTextSize,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp, max = 110.dp),
            )
            if (state.vehicleMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (highContrast) Color(0xFF0F3142) else Color(0xFF0B1F2A),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 6.dp,
                ) {
                    Text(
                        text = state.vehicleMessage,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        color = if (highContrast) Color(0xFFFFF59D) else Color(0xFFB6C9CE),
                        fontSize = msgTextSize,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }

        QuickChipsRow(viewModel = viewModel, highContrast = highContrast)
    }
}

@Composable
private fun StatusBar(
    bydState: BydConnectionState,
    voiceState: VoiceManager.VoiceState,
    modelStatus: ModelManager.ModelStatus,
    highContrast: Boolean,
    onTogglePanel: () -> Unit,
    onToggleContrast: () -> Unit,
    onShowSettings: () -> Unit,
) {
    val bydColor = when (bydState) {
        BydConnectionState.READY -> Color(0xFF6CE0D1)
        BydConnectionState.DEGRADED -> Color(0xFFFFE082)
        BydConnectionState.ERROR -> Color(0xFFFF8A80)
        else -> Color(0xFFB6C9CE)
    }
    val voiceColor = when (voiceState) {
        VoiceManager.VoiceState.READY,
        VoiceManager.VoiceState.LISTENING,
        VoiceManager.VoiceState.SPEAKING -> Color(0xFF6CE0D1)
        VoiceManager.VoiceState.ERROR -> Color(0xFFFF8A80)
        VoiceManager.VoiceState.DOWNLOADING_MODELS,
        VoiceManager.VoiceState.INITIALIZING,
        VoiceManager.VoiceState.PROCESSING -> Color(0xFFFFE082)
        else -> Color(0xFFB6C9CE)
    }
    val bydText = when (bydState) {
        BydConnectionState.READY -> "متصل"
        BydConnectionState.DEGRADED -> "مقيد"
        BydConnectionState.DISCONNECTED -> "غير متصل"
        else -> "جاري..."
    }
    val voiceText = when (voiceState) {
        VoiceManager.VoiceState.READY -> "جاهز"
        VoiceManager.VoiceState.LISTENING -> "بيسمع"
        VoiceManager.VoiceState.SPEAKING -> "بيتكلم"
        VoiceManager.VoiceState.DOWNLOADING_MODELS -> "تحميل"
        VoiceManager.VoiceState.INITIALIZING -> "تجهيز"
        VoiceManager.VoiceState.PROCESSING -> "معالجة"
        VoiceManager.VoiceState.ERROR -> "خطأ"
        else -> "انتظار"
    }
    val chipBg = if (highContrast) Color(0xFF1A3E55) else Color(0xFF132A3A)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        color = chipBg,
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(text = "سيارة: $bydText", color = bydColor, highContrast = highContrast)
                StatusChip(text = "صوت: $voiceText", color = voiceColor, highContrast = highContrast)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("نماذج: ", color = Color(0xFFB6C9CE), fontSize = 14.sp)
                    IndicatorDot(ready = modelStatus.asrReady, label = "تعرف", highContrast = highContrast)
                    Spacer(Modifier.width(6.dp))
                    IndicatorDot(ready = modelStatus.ttsReady, label = "كلام", highContrast = highContrast)
                }
            }

            Row {
                IconButton(onClick = onToggleContrast, modifier = Modifier.size(42.dp)) {
                    Icon(
                        Icons.Default.Brightness7,
                        contentDescription = "تباين",
                        tint = if (highContrast) Color(0xFFFFF59D) else Color.White.copy(alpha = 0.85f),
                    )
                }
                IconButton(onClick = onTogglePanel, modifier = Modifier.size(42.dp)) {
                    Icon(
                        if (onTogglePanel.hashCode() == 0) Icons.Default.Close else Icons.Default.Menu,
                        contentDescription = "سجل",
                        tint = Color.White.copy(alpha = 0.85f),
                    )
                }
                IconButton(onClick = onShowSettings, modifier = Modifier.size(42.dp)) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "إعدادات",
                        tint = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color, highContrast: Boolean) {
    Surface(
        color = color.copy(alpha = if (highContrast) 0.22f else 0.16f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = color,
            fontSize = if (highContrast) 15.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun IndicatorDot(ready: Boolean, label: String, highContrast: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = if (ready) Color(0xFF6CE0D1) else Color(0xFFFF8A80),
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            color = if (ready) Color(0xFF6CE0D1) else Color(0xFFFF8A80),
            fontSize = if (highContrast) 13.sp else 12.sp,
        )
    }
}

@Composable
private fun QuickChipsRow(viewModel: MainViewModel, highContrast: Boolean) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickChip("استمع", highContrast) { viewModel.beginListening() }
        QuickChip("تكييف 22°", highContrast) { viewModel.directClimate22() }
        QuickChip("شباك تهوية", highContrast) { viewModel.directWindowVent() }
        QuickChip("قفل", highContrast) { viewModel.directLockDoors() }
        QuickChip("فتح قفل", highContrast) { viewModel.directUnlockDoors() }
        QuickChip("البطارية", highContrast) { viewModel.directReadSoc() }
        QuickChip("ADB test", highContrast) { viewModel.directAdbShellTest() }
        QuickChip("Reconnect", highContrast) { viewModel.reconnectByd() }
    }
}

@Composable
private fun QuickChip(label: String, highContrast: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C3A4A)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = label, fontSize = if (highContrast) 15.sp else 14.sp, color = Color.White)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PilotPanel(
    viewModel: MainViewModel,
    highContrast: Boolean,
    onClosePanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.voiceDownloadProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val cardBg = if (highContrast) Color(0xFF164059) else Color(0xFF0F2433)
    val logBg = if (highContrast) Color(0xFF0B2535) else Color(0xFF06131D)

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Card(
        modifier = modifier.padding(start = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "سجل التشغيل",
                    color = Color.White,
                    fontSize = if (highContrast) 20.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onClosePanel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White.copy(alpha = 0.8f))
                }
            }
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(logBg, RoundedCornerShape(14.dp))
                    .padding(8.dp),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(logs, key = { it.hashCode() }) { line ->
                        val color = when (line.level) {
                            PilotLogger.Level.E -> Color(0xFFFF8A80)
                            PilotLogger.Level.W -> Color(0xFFFFE082)
                            PilotLogger.Level.I -> Color(0xFF8BCCEB)
                            PilotLogger.Level.D -> Color(0xFFB0BEC5)
                        }
                        Text(
                            text = "[${line.time}] ${line.level.name}/${line.tag}: ${line.message}",
                            color = color,
                            fontSize = if (highContrast) 12.sp else 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = if (highContrast) 16.sp else 14.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = {
                    PilotLogger.copyToClipboard(context)
                    clipboard.setText(AnnotatedString("copied"))
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = Color.White)
                }
                IconButton(onClick = {
                    scope.launch {
                        val uri = PilotLogger.saveAndShare(context)
                        uri?.let { PilotLogger.share(context, it) }
                    }
                }) {
                    Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = Color.White)
                }
                IconButton(onClick = { viewModel.clearLogs() }) {
                    Icon(Icons.Default.Delete, contentDescription = "مسح", tint = Color(0xFFFF8A80))
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFF2A4658))

            Text(
                text = "النماذج الصوتية",
                color = Color.White,
                fontSize = if (highContrast) 18.sp else 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))

            ModelStatusRow(label = "التعرف على الكلام", ready = modelStatus.asrReady, highContrast = highContrast)
            ModelStatusRow(label = "توليد الصوت", ready = modelStatus.ttsReady, highContrast = highContrast)

            if (voiceState == VoiceManager.VoiceState.DOWNLOADING_MODELS) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (downloadProgress.total > 0) downloadProgress.current.toFloat() / downloadProgress.total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF6CE0D1),
                    trackColor = Color(0xFF2A4658),
                )
                Text(
                    text = "${downloadProgress.modelName} ${downloadProgress.current / 1024 / 1024}MB / ${downloadProgress.total / 1024 / 1024}MB",
                    color = Color(0xFFB6C9CE),
                    fontSize = if (highContrast) 13.sp else 12.sp,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.downloadVoiceModels() },
                    enabled = voiceState != VoiceManager.VoiceState.DOWNLOADING_MODELS,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176C83)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("تحميل", fontSize = if (highContrast) 15.sp else 14.sp)
                }
                Button(
                    onClick = { viewModel.rescanVoiceModels() },
                    enabled = voiceState != VoiceManager.VoiceState.DOWNLOADING_MODELS && voiceState != VoiceManager.VoiceState.INITIALIZING,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A5A6B)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("إعادة تفحص", fontSize = if (highContrast) 15.sp else 14.sp)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFF2A4658))

            Text(
                text = "اختبار يدوي",
                color = Color.White,
                fontSize = if (highContrast) 18.sp else 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TestChip("استمع", highContrast) { viewModel.beginListening() }
                TestChip("تجربة صوت", highContrast) { viewModel.testTts() }
                TestChip("تكييف 22°", highContrast) { viewModel.runManualCommand("خلي التكييف على 22") }
                TestChip("افتح شباك", highContrast) { viewModel.runManualCommand("افتح شباك السائق للتهوية") }
                TestChip("اقفل الأبواب", highContrast) { viewModel.runManualCommand("اقفل الأبواب") }
                TestChip("اقرأ البطارية", highContrast) { viewModel.runManualCommand("كام في المية") }
                TestChip("Reconnect", highContrast) { viewModel.reconnectByd() }
            }
        }
    }
}

@Composable
private fun ModelStatusRow(label: String, ready: Boolean, highContrast: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = Color(0xFFB6C9CE), fontSize = if (highContrast) 15.sp else 14.sp)
        Text(
            text = if (ready) "جاهز" else "غير موجود",
            color = if (ready) Color(0xFF6CE0D1) else Color(0xFFFF8A80),
            fontSize = if (highContrast) 15.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TestChip(label: String, highContrast: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C3A4A)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text = label, fontSize = if (highContrast) 14.sp else 13.sp, color = Color.White)
    }
}

@Composable
private fun ListenButton(
    state: VoiceManager.VoiceState,
    listening: Boolean,
    buttonSize: androidx.compose.ui.unit.Dp,
    highContrast: Boolean,
    onClick: () -> Unit,
) {
    val enabled = state in setOf(
        VoiceManager.VoiceState.READY,
        VoiceManager.VoiceState.ERROR,
        VoiceManager.VoiceState.LISTENING,
    )
    val label = when (state) {
        VoiceManager.VoiceState.DOWNLOADING_MODELS -> "تحميل"
        VoiceManager.VoiceState.INITIALIZING -> "تجهيز"
        VoiceManager.VoiceState.LISTENING -> "إيقاف"
        VoiceManager.VoiceState.PROCESSING -> "لحظة"
        VoiceManager.VoiceState.SPEAKING -> "بيتكلم"
        VoiceManager.VoiceState.ERROR -> "إعادة"
        else -> "استمع"
    }
    val bg = when {
        listening -> Color(0xFFFF846F)
        highContrast -> Color(0xFF7DFAE6)
        else -> Color(0xFF6CE0D1)
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(buttonSize),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = Color(0xFF09202B),
            disabledContainerColor = Color(0xFF315462),
            disabledContentColor = Color(0xFFB6C9CE),
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (listening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(if (highContrast) 52.dp else 48.dp),
            )
            Text(
                text = label,
                fontSize = if (highContrast) 22.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StopButton(
    buttonSize: androidx.compose.ui.unit.Dp,
    highContrast: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(buttonSize),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (highContrast) Color(0xFFFFB3A7) else Color(0xFFFF846F),
            contentColor = Color(0xFF09202B),
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(if (highContrast) 32.dp else 28.dp))
            Text("إلغاء", fontSize = if (highContrast) 16.sp else 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CaptionBar(
    speaker: CaptionSpeaker?,
    text: String,
    background: Color,
    textSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = background,
        contentColor = Color.White,
        shape = RoundedCornerShape(26.dp),
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = when (speaker) {
                    CaptionSpeaker.YOU -> "أنت قلت"
                    CaptionSpeaker.ASSISTANT -> "آدم قال"
                    null -> "تنبيه"
                },
                color = if (speaker == CaptionSpeaker.YOU) Color(0xFF7CE4D7) else Color(0xFF9FCBE0),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                color = Color(0xFFF3F8FA),
                fontSize = textSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun SettingsDialog(
    hasOpenRouterKey: Boolean,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var keyInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات آدم") },
        text = {
            Column {
                Text("أوامر السيارة وفتح التطبيقات المعروفة تعمل محلياً. الطلبات التي تحتاج فهماً أوسع والأسئلة العامة تُرسل إلى OpenRouter، وتحتاج مفتاحاً واتصالاً بالإنترنت.")
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = { Text("OpenRouter API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Text(
                    text = if (hasOpenRouterKey) "مفتاح محفوظ على هذا الجهاز" else "لم يتم إعداد المفتاح بعد",
                    modifier = Modifier.padding(top = 8.dp),
                    color = Color(0xFF526A79),
                    fontSize = 14.sp,
                )
                Text(
                    text = "إنشاء مفتاح OpenRouter",
                    modifier = Modifier.padding(top = 6.dp).clickable { uriHandler.openUri("https://openrouter.ai/keys") },
                    color = Color(0xFF176C83),
                    fontSize = 14.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (keyInput.isNotBlank()) onSaveKey(keyInput)
                onDismiss()
            }) { Text("حفظ المفتاح") }
        },
        dismissButton = {
            TextButton(onClick = {
                if (hasOpenRouterKey) onClearKey()
                onDismiss()
            }) { Text(if (hasOpenRouterKey) "مسح المفتاح" else "إغلاق") }
        },
    )
}
