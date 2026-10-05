package com.ziaada.aifriend

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaada.aifriend.byd.BydConnectionState
import com.ziaada.aifriend.domain.FaceState
import com.ziaada.aifriend.ui.MainViewModel
import com.ziaada.aifriend.ui.face.AiFace
import com.ziaada.aifriend.ui.theme.AiFriendTheme
import com.ziaada.aifriend.voice.VoiceManager

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val requestAudio = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) vm.beginListening()
    }

    private lateinit var insetsController: WindowInsetsControllerCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupEdgeToEdge()

        setContent {
            AiFriendTheme {
                AiFriendScreen(
                    viewModel = vm,
                    onMic = ::toggleListening,
                )
            }
        }
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hideSystemBars()
    }

    private fun hideSystemBars() {
        insetsController.hide(WindowInsets.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun toggleListening() {
        val state = vm.state.value
        if (state.isListening) {
            vm.stopListening()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            vm.beginListening()
        } else {
            requestAudio.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
private fun AiFriendScreen(
    viewModel: MainViewModel = viewModel(),
    onMic: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bydState by viewModel.bydState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.voiceDownloadProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF021733), Color(0xFF032858), Color(0xFF03152C)),
                ),
            ),
    ) {
        Header()

        AiFace(
            state = state.faceState,
            modifier = Modifier.fillMaxSize(),
        )

        val message = when (state.faceState) {
            FaceState.LISTENING -> "جاري الاستماع..."
            FaceState.THINKING -> "ثانية بفكر..."
            FaceState.SPEAKING -> state.vehicleMessage
            else -> state.vehicleMessage
        }
        StatusChip(
            text = message,
            isActive = state.isListening,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 86.dp),
        )

        // BYD Connection status indicator
        ConnectionIndicator(
            state = bydState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 86.dp, end = 26.dp),
        )

        // Voice download/initialization progress
        when (voiceState) {
            VoiceManager.VoiceState.DOWNLOADING_MODELS -> VoiceProgressIndicator(
                progress = downloadProgress,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 150.dp),
            )
            VoiceManager.VoiceState.INITIALIZING -> VoiceProgressIndicator(
                progress = VoiceManager.DownloadProgress(0, 1, "تهيئة المحرك الصوتي..."),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 150.dp),
            )
            else -> Unit
        }

        if (state.transcript.isNotBlank()) {
            TranscriptBubble(
                text = state.transcript,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 250.dp)
                    .fillMaxWidth(0.55f),
            )
        }

        MicButton(
            active = state.isListening,
            onClick = onMic,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
        )

        BottomBar(
            climate = state.climateSetpointC,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("12:45", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text("BYD  •  AI Friend", color = Color.White.copy(alpha = 0.88f), fontSize = 18.sp)
    }
}

@Composable
private fun StatusChip(text: String, isActive: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0xFF062B57).copy(alpha = 0.9f), RoundedCornerShape(28.dp))
            .shadow(12.dp, RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Mic, contentDescription = null, tint = if (isActive) Color(0xFF31D5FF) else Color.White)
        Spacer(Modifier.size(10.dp))
        Text(text, color = Color.White, fontSize = 20.sp)
    }
}

@Composable
private fun TranscriptBubble(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF0A3E78).copy(alpha = 0.76f), RoundedCornerShape(28.dp))
            .padding(horizontal = 28.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MicButton(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(94.dp)
            .background(
                if (active) Color(0xFF007BC2) else Color(0xFF0C3F76),
                RoundedCornerShape(50),
            ),
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = "تحدث",
            tint = Color.White,
            modifier = Modifier.size(44.dp),
        )
    }
}

@Composable
private fun BottomBar(climate: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp)
            .background(Color(0xFF03172F).copy(alpha = 0.96f))
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(Icons.Default.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        Icon(Icons.Default.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        Text("${climate}°C  •  AUTO", color = Color.White, fontSize = 21.sp)
        Text("Climate", color = Color(0xFF6CE0FF), fontSize = 19.sp)
    }
}

@Composable
private fun ConnectionIndicator(
    state: BydConnectionState,
    modifier: Modifier = Modifier,
) {
    val (color, text) = when (state) {
        BydConnectionState.READY -> Color(0xFF31D5FF) to "متصل"
        BydConnectionState.DEGRADED -> Color(0xFFFFAA00) to "مقيد"
        BydConnectionState.FID_SYNCING -> Color(0xFF31D5FF) to "مزامنة..."
        BydConnectionState.DAEMON_STARTING -> Color(0xFF31D5FF) to "جاري البدء..."
        BydConnectionState.ADB_AUTHENTICATING -> Color(0xFF31D5FF) to "مصادقة..."
        BydConnectionState.ADB_CONNECTING -> Color(0xFF31D5FF) to "اتصال..."
        else -> Color(0xFFFF4444) to "غير متصل"
    }

    Box(
        modifier = modifier
            .background(Color(0xFF062B57).copy(alpha = 0.9f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.padding(start = 6.dp))
            Text(text, color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
private fun VoiceProgressIndicator(
    progress: VoiceManager.DownloadProgress,
    modifier: Modifier = Modifier,
) {
    val percent = if (progress.total > 0) progress.current.toFloat() / progress.total else 0f

    Box(
        modifier = modifier
            .background(Color(0xFF062B57).copy(alpha = 0.95f), RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .fillMaxWidth(0.8f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFF31D5FF), RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = progress.modelName,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color(0xFF021733), RoundedCornerShape(4.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(percent)
                        .height(8.dp)
                        .background(Color(0xFF31D5FF), RoundedCornerShape(4.dp))
                        .animateContentSize(animationSpec = tween(300)),
                )
            }

            Text(
                text = "${(percent * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
            )
        }
    }
}
