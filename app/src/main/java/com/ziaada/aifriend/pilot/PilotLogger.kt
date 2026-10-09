package com.ziaada.aifriend.pilot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-app pilot test log buffer with copy/share support. */
object PilotLogger {
    private const val MAX_LINES = 500
    private const val TAG = "PilotLogger"

    enum class Level { D, I, W, E }

    data class LogLine(
        val time: String,
        val level: Level,
        val tag: String,
        val message: String,
    )

    private val _logs = MutableStateFlow<List<LogLine>>(emptyList())
    val logs = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    @Synchronized
    private fun append(level: Level, tag: String, message: String) {
        val line = LogLine(time = timeFormat.format(Date()), level = level, tag = tag, message = message)
        val current = _logs.value.toMutableList().apply { add(line) }
        while (current.size > MAX_LINES) current.removeAt(0)
        _logs.value = current
        // Also mirror to Android logcat so logcat captures it even if the UI is hidden
        val formatted = "[${line.time}] ${level.name}/$tag: $message"
        when (level) {
            Level.D -> Log.d(tag, message)
            Level.I -> Log.i(tag, message)
            Level.W -> Log.w(tag, message)
            Level.E -> Log.e(tag, message)
        }
    }

    fun d(tag: String, message: String) = append(Level.D, tag, message)
    fun i(tag: String, message: String) = append(Level.I, tag, message)
    fun w(tag: String, message: String) = append(Level.W, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val full = if (throwable == null) message else "$message\n${throwable.stackTraceToString()}"
        append(Level.E, tag, full)
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun toText(): String = _logs.value.joinToString(separator = "\n") {
        "[${it.time}] ${it.level.name}/${it.tag}: ${it.message}"
    }

    /** Copies logs to the system clipboard. */
    fun copyToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AiFriend logs", toText()))
    }

    /** Writes logs to external files dir and returns a shareable URI. */
    suspend fun saveAndShare(context: Context): Uri? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "aifriend_logs_$stamp.txt")
            file.writeText(toText())
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e(TAG, "Failed to save logs", e)
            null
        }
    }

    /** Opens a share sheet for the saved log file. */
    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share AiFriend logs")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
