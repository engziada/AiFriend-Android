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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ziaada.aifriend.ui.MainViewModel
import com.ziaada.aifriend.ui.face.AiFace
import com.ziaada.aifriend.ui.theme.AiFriendTheme
import com.ziaada.aifriend.voice.VoiceManager
import com.ziaada.aifriend.LogManager
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontFamily

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
fun SimpleTestButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ===== Simple Pilot Test Panel =====
@Composable
fun PilotTestPanel(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val logsText by remember { mutableStateOf(LogManager.getLogs().reversed().joinToString("\n")) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .width(360.dp)
            .background(Color(0xFF0A1A2A))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)),
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🛠 Pilot Test Panel", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

            // --- Log Window ---
            Text("📋 Verbose Logs", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7CE4D7))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Logs: ${LogManager.getLogs().size}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(Color(0xFF061622))
                    .padding(8.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = LogManager.getLogs().reversed().joinToString("\n"),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color.White,
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

            // --- Missing Models Download ---
            Text("📥 تنزيل النماذج المفقودة", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
            
            Button(
                onClick = { 
                    LogManager.log("PILOT", "Download missing models clicked")
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F), contentColor = Color.Black),
            ) {
                Text("تنزيل النماذج المفقودة", fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

            // --- Manual Test Buttons ---
            Text("🔧 أزرار اختبار يدوية", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7CE4D7))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🌡️ التكييف", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SimpleTestButton("تشغيل", Color(0xFF4CAF50)) { LogManager.log("PILOT", "Test: Climate ON") }
                    SimpleTestButton("إيقاف", Color(0xFFF44336)) { LogManager.log("PILOT", "Test: Climate OFF") }
                    SimpleTestButton("أوتو", Color(0xFF2196F3)) { LogManager.log("PILOT", "Test: Climate Auto") }
                    SimpleTestButton("22°", Color(0xFF9C27B0)) { LogManager.log("PILOT", "Test: Climate 22°C") }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SimpleTestButton("تدوير", Color(0xFF00BCD4)) { LogManager.log("PILOT", "Test: Climate Recirc") }
                    SimpleTestButton("إزالة ضباب", Color(0xFF673AB7)) { LogManager.log("PILOT", "Test: Defrost") }
                    SimpleTestButton("مروحة 3", Color(0xFF795548)) { LogManager.log("PILOT", "Test: Fan 3") }
                }

                Text("🪟 الشبابيك", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SimpleTestButton("كل افتح", Color(0xFF4CAF50)) { LogManager.log("PILOT", "Test: Windows All Open") }
                    SimpleTestButton("كل قفل", Color(0xFFF44336)) { LogManager.log("PILOT", "Test: Windows All Close") }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SimpleTestButton("سائق", Color(0xFF4CAF50)) { LogManager.log("PILOT", "Test: Window Driver") }
                    SimpleTestButton("راكب", Color(0xFF4CAF50)) { LogManager.log("PILOT", "Test: Window Passenger") }
                    SimpleTestButton("خلف يمين", Color(0xFF4CAF50)) { LogManager.log("PILOT", "Test: Window Rear Right") }
                    SimpleTestButton("خلف يسار", Color(0xFF4CAF50)) { LogManager.log("PILOT", "Test: Window Rear Left") }
                }

                Text("☀️ السقف", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SimpleTestButton("افتح", Color(0xFF4CAF50)) { LogManager.log("PILOT", "Test: Sunroof Open") }
                    SimpleTestButton("قفل", Color(0xFFF44336)) { LogManager.log("PILOT", "Test: Sunroof Close") }
                    SimpleTestButton("ميل", Color(0xFFFF9800)) { LogManager.log("PILOT", "Test: Sunroof Tilt") }
                    SimpleTestButton("ستارة", Color(0xFF607D8B)) { LogManager.log("PILOT", "Test: Sunshade") }
                }

                Text("🔐 الأبواب", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SimpleTestButton("قفل الكل", Color(0xFFF44336)) { LogManager.log("PILOT", "Test: Doors Lock") }
                    SimpleTestButton("فتح الكل", Color(0xFF4CAF50)) { LogManager.log("PILOT", "Test: Doors Unlock") }
                    SimpleTestButton("شنطة أمامية", Color(0xFF795548)) { LogManager.log("PILOT", "Test: Frunk") }
                }

                Text("📊 استعلامات", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SimpleTestButton("SOC", Color(0xFF2196F3)) { LogManager.log("PILOT", "Test: SOC Query") }
                    SimpleTestButton("المدى", Color(0xFF00BCD4)) { LogManager.log("PILOT", "Test: Range Query") }
                    SimpleTestButton("حرارة", Color(0xFFFF9800)) { LogManager.log("PILOT", "Test: Temp Query") }
                    SimpleTestButton("ضغط إطارات", Color(0xFF607D8B)) { LogManager.log("PILOT", "Test: TPMS Query") }
                }

                Text("🎤 الصوت", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SimpleTestButton("اختبار ASR", Color(0xFF9C27B0)) { LogManager.log("PILOT", "Test: ASR Test") }
                    SimpleTestButton("اختبار TTS", Color(0xFFE91E63)) { LogManager.log("PILOT", "Test: TTS Test") }
                    SimpleTestButton("测试 VAD", Color(0xFF00BCD4)) { LogManager.log("PILOT", "Test: VAD Test") }
                }
            }
        }
    }
}

