package com.ziaada.aifriend.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssistantEngine(
    private val context: Context,
    private val vehicleController: VehicleController,
) {
    private val commandRouter = CommandRouter(context, vehicleController, OpenRouterLlm(context))

    suspend fun handleArabicCommand(text: String): String {
        return withContext(Dispatchers.IO) {
            commandRouter.route(text)
        }
    }

    fun setLlmApiKey(key: String) {
        // OpenRouterLlm handles its own prefs
    }

    fun hasLlmKey(): Boolean = true // OpenRouterLlm checks prefs internally

    fun enableLlmFallback(enabled: Boolean) {
        // OpenRouterLlm checks prefs; empty key = offline mode
    }
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
    )
}

interface LlmFallback {
    suspend fun query(prompt: String): String
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