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
    private val vehicleController: VehicleController,
    private val appLauncher: AppLauncher,
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

    private val systemPrompt = AssistantPersona.SYSTEM_PROMPT

    override suspend fun query(prompt: String, vehicleConnected: Boolean): String {
        val apiKey = prefs.getString("openrouter_key", "")?.takeIf { it.isNotBlank() }
            ?: return "أنا آدم. الأسئلة العامة محتاجة مفتاح OpenRouter من زر الإعدادات. أوامر السيارة وفتح التطبيقات المعروفة بتشتغل محلياً."

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

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "OpenRouter error: ${response.code} ${response.body?.string()}")
                        return@withContext strings.get(StringRes.llm_error)
                    }

                    val json = JsonParser.parseString(response.body?.string() ?: "{}").asJsonObject
                    val choice = json.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                        ?: return@withContext strings.get(StringRes.llm_error)
                    val message = choice.getAsJsonObject("message")
                    val toolCalls = message.getAsJsonArray("tool_calls")

                    if (toolCalls != null && toolCalls.size() > 0) {
                        return@withContext handleToolCalls(toolCalls, vehicleConnected)
                    }

                    val content = message.get("content")
                    return@withContext if (content != null && !content.isJsonNull) content.asString
                    else "أنا آدم، ما وصلنيش رد واضح. ممكن تعيد السؤال؟"
                }
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
                            "action" to mapOf("type" to "string", "enum" to listOf("none", "on", "off", "auto_on", "auto_off")),
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
                    "name" to "set_frunk",
                    "description" to "افتح أو أغلق الصندوق الأمامي عند طلب المستخدم بوضوح؛ الفتح متاح فقط والسيارة متوقفة.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf("action" to mapOf("type" to "string", "enum" to listOf("open", "close"))),
                        "required" to listOf("action"),
                    ),
                ),
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
                            "query" to mapOf("type" to "string", "enum" to listOf("soc", "range", "inside_temp", "outside_temp", "climate_setpoint", "battery_temp", "voltage_12v", "speed", "power_kw", "soh", "tpms", "charging"))
                        ),
                        "required" to listOf("query")
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "open_app",
                    "description" to "افتح تطبيقاً مثبتاً عندما يطلب المستخدم ذلك صراحةً، باستخدام اسمه الظاهر على شاشة التطبيقات.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf("app_name" to mapOf("type" to "string")),
                        "required" to listOf("app_name"),
                    ),
                ),
            )
        )
    }

    private suspend fun handleToolCalls(
        toolCalls: com.google.gson.JsonArray,
        vehicleConnected: Boolean,
    ): String {
        val results = mutableListOf<String>()
        for (call in toolCalls) {
            val result = try {
                val fn = call.asJsonObject.getAsJsonObject("function")
                val name = fn.get("name").asString
                val args = JsonParser.parseString(fn.get("arguments").asString).asJsonObject
                if (name != "open_app" && !vehicleConnected) {
                    "السيارة مش متصلة بخدمة التحكم دلوقتي، فما نفذتش أمر السيارة."
                } else {
                    when (name) {
                        "set_climate" -> executeClimate(args)
                        "set_windows" -> executeWindows(args)
                        "set_sunroof" -> executeSunroof(args)
                        "set_doors" -> executeDoors(args)
                        "set_frunk" -> executeFrunk(args)
                        "set_seat" -> executeSeat(args)
                        "set_steering_heat" -> executeSteering(args)
                        "set_drive_mode" -> executeDriveMode(args)
                        "get_telemetry" -> executeTelemetry(args)
                        "open_app" -> appLauncher.launch(args.get("app_name").asString).message
                        else -> "مش قادر أنفذ الطلب ده لسه."
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Tool execution failed", e)
                "ما قدرتش أنفذ الأمر: ${e.message ?: "حصل خطأ في خدمة السيارة"}"
            }
            results.add(result)
        }
        return results.joinToString("\n")
    }

    private suspend fun executeClimate(args: JsonObject): String {
        val action = args.get("action").asString
        val temp = args.intOrNull("temperature")
        val fan = args.intOrNull("fan_level")
        when (action) {
            "none" -> Unit
            "on" -> vehicleController.setClimatePower(true)
            "off" -> vehicleController.setClimatePower(false)
            "auto_on" -> vehicleController.setClimateAuto(true)
            "auto_off" -> vehicleController.setClimateAuto(false)
            else -> return "ما فهمتش وضع التكييف المطلوب."
        }
        if (temp != null) {
            require(temp in 16..30) { "درجة التكييف لازم تكون من 16 إلى 30" }
            vehicleController.setClimateTemperature(temp)
        }
        if (fan != null) {
            require(fan in 1..7) { "سرعة المروحة لازم تكون من 1 إلى 7" }
            vehicleController.setClimateFanLevel(fan)
        }
        args.booleanOrNull("recirculation")?.let { vehicleController.setRecirculation(it) }
        args.booleanOrNull("defrost")?.let { vehicleController.setRearDefrost(it) }
        return when (action) {
            "none" -> "تمام، ضبطت التكييف${temp?.let { " على $it درجة" } ?: ""}."
            "off" -> "تمام، طفيت التكييف."
            "auto_on" -> "تمام، فعلت وضع التكييف التلقائي."
            "auto_off" -> "تمام، أوقفت الوضع التلقائي."
            else -> "تمام، شغلت التكييف${temp?.let { " على $it درجة" } ?: ""}."
        }
    }

    private suspend fun executeWindows(args: JsonObject): String {
        val action = args.get("action").asString
        val position = args.get("position").asString
        val percent = args.intOrNull("percent")
        val windows = when (position) {
            "driver" -> listOf("fl")
            "passenger" -> listOf("fr")
            "rear_left" -> listOf("rl")
            "rear_right" -> listOf("rr")
            "all" -> listOf("fl", "fr", "rl", "rr")
            else -> return "ما عرفتش أحدد الشباك المطلوب."
        }
        when (action) {
            "open" -> if (position == "all" && (percent == null || percent == 100)) vehicleController.openWindows()
                else windows.forEach { vehicleController.setWindowPosition(it, percent ?: 100) }
            "close" -> if (position == "all" && (percent == null || percent == 0)) vehicleController.closeWindows()
                else windows.forEach { vehicleController.setWindowPosition(it, percent ?: 0) }
            "vent" -> windows.forEach { vehicleController.setWindowPosition(it, percent ?: 15) }
            else -> return "ما فهمتش حركة الشباك المطلوبة."
        }
        return "تمام، نفذت أمر الشباك $position."
    }

    private suspend fun executeSunroof(args: JsonObject): String {
        val action = args.get("action").asString
        when (action) {
            "open" -> vehicleController.openSunroof()
            "close" -> vehicleController.closeSunroof()
            "tilt" -> vehicleController.tiltSunroof()
            "shade_open" -> vehicleController.openSunshade()
            "shade_close" -> vehicleController.closeSunshade()
            else -> return "ما فهمتش حركة السقف المطلوبة."
        }
        return "تمام، نفذت أمر السقف."
    }

    private suspend fun executeDoors(args: JsonObject): String {
        val lock = args.get("lock").asBoolean
        vehicleController.setDoorLocks(lock)
        return if (lock) "تمام، قفلت الأبواب." else "تمام، فتحت قفل الأبواب."
    }

    private suspend fun executeFrunk(args: JsonObject): String {
        return when (args.get("action").asString) {
            "open" -> {
                vehicleController.openFrunk()
                "تمام، فتحت الصندوق الأمامي."
            }
            "close" -> {
                vehicleController.closeFrunk()
                "تمام، أغلقت الصندوق الأمامي."
            }
            else -> "ما فهمتش أمر الصندوق الأمامي."
        }
    }

    private suspend fun executeSeat(args: JsonObject): String {
        val pos = args.get("position").asString
        val feature = args.get("feature").asString
        val action = args.get("action").asString
        val level = args.intOrNull("level") ?: 3
        require(level in 1..5) { "مستوى المقعد لازم يكون من 1 إلى 5" }
        val position = if (pos == "passenger") "passenger" else "driver"
        val activeLevel = if (action == "off") 0 else level
        when (feature) {
            "heat" -> vehicleController.setSeatHeat(position, activeLevel)
            "vent" -> vehicleController.setSeatVent(position, activeLevel)
            else -> return "ما فهمتش إعداد المقعد المطلوب."
        }
        return "تمام، ${if (action == "off") "أوقفت" else "شغلت"} ${if (feature == "heat") "تدفئة" else "تهوية"} كرسي ${if (position == "driver") "السائق" else "الراكب"}."
    }

    private suspend fun executeSteering(args: JsonObject): String {
        val on = args.get("on").asBoolean
        vehicleController.setSteeringHeat(on)
        return if (on) "تمام، شغلت تدفئة المقود." else "تمام، أوقفت تدفئة المقود."
    }

    private suspend fun executeDriveMode(args: JsonObject): String {
        val mode = args.get("mode").asString
        require(mode in setOf("ECO", "SPORT", "NORMAL", "SNOW", "SAND", "MUD", "MOUNTAIN", "SMART")) { "وضع القيادة غير مدعوم" }
        vehicleController.setDriveMode(mode)
        return "تمام، غيرت وضع القيادة إلى $mode."
    }

    private suspend fun executeTelemetry(args: JsonObject): String {
        val query = args.get("query").asString
        val data = vehicleController.readTelemetry() ?: return "مش قادر أوصل لقراءات السيارة حالياً."
        return when (query) {
            "soc" -> "نسبة شحن البطارية ${data.soc}٪."
            "speed" -> "سرعة السيارة ${data.speedKph} كم/س."
            "power_kw" -> "قدرة المحرك الحالية ${data.powerKw} كيلوواط."
            "soh" -> "صحة البطارية ${data.soh}٪."
            "inside_temp" -> "درجة الحرارة داخل السيارة ${data.insideTemp} درجة."
            "outside_temp" -> "درجة الحرارة خارج السيارة ${data.outsideTemp} درجة."
            "climate_setpoint" -> data.climateSetpointC?.let { "درجة التكييف المضبوطة ${it} درجة." } ?: "درجة ضبط التكييف مش متاحة حالياً."
            "battery_temp" -> "أعلى درجة حرارة للبطارية ${data.maxBatTemp} درجة."
            "voltage_12v" -> "جهد البطارية 12 فولت ${data.voltage12v} فولت."
            "range" -> "قراءة المدى المتبقية مش متاحة من بيانات السيارة الحالية."
            "tpms" -> if (data.tirePressureKpa.isEmpty()) "قراءات ضغط الإطارات مش متاحة حالياً."
                else data.tirePressureKpa.entries.joinToString("، ") { (name, value) -> "$name: ${value.toInt()} كيلوباسكال" } + "."
            "charging" -> "حالة الشحن مش متاحة من بيانات السيارة الحالية."
            else -> "مش لاقية القراءة المطلوبة في بيانات السيارة."
        }
    }

    private fun JsonObject.intOrNull(name: String): Int? = get(name)?.takeUnless { it.isJsonNull }?.asInt
    private fun JsonObject.booleanOrNull(name: String): Boolean? = get(name)?.takeUnless { it.isJsonNull }?.asBoolean

    fun setApiKey(key: String) {
        if (key.isBlank()) prefs.edit().remove("openrouter_key").apply()
        else prefs.edit().putString("openrouter_key", key.trim()).apply()
    }

    fun hasApiKey(): Boolean = !prefs.getString("openrouter_key", "").isNullOrBlank()
}