@Composable
fun AiFriendScreen(viewModel: MainViewModel, onMic: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val voiceDownloadProgress by viewModel.voiceDownloadProgress.collectAsStateWithLifecycle()
    val hasOpenRouterKey by viewModel.hasOpenRouterKey.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF081522), Color(0xFF0C1D2C), Color(0xFF081522)))),
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
                    .padding(horizontal = 40.dp, vertical = 12.dp),
            ) {
                val buttonSize = (maxHeight * 0.52f).coerceIn(108.dp, 158.dp)
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    ListenButton(
                        voiceState = voiceState,
                        listening = state.isListening,
                        buttonSize = buttonSize,
                        onClick = onMic,
                    )
                    StatusBar(
                        state = state,
                        voiceState = voiceState,
                        voiceDownloadProgress = voiceDownloadProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                    )
                }
            }
        }

        IconButton(
            onClick = { showSettings = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .size(48.dp),
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "إعدادات آدم",
                tint = Color.White.copy(alpha = 0.84f),
            )
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

@Composable
fun ListenButton(
    voiceState: VoiceManager.VoiceState,
    listening: Boolean,
    buttonSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val enabled = voiceState in setOf(
        VoiceManager.VoiceState.READY,
        VoiceManager.VoiceState.ERROR,
        VoiceManager.VoiceState.LISTENING,
    )
    val label = when (voiceState) {
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
fun StatusBar(
    state: com.ziaada.aifriend.domain.AiFriendUiState,
    voiceState: VoiceManager.VoiceState,
    voiceDownloadProgress: VoiceManager.DownloadProgress,
    modifier: Modifier = Modifier,
) {
    val voiceLabel = when (voiceState) {
        VoiceManager.VoiceState.DOWNLOADING_MODELS -> "جاري تحميل نماذج الصوت... ${if (voiceDownloadProgress.total > 0) "${(voiceDownloadProgress.current * 100 / voiceDownloadProgress.total)}%" else ""}"
        VoiceManager.VoiceState.INITIALIZING -> "تهيئة المحرك الصوتي..."
        VoiceManager.VoiceState.ERROR -> "خطأ في الصوت: ${state.vehicleMessage}"
        else -> state.vehicleMessage
    }

    Surface(
        modifier = modifier
            .background(Color(0xFF132A3A)),
        shape = RoundedCornerShape(26.dp),
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = voiceLabel,
                color = Color(0xFFF3F8FA),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}