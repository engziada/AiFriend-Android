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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                AiFriendScreen(viewModel = vm, onMic = ::toggleListening)
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
}

@Composable
private fun AiFriendScreen(viewModel: MainViewModel, onMic: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val voiceError by viewModel.voiceError.collectAsStateWithLifecycle()
    val hasOpenRouterKey by viewModel.hasOpenRouterKey.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF081522), Color(0xFF0C1D2C), Color(0xFF081522)))),
    ) {
        PilotPanel(
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.40f)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.60f)
                .padding(start = 6.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                AiFace(
                    state = state.faceState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f),
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f)
                        .padding(horizontal = 28.dp, vertical = 8.dp),
                ) {
                    val buttonSize = (maxHeight * 0.50f).coerceIn(96.dp, 142.dp)
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ListenButton(
                            state = voiceState,
                            listening = state.isListening,
                            buttonSize = buttonSize,
                            onClick = onMic,
                        )
                        CaptionBar(
                            speaker = if (voiceError != null) null else state.captionSpeaker,
                            text = voiceError ?: state.caption,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 72.dp, max = 96.dp),
                        )
                    }
                }
            }

            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(44.dp),
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "إعدادات آدم",
                    tint = Color.White.copy(alpha = 0.84f),
                )
            }
        }
    }

    if (showSettings) {
        var keyInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSettings = false },
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
                    if (keyInput.isNotBlank()) viewModel.saveOpenRouterKey(keyInput)
                    showSettings = false
                }) { Text("حفظ المفتاح") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (hasOpenRouterKey) viewModel.clearOpenRouterKey()
                    showSettings = false
                }) { Text(if (hasOpenRouterKey) "مسح المفتاح" else "إغلاق") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PilotPanel(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.voiceDownloadProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Auto-scroll to newest log
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2433)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Text(
                text = "سجل التشغيل",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF06131D), RoundedCornerShape(14.dp))
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
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            lineHeight = 14.sp,
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
                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ السجل", tint = Color.White)
                }
                IconButton(onClick = {
                    scope.launch {
                        val uri = PilotLogger.saveAndShare(context)
                        uri?.let { PilotLogger.share(context, it) }
                    }
                }) {
                    Icon(Icons.Default.Share, contentDescription = "مشاركة السجل", tint = Color.White)
                }
                IconButton(onClick = { viewModel.clearLogs() }) {
                    Icon(Icons.Default.Delete, contentDescription = "مسح السجل", tint = Color(0xFFFF8A80))
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFF2A4658))

            Text(
                text = "النماذج الصوتية",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))

            ModelStatusRow(label = "التعرف على الكلام", ready = modelStatus.asrReady)
            ModelStatusRow(label = "توليد الصوت", ready = modelStatus.ttsReady)

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
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { viewModel.downloadVoiceModels() },
                    enabled = voiceState != VoiceManager.VoiceState.DOWNLOADING_MODELS,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176C83)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تحميل", fontSize = 13.sp)
                }
                Button(
                    onClick = { viewModel.rescanVoiceModels() },
                    enabled = voiceState != VoiceManager.VoiceState.DOWNLOADING_MODELS && voiceState != VoiceManager.VoiceState.INITIALIZING,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A5A6B)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إعادة تفحص", fontSize = 13.sp)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFF2A4658))

            Text(
                text = "اختبار يدوي",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TestChip("استمع") { viewModel.beginListening() }
                TestChip("تجربة صوت") { viewModel.testTts() }
                TestChip("تكييف 22°") { viewModel.runManualCommand("خلي التكييف على 22") }
                TestChip("افتح شباك") { viewModel.runManualCommand("افتح شباك السائق للتهوية") }
                TestChip("اقفل الأبواب") { viewModel.runManualCommand("اقفل الأبواب") }
                TestChip("اقرأ البطارية") { viewModel.runManualCommand("كام في المية") }
                TestChip(" reconnect") { viewModel.reconnectByd() }
            }
        }
    }
}

@Composable
private fun ModelStatusRow(label: String, ready: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = Color(0xFFB6C9CE), fontSize = 14.sp)
        Text(
            text = if (ready) "جاهز" else "غير موجود",
            color = if (ready) Color(0xFF6CE0D1) else Color(0xFFFF8A80),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TestChip(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C3A4A)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text = label, fontSize = 12.sp, color = Color.White)
    }
}

@Composable
private fun ListenButton(
    state: VoiceManager.VoiceState,
    listening: Boolean,
    buttonSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val enabled = state in setOf(
        VoiceManager.VoiceState.READY,
        VoiceManager.VoiceState.ERROR,
        VoiceManager.VoiceState.LISTENING,
    )
    val label = when (state) {
        VoiceManager.VoiceState.DOWNLOADING_MODELS -> "تحميل الصوت"
        VoiceManager.VoiceState.INITIALIZING -> "جاري التجهيز"
        VoiceManager.VoiceState.LISTENING -> "إيقاف الاستماع"
        VoiceManager.VoiceState.PROCESSING -> "لحظة واحدة"
        VoiceManager.VoiceState.SPEAKING -> "المساعد يتكلم"
        VoiceManager.VoiceState.ERROR -> "إعادة المحاولة"
        else -> "ابدأ الاستماع"
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(buttonSize),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (listening) Color(0xFFFF846F) else Color(0xFF6CE0D1),
            contentColor = Color(0xFF09202B),
            disabledContainerColor = Color(0xFF315462),
            disabledContentColor = Color(0xFFB6C9CE),
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (listening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
            )
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CaptionBar(speaker: CaptionSpeaker?, text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFF132A3A),
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
                    null -> "تنبيه صوتي"
                },
                color = if (speaker == CaptionSpeaker.YOU) Color(0xFF7CE4D7) else Color(0xFF9FCBE0),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                color = Color(0xFFF3F8FA),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
