package com.ziaada.aifriend.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssistantEngine(
    private val context: Context,
    private val vehicleController: VehicleController,
) {
    private val appLauncher = AppLauncher(context)
    private val llm = OpenRouterLlm(context, vehicleController, appLauncher)
    private val commandRouter = CommandRouter(context, vehicleController, llm, appLauncher)

    suspend fun handleArabicCommand(text: String, vehicleConnected: Boolean): String {
        return withContext(Dispatchers.IO) {
            commandRouter.route(text, vehicleConnected)
        }
    }

    fun setLlmApiKey(key: String) {
        llm.setApiKey(key)
    }

    fun hasLlmKey(): Boolean = llm.hasApiKey()

    fun enableLlmFallback(enabled: Boolean) {
        if (!enabled) llm.setApiKey("")
    }
}

object AssistantPersona {
    const val NAME_ARABIC = "آدم"
    const val NAME_ENGLISH = "Adam"
    val SYSTEM_PROMPT = """
        أنت آدم، رفيق صوتي ودود وهادئ لسائق سيارة BYD Song Plus.
        تكلم بالعربية المصرية البسيطة وبإجابات قصيرة مناسبة للاستماع أثناء القيادة.
        خليك عملي وصريح. لا تدّعي تنفيذ أمر أو معرفة بيانات السيارة إلا بعد نجاح الأداة أو قراءة البيانات الفعلية.
        استخدم أداة التحكم فقط عندما يطلب المستخدم تغييراً واضحاً ومقصوداً. السؤال عن كيفية فعل شيء لا يعني تنفيذه.
        لا تخمن قيماً للسيارة، ولا تنفذ أمراً غير واضح؛ اسأل سؤالاً قصيراً للتوضيح.
        استخدم أداة فتح التطبيقات فقط عند طلب فتح تطبيق بالاسم، ولا تفتح تطبيقاً لمجرد ذكره.
        لا تختلق إجابات لمعلومات حديثة عند عدم توفر الإنترنت أو المصدر.
    """.trimIndent()
}

interface VehicleController {
    suspend fun setClimatePower(enabled: Boolean)
    suspend fun setClimateTemperature(celsius: Int)
    suspend fun openWindows()
    suspend fun closeWindows()
    suspend fun getSocPercent(): Int?

    suspend fun setClimateAuto(enabled: Boolean) = Unit
    suspend fun setClimateFanLevel(level: Int) = Unit
    suspend fun setRecirculation(enabled: Boolean) = Unit
    suspend fun setRearDefrost(enabled: Boolean) = Unit
    suspend fun setWindowPosition(window: String, percent: Int) = Unit
    suspend fun openSunroof() = Unit
    suspend fun closeSunroof() = Unit
    suspend fun tiltSunroof() = Unit
    suspend fun openSunshade() = Unit
    suspend fun closeSunshade() = Unit
    suspend fun setDoorLocks(lock: Boolean) = Unit
    suspend fun openFrunk() = Unit
    suspend fun closeFrunk() = Unit
    suspend fun setSeatHeat(position: String, level: Int) = Unit
    suspend fun setSeatVent(position: String, level: Int) = Unit
    suspend fun setSteeringHeat(enabled: Boolean) = Unit
    suspend fun setDriveMode(mode: String) = Unit
    suspend fun readTelemetry(): Telemetry? = null

    data class Telemetry(
        val soc: Int,
        val speedKph: Float,
        val powerKw: Float,
        val soh: Int,
        val insideTemp: Float,
        val outsideTemp: Float,
        val voltage12v: Float,
        val maxBatTemp: Float,
        val minBatTemp: Float,
        val climateSetpointC: Float? = null,
        val tirePressureKpa: Map<String, Float> = emptyMap(),
    )
}

interface LlmFallback {
    suspend fun query(prompt: String, vehicleConnected: Boolean): String
}

class StubVehicleController : VehicleController {
    private var climate = false
    private var temperature = 22

    override suspend fun setClimatePower(enabled: Boolean) { climate = enabled }
    override suspend fun setClimateTemperature(celsius: Int) { temperature = celsius.coerceIn(16, 30) }
    override suspend fun openWindows() = Unit
    override suspend fun closeWindows() = Unit
    override suspend fun getSocPercent(): Int? = null
}
