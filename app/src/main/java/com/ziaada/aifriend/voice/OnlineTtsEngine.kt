package com.ziaada.aifriend.voice

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import com.ziaada.aifriend.pilot.PilotLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Online Arabic TTS via Google Translate; much more natural than local Piper. */
class OnlineTtsEngine {
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var stopped = false

    suspend fun speak(text: String): Boolean = withContext(Dispatchers.IO) {
        stopped = false
        try {
            val chunks = chunk(text)
            for ((index, chunk) in chunks.withIndex()) {
                if (stopped) return@withContext false
                PilotLogger.d(TAG, "Online TTS chunk ${index + 1}/${chunks.size}: '${chunk.take(50)}…'")
                val audio = download(chunk)
                if (audio.isEmpty()) error("Empty audio for chunk ${index + 1}")
                playBlocking(audio)
            }
            true
        } catch (e: Exception) {
            if (stopped) PilotLogger.i(TAG, "Online TTS stopped by user")
            else PilotLogger.e(TAG, "Online TTS failed", e)
            false
        } finally {
            releasePlayer()
        }
    }

    fun stop() {
        stopped = true
        releasePlayer()
    }

    private fun chunk(text: String): List<String> {
        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return emptyList()
        if (cleaned.length <= MAX_CHUNK) return listOf(cleaned)
        val parts = cleaned.split(Regex("(?<=[.!?؟،؛])\\s+")).filter { it.isNotBlank() }
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (part in parts) {
            if (current.isNotEmpty() && current.length + part.length + 1 > MAX_CHUNK) {
                chunks.add(current.toString().trim())
                current.setLength(0)
            }
            if (part.length > MAX_CHUNK) {
                part.chunked(MAX_CHUNK).forEach { chunks.add(it.trim()) }
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(part)
            }
        }
        if (current.isNotEmpty()) chunks.add(current.toString().trim())
        return chunks.filter { it.isNotBlank() }
    }

    private fun download(chunk: String): ByteArray {
        val encoded = URLEncoder.encode(chunk, "UTF-8")
        val url = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=ar&q=$encoded"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AiFriend/1.0")
            setRequestProperty("Referer", "https://translate.google.com/")
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "TTS HTTP ${connection.responseCode}" }
            val bytes = connection.inputStream.readBytes()
            check(bytes.size > MIN_AUDIO_BYTES) { "TTS audio too small (${bytes.size} bytes)" }
            return bytes
        } finally {
            connection.disconnect()
        }
    }

    private fun playBlocking(audio: ByteArray) {
        releasePlayer()
        val temp = File.createTempFile("aifriend_tts", ".mp3")
        val latch = CountDownLatch(1)
        val mp = MediaPlayer()
        player = mp
        try {
            FileOutputStream(temp).use { it.write(audio) }
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(temp.absolutePath)
            mp.prepare()
            mp.setPlaybackParams(PlaybackParams().setSpeed(1.05f))
            mp.setOnCompletionListener { latch.countDown() }
            mp.setOnErrorListener { _, what, extra ->
                PilotLogger.e(TAG, "MediaPlayer error what=$what extra=$extra")
                latch.countDown()
                true
            }
            mp.start()
            latch.await(60, TimeUnit.SECONDS)
        } finally {
            temp.delete()
        }
    }

    private fun releasePlayer() {
        player?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        player = null
    }

    companion object {
        private const val TAG = "OnlineTts"
        private const val MAX_CHUNK = 190
        private const val MIN_AUDIO_BYTES = 512
    }
}
