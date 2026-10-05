package com.ziaada.aifriend.domain

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OpenRouterLlm(
    private val context: Context,
) : LlmFallback {
    private val TAG = "OpenRouterLlm"
    private val strings = StringRes(context)
    private val gson = Gson()
    private val prefs: SharedPreferences = context.getSharedPreferences("llm_prefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiUrl = "https://openrouter.ai/api/v1/chat/completions"

    private val systemPrompt = """
        أنت مساعد صوتي في سيارة BYD Song Plus. تتحدث بالعربية المصرية.
        يمكنك تنفيذ أوامر السيارة عبر أدوات (tools) مثل: التكييف، الشبابيك، السقف، الأبواب، الكراسي، الاستعلامات.
        كن مختصراً وودوداً. لا تشرح الأدوات للمستخدم - فقط نفذ وقل النتيجة.
        """.trimIndent()

    override suspend fun query(prompt: String): String {
        val apiKey = prefs.getString("openrouter_key", "") ?: return strings.get(StringRes.llm_offline)

        return withContext(Dispatchers.IO) {
            try {
                val requestBody = JsonObject().apply {
                    addProperty("model", "openai/gpt-4o-mini")
                    add("messages", gson.toJsonTree(listOf(
                        mapOf("role" to "system", "content" to systemPrompt),
                        mapOf("role" to "user", "content" to prompt)
                    )))
                    addProperty("temperature", 0.3)
                    addProperty("max_tokens", 200)
                    add("tools", gson.toJsonTree(buildTools()))
                    addProperty("tool_choice", "auto")
                }

                val request = Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://github.com/ziaada/AiFriend")
                    .addHeader("X-Title", "AiFriend BYD")
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "OpenRouter error: ${response.code} ${response.body?.string()}")
                    return@withContext strings.get(StringRes.llm_error)
                }

                val json = JsonParser.parseString(response.body?.string() ?: "{}").asJsonObject
                val choice = json.getAsJsonArray("choices").get(0).asJsonObject
                val message = choice.getAsJsonObject("message")

                if (message.has("tool_calls")) {
                    return@withContext handleToolCalls(message.getAsJsonArray("tool_calls"))
                }

                return@withContext message.get("content").asString ?: strings.get(StringRes.llm_error)
            } catch (e: IOException) {
                Log.e(TAG, "Network error", e)
                return@withContext strings.get(StringRes.llm_error)
            } catch (e: Exception) {
                Log.e(TAG, "LLM error", e)
                return@withContext strings.get(StringRes.llm_error)
            }
        }
    }

    private fun buildTools(): List<Map<String, Any>> {
        return listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "set_climate",
                    "description" to "تحكم في التكييف",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "action" to mapOf("type" to "string", "enum" to listOf("on", "off", "auto_on", "auto_off")),
                            "temperature" to mapOf("type" to "integer", "minimum" to 16, "maximum" to 30),
                            "fan_level" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 7),
                            "recirculation" to mapOf("type" to "boolean"),
                            "defrost" to mapOf("type" to "boolean"),
                        ),
                        "required" to listOf("action")
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "set_windows",
                    "description" to "تحكم في الشبابيك",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "action" to mapOf("type" to "string", "enum" to listOf("open", "close", "vent")),
                            "position" to mapOf("type" to "string", "enum" to listOf("all", "driver", "passenger", "rear_left", "rear_right")),
                            "percent" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 100),
                        ),
                        "required" to listOf("action", "position")
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "set_sunroof",
                    "description" to "تحكم في السقف البانورامي",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "action" to mapOf("type" to "string", "enum" to listOf("open", "close", "tilt", "shade_open", "shade_close"))
                        ),
                        "required" to listOf("action")
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "set_doors",
                    "description" to "قفل/فتح الأبواب",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "lock" to mapOf("type" to "boolean")
                        ),
                        "required" to listOf("lock")
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "set_seat",
                    "description" to "تدفئة/تهوية الكراسي",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "position" to mapOf("type" to "string", "enum" to listOf("driver", "passenger")),
                            "feature" to mapOf("type" to "string", "enum" to listOf("heat", "vent")),
                            "action" to mapOf("type" to "string", "enum" to listOf("on", "off")),
                            "level" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 5)
                        ),
                        "required" to listOf("position", "feature", "action")
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "set_steering_heat",
                    "description" to "تدفئة الدركسيون",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "on" to mapOf("type" to "boolean")
                        ),
                        "required" to listOf("on")
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "set_drive_mode",
                    "description" to "وضع القيادة",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "mode" to mapOf("type" to "string", "enum" to listOf("ECO", "SPORT", "NORMAL", "SNOW", "SAND", "MUD", "MOUNTAIN", "SMART"))
                        ),
                        "required" to listOf("mode")
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "get_telemetry",
                    "description" to "استعلام بيانات السيارة",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "query" to mapOf("type" to "string", "enum" to listOf("soc", "range", "inside_temp", "outside_temp", "battery_temp", "voltage_12v", "tpms", "charging"))
                        ),
                        "required" to listOf("query")
                    )
                )
            )
        )
    }

    private fun handleToolCalls(toolCalls: com.google.gson.JsonArray): String {
        val results = mutableListOf<String>()
        for (call in toolCalls) {
            val fn = call.asJsonObject.get("function").asJsonObject
            val name = fn.get("name").asString
            val args = JsonParser.parseString(fn.get("arguments").asString).asJsonObject

            val result = when (name) {
                "set_climate" -> executeClimate(args)
                "set_windows" -> executeWindows(args)
                "set_sunroof" -> executeSunroof(args)
                "set_doors" -> executeDoors(args)
                "set_seat" -> executeSeat(args)
                "set_steering_heat" -> executeSteering(args)
                "set_drive_mode" -> executeDriveMode(args)
                "get_telemetry" -> executeTelemetry(args)
                else -> "أداة غير معروفة: $name"
            }
            results.add(result)
        }
        return results.joinToString("\n")
    }

    private fun executeClimate(args: JsonObject): String {
        val action = args.get("action").asString
        val temp = args.get("temperature")?.asInt
        val fan = args.get("fan_level")?.asInt
        val recirc = args.get("recirculation")?.asBoolean
        val defrost = args.get("defrost")?.asBoolean
        return "تم تنفيذ: تكييف $action${temp?.let { " على $it درجة" } ?: ""}${fan?.let { "، مروحة $it" } ?: ""}${recirc?.let { if (it) "، تدوير داخلي" } ?: ""}${defrost?.let { if (it) "، إزالة ضباب" } ?: ""}"
    }

    private fun executeWindows(args: JsonObject): String {
        val action = args.get("action").asString
        val position = args.get("position").asString
        val percent = args.get("percent")?.asInt
        return "تم تنفيذ: شباك $position $action${percent?.let { " $it%" } ?: ""}"
    }

    private fun executeSunroof(args: JsonObject): String {
        val action = args.get("action").asString
        return "تم تنفيذ: سقف $action"
    }

    private fun executeDoors(args: JsonObject): String {
        val lock = args.get("lock").asBoolean
        return "تم تنفيذ: أبواب ${if (lock) "قفل" else "فتح"}"
    }

    private fun executeSeat(args: JsonObject): String {
        val pos = args.get("position").asString
        val feature = args.get("feature").asString
        val action = args.get("action").asString
        val level = args.get("level")?.asInt
        return "تم تنفيذ: كرسي $pos ${if (feature == "heat") "تدفئة" else "تهوية"} $action${level?.let { " مستوى $it" } ?: ""}"
    }

    private fun executeSteering(args: JsonObject): String {
        val on = args.get("on").asBoolean
        return "تم تنفيذ: دركسيون ${if (on) "تدفئة شغّالة" else "تدفئة طافية"}"
    }

    private fun executeDriveMode(args: JsonObject): String {
        val mode = args.get("mode").asString
        return "تم تنفيذ: وضع قيادة $mode"
    }

    private fun executeTelemetry(args: JsonObject): String {
        val query = args.get("query").asString
        return "استعلام: $query - (البيانات الفعلية تحتاج تنفيذ)"
    }

    fun setApiKey(key: String) {
        prefs.edit().putString("openrouter_key", key).apply()
    }

    fun hasApiKey(): Boolean = prefs.contains("openrouter_key")
}