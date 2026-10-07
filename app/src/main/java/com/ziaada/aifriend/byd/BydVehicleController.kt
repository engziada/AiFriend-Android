package com.ziaada.aifriend.byd

import com.ziaada.aifriend.domain.VehicleController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Executes commands through DiLink autoservice, failing instead of claiming a rejected write succeeded. */
class BydVehicleController(
    private val autoserviceClient: AutoserviceClient,
) : VehicleController {

    override suspend fun setClimatePower(enabled: Boolean) = write("ac_power_set", if (enabled) 1 else 0)

    override suspend fun setClimateTemperature(celsius: Int) {
        require(celsius in 16..30) { "درجة التكييف لازم تكون من 16 إلى 30" }
        write("ac_temp_main_set", celsius)
    }

    override suspend fun openWindows() {
        requireSpeedAtMost(120f, "فتح الشبابيك")
        listOf("window_fl_ctrl", "window_fr_ctrl", "window_rl_ctrl", "window_rr_ctrl")
            .forEach { write(it, 1) }
    }

    override suspend fun closeWindows() {
        listOf("window_fl_ctrl", "window_fr_ctrl", "window_rl_ctrl", "window_rr_ctrl")
            .forEach { write(it, 2) }
    }

    override suspend fun getSocPercent(): Int? = readTelemetry()?.soc

    override suspend fun setClimateAuto(enabled: Boolean) = write("ac_ctrl_mode_set", if (enabled) 0 else 1)

    override suspend fun setClimateFanLevel(level: Int) {
        require(level in 1..7) { "سرعة المروحة لازم تكون من 1 إلى 7" }
        write("ac_fan_level_set", level)
    }

    override suspend fun setRecirculation(enabled: Boolean) = write("ac_cycle_mode_set", if (enabled) 1 else 0)

    override suspend fun setRearDefrost(enabled: Boolean) = write("ac_defrost_rear_set", if (enabled) 1 else 0)

    override suspend fun setWindowPosition(window: String, percent: Int) {
        val key = when (window) {
            "fl" -> "window_fl_pct"
            "fr" -> "window_fr_pct"
            "rl" -> "window_rl_pct"
            "rr" -> "window_rr_pct"
            else -> throw IllegalArgumentException("الشباك المطلوب غير معروف")
        }
        require(percent in 0..100) { "نسبة فتح الشباك لازم تكون من 0 إلى 100" }
        if (percent > 0) requireSpeedAtMost(120f, "فتح الشباك")
        write(key, percent)
        delay(300)
        write(key, 255)
    }

    override suspend fun openSunroof() {
        requireSpeedAtMost(80f, "فتح السقف")
        write("sunroof_ctrl", 1)
    }

    override suspend fun closeSunroof() = write("sunroof_ctrl", 2)

    override suspend fun tiltSunroof() {
        requireSpeedAtMost(80f, "إمالة السقف")
        write("sunroof_ctrl", 3)
    }

    override suspend fun openSunshade() = write("sunshade_ctrl", 1)

    override suspend fun closeSunshade() = write("sunshade_ctrl", 2)

    override suspend fun setDoorLocks(lock: Boolean) {
        if (!lock) requireSpeedAtMost(30f, "فتح قفل الأبواب")
        write("doors_lock_set", if (lock) 2 else 1)
    }

    override suspend fun openFrunk() {
        requireSpeedAtMost(0.1f, "فتح الصندوق الأمامي")
        write("frunk_ctrl", 1)
    }

    override suspend fun closeFrunk() = write("frunk_ctrl", 3)

    override suspend fun setSeatHeat(position: String, level: Int) = setSeatFeature(position, "heat", level)

    override suspend fun setSeatVent(position: String, level: Int) = setSeatFeature(position, "vent", level)

    private suspend fun setSeatFeature(position: String, feature: String, level: Int) {
        require(level in 0..5) { "مستوى المقعد لازم يكون من 0 إلى 5" }
        val seat = when (position) {
            "driver" -> "driver"
            "passenger" -> "pass"
            else -> throw IllegalArgumentException("المقعد المطلوب غير معروف")
        }
        val primarySwitch = "seat_${seat}_${feature}_switch"
        val primaryLevel = "seat_${seat}_${feature}_level"
        val fallback = "seat_${seat}_${feature}_fb"
        try {
            if (level == 0) {
                write(primarySwitch, 2)
            } else {
                write(primarySwitch, 1)
                write(primaryLevel, level)
            }
        } catch (primaryError: Exception) {
            try {
                write(fallback, if (level == 0) 1 else level + 1)
            } catch (_: Exception) {
                throw primaryError
            }
        }
    }

    override suspend fun setSteeringHeat(enabled: Boolean) = write("steering_heat_set", if (enabled) 2 else 1)

    override suspend fun setDriveMode(mode: String) {
        // FID values vary by BYD model and firmware; do not guess a write value.
        throw UnsupportedOperationException("مش هغيّر وضع القيادة قبل ما أتأكد من قيمه الصحيحة على إصدار السيارة ده")
    }

    override suspend fun readTelemetry(): VehicleController.Telemetry? = withContext(Dispatchers.IO) {
        val values = listOf(
            "soc", "speed", "power_kw", "soh", "ac_inside_temp", "ac_outside_temp", "ac_temp_main",
            "voltage_12v", "max_bat_temp", "min_bat_temp", "tpms_fl", "tpms_fr", "tpms_rl", "tpms_rr",
        ).mapNotNull { name -> readValue(name)?.let { name to it } }.toMap()
        if (values.isEmpty()) return@withContext null

        VehicleController.Telemetry(
            soc = (values["soc"] ?: 0f).toInt(),
            speedKph = values["speed"] ?: 0f,
            powerKw = values["power_kw"] ?: 0f,
            soh = (values["soh"] ?: 0f).toInt(),
            insideTemp = values["ac_inside_temp"] ?: 0f,
            outsideTemp = values["ac_outside_temp"] ?: 0f,
            voltage12v = values["voltage_12v"] ?: 0f,
            maxBatTemp = values["max_bat_temp"] ?: 0f,
            minBatTemp = values["min_bat_temp"] ?: 0f,
            climateSetpointC = values["ac_temp_main"],
            tirePressureKpa = mapOf(
                "أمامي يسار" to "tpms_fl",
                "أمامي يمين" to "tpms_fr",
                "خلفي يسار" to "tpms_rl",
                "خلفي يمين" to "tpms_rr",
            ).mapNotNull { (label, key) -> values[key]?.let { label to it } }.toMap(),
        )
    }

    private suspend fun requireSpeedAtMost(limitKph: Float, action: String) {
        val speed = readValue("speed") ?: throw IllegalStateException("مش قادر أتأكد من سرعة السيارة؛ $action اتلغى للأمان")
        check(speed <= limitKph) { "مش هقدر أنفذ $action عند سرعة $speed كم/س" }
    }

    private suspend fun readValue(name: String): Float? {
        val address = FidRegistry.getRead(name) ?: return null
        return if (address.decoder.startsWith("FLOAT")) {
            val value = autoserviceClient.readFloat(address.deviceId, address.fid)
            value.takeUnless { autoserviceClient.isFloatSentinel(it) }
        } else {
            val raw = autoserviceClient.readInt(address.deviceId, address.fid)
            if (autoserviceClient.isSentinel(name, raw)) null
            else (autoserviceClient.decodeValue(name, raw) as? Number)?.toFloat()
        }
    }

    private suspend fun write(name: String, value: Int) = withContext(Dispatchers.IO) {
        val address = FidRegistry.getWrite(name)
            ?: throw IllegalStateException("الأمر ده مش متاح في كتالوج السيارة الحالي")
        check(autoserviceClient.writeInt(address.deviceId, address.fid, value)) {
            "خدمة السيارة ما أكدتش تنفيذ الأمر. تأكدي من اتصال السيارة وجربي تاني."
        }
    }
}
