package com.ziaada.aifriend.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CommandRouter(
    private val context: Context,
    private val vehicleController: VehicleController,
    private val llmFallback: LlmFallback?,
) {
    private val strings = StringRes(context)

    private fun normalize(text: String): String {
        return text.trim()
            .replace(Regex("[ـ]"), "")
            .replace(Regex("[َُِّْٰ]"), "")
            .replace('٠', '0').replace('١', '1').replace('٢', '2')
            .replace('٣', '3').replace('٤', '4').replace('٥', '5')
            .replace('٦', '6').replace('٧', '7').replace('٨', '8').replace('٩', '9')
            .lowercase()
    }

    private fun extractNumber(text: String): Int? {
        val cleaned = text.replace(Regex("[^0-9٠-٩]"), "")
            .replace('٠', '0').replace('١', '1').replace('٢', '2')
            .replace('٣', '3').replace('٤', '4').replace('٥', '5')
            .replace('٦', '6').replace('٧', '7').replace('٨', '8').replace('٩', '9')
        return cleaned.toIntOrNull()
    }

    private fun extractWindowPosition(text: String): String? {
        val positions = mapOf(
            "السائق" to "fl", "سائق" to "fl", "يمين" to "fl",
            "الراكب" to "fr", "راكب" to "fr", "يسار" to "fr", "الضيف" to "fr",
            "الخلف" to "rl", "خلفي" to "rl", "يمين خلف" to "rl",
            "اليسار خلف" to "rr", "يسار خلف" to "rr", "اليمين خلف" to "rr",
        )
        return positions.entries.firstOrNull { text.contains(it.key) }?.value
    }

    private fun extractSeatPosition(text: String): String? {
        val positions = mapOf(
            "السائق" to "driver", "سائق" to "driver",
            "الراكب" to "passenger", "راكب" to "passenger", "الضيف" to "passenger",
        )
        return positions.entries.firstOrNull { text.contains(it.key) }?.value
    }

    private fun extractSeatFeature(text: String): String? {
        return when {
            text.contains("تدفئة") || text.contains("سخن") || text.contains("دفى") -> "heat"
            text.contains("تهوية") || text.contains("مكيف كرسي") || text.contains("تبريد كرسي") -> "vent"
            else -> null
        }
    }

    suspend fun route(command: String): String {
        val normalized = normalize(command)

        if (normalized.contains("ساعدني") || normalized.contains("مساعدة") || normalized.contains("الأوامر")) {
            return strings.get(StringRes.help_text)
        }

        if (normalized.contains("مكيف") || normalized.contains("التكييف") || normalized.contains("كليمت") || normalized.contains("تبريد")) {
            return routeClimate(normalized)
        }
        if (normalized.contains("شباك") || normalized.contains("شبابيك") || normalized.contains("نافذة") || normalized.contains("زجاج")) {
            return routeWindows(normalized)
        }
        if (normalized.contains("سقف") || normalized.contains("فتحة سقف") || normalized.contains("ستارة سقف")) {
            return routeSunroof(normalized)
        }
        if (normalized.contains("باب") || normalized.contains("أبواب") || normalized.contains("قفل") || normalized.contains("فتح أبواب") || normalized.contains("اقفال")) {
            return routeDoors(normalized)
        }
        if (normalized.contains("كرسي") || normalized.contains("مقعد") || normalized.contains("تدفئة") || normalized.contains("تهوية كرسي")) {
            return routeSeats(normalized)
        }
        if (normalized.contains("دركسيون") || normalized.contains("المقود") || normalized.contains("ستيرينج")) {
            return routeSteering(normalized)
        }
        if (normalized.contains("وضع قيادة") || normalized.contains("قيادة") && (normalized.contains("اقتصادي") || normalized.contains("رياضي") || normalized.contains("ثلج") || normalized.contains("رمل") || normalized.contains("طين") || normalized.contains("جبل") || normalized.contains("ذكي") || normalized.contains("عادي"))) {
            return routeDriveMode(normalized)
        }
        if (normalized.contains("كم") || normalized.contains("كام") || normalized.contains("في المية") || normalized.contains("النسبة") || normalized.contains("المدى") || normalized.contains("المسافة") || normalized.contains("حرارة") || normalized.contains("ضغط") || normalized.contains("شحن")) {
            return routeTelemetry(normalized)
        }
        if (normalized.contains("شنطة") && (normalized.contains("أمامية") || normalized.contains("قدامي"))) {
            return routeFrunk(normalized)
        }

        return llmFallback?.query(normalized) ?: strings.get(StringRes.command_not_understood)
    }

    private suspend fun routeClimate(text: String): String {
        val temp = extractNumber(text)

        if (text.contains("شغ") || text.contains("تشغيل") || text.contains("فتّح") || text.contains("افتح")) {
            vehicleController.setClimatePower(true)
            if (temp != null) {
                vehicleController.setClimateTemperature(temp)
                return strings.get(StringRes.climate_temp_set, temp)
            }
            return strings.get(StringRes.climate_on)
        }
        if (text.contains("طفي") || text.contains("أطفئ") || text.contains("قفل") || text.contains("أوقف")) {
            vehicleController.setClimatePower(false)
            return strings.get(StringRes.climate_off)
        }

        if (text.contains("أوتوماتيك") || text.contains("أوتو") || text.contains("تلقائي")) {
            if (text.contains("طفي") || text.contains("أطفئ") || text.contains("إلغاء")) {
                vehicleController.setClimateAuto(false)
                return "طفيت وضع أوتوماتيك"
            }
            vehicleController.setClimateAuto(true)
            return strings.get(StringRes.climate_auto_on)
        }

        if (temp != null && temp in 16..30) {
            vehicleController.setClimateTemperature(temp)
            return strings.get(StringRes.climate_temp_set, temp)
        }

        if (text.contains("مروحة") || text.contains("سرعة") || text.contains("هواء")) {
            val fanTemp = extractNumber(text)
            if (fanTemp != null && fanTemp in 1..7) {
                vehicleController.setClimateFanLevel(fanTemp)
                return strings.get(StringRes.climate_fan_set, fanTemp)
            }
        }

        if (text.contains("تدوير") || text.contains("داخلي") || text.contains("دورة")) {
            if (text.contains("شغ") || text.contains("نعم") || text.contains("داخلي")) {
                vehicleController.setRecirculation(true)
                return strings.get(StringRes.climate_recirc_on)
            }
            if (text.contains("طفي") || text.contains("خارجي")) {
                vehicleController.setRecirculation(false)
                return strings.get(StringRes.climate_recirc_off)
            }
        }

        if (text.contains("ضباب") || text.contains("تجمد") || text.contains("ازالة")) {
            if (text.contains("شغ") || text.contains("نعم")) {
                vehicleController.setRearDefrost(true)
                return strings.get(StringRes.climate_defrost_on)
            }
            if (text.contains("طفي")) {
                vehicleController.setRearDefrost(false)
                return strings.get(StringRes.climate_defrost_off)
            }
        }

        return strings.get(StringRes.command_not_understood)
    }

    private suspend fun routeWindows(text: String): String {
        val pos = extractWindowPosition(text)
        val target = pos ?: "all"

        if (text.contains("افتح") || text.contains("شغ") || text.contains("انزل") || text.contains("فك")) {
            if (text.contains("تهوية") || text.contains("نص") || text.contains("نصف")) {
                if (target == "all") {
                    return strings.get(StringRes.windows_all_open)
                }
                return strings.get(StringRes.window_vent, target)
            }
            if (target == "all") {
                vehicleController.openWindows()
                return strings.get(StringRes.windows_all_open)
            }
            return strings.get(StringRes.window_open, target)
        }

        if (text.contains("قفل") || text.contains("أغلق") || text.contains("ارفع") || text.contains("طفي")) {
            if (target == "all") {
                vehicleController.closeWindows()
                return strings.get(StringRes.windows_all_close)
            }
            return strings.get(StringRes.window_close, target)
        }

        val pct = extractNumber(text)
        if (pct != null && pct in 0..100 && target != "all") {
            vehicleController.setWindowPosition(target, pct)
            return strings.get(StringRes.window_pos_set, target, pct)
        }

        return strings.get(StringRes.command_not_understood)
    }

    private suspend fun routeSunroof(text: String): String {
        if (text.contains("ستارة") || text.contains("غطاء")) {
            if (text.contains("افتح") || text.contains("شغ")) {
                vehicleController.openSunshade()
                return strings.get(StringRes.sunshade_open)
            }
            if (text.contains("قفل") || text.contains("طفي")) {
                vehicleController.closeSunshade()
                return strings.get(StringRes.sunshade_close)
            }
            return strings.get(StringRes.command_not_understood)
        }

        if (text.contains("ميل") || text.contains("تهوية") || text.contains("نص")) {
            vehicleController.tiltSunroof()
            return strings.get(StringRes.sunroof_tilt)
        }

        if (text.contains("افتح") || text.contains("شغ")) {
            vehicleController.openSunroof()
            return strings.get(StringRes.sunroof_open)
        }

        if (text.contains("قفل") || text.contains("طفي") || text.contains("أغلق")) {
            vehicleController.closeSunroof()
            return strings.get(StringRes.sunroof_close)
        }

        return strings.get(StringRes.command_not_understood)
    }

    private suspend fun routeDoors(text: String): String {
        if (text.contains("قفل") || text.contains("أقفل") || text.contains("اقفال") || text.contains("قفلها")) {
            vehicleController.setDoorLocks(true)
            return strings.get(StringRes.doors_locked)
        }
        if (text.contains("افتح") || text.contains("فتّح") || text.contains("افتحها") || text.contains("افتح الأبواب")) {
            vehicleController.setDoorLocks(false)
            return strings.get(StringRes.doors_unlocked)
        }
        return strings.get(StringRes.command_not_understood)
    }

    private suspend fun routeFrunk(text: String): String {
        if (text.contains("افتح") || text.contains("شغ")) {
            vehicleController.openFrunk()
            return strings.get(StringRes.frunk_open)
        }
        if (text.contains("قفل") || text.contains("طفي") || text.contains("أغلق")) {
            vehicleController.closeFrunk()
            return strings.get(StringRes.frunk_close)
        }
        return strings.get(StringRes.command_not_understood)
    }

    private suspend fun routeSeats(text: String): String {
        val seatPos = extractSeatPosition(text) ?: "driver"
        val feature = extractSeatFeature(text)
        val level = extractNumber(text)

        if (feature == "heat") {
            if (text.contains("شغ") || text.contains("نعم") || text.contains("سخن")) {
                val lvl = level?.coerceIn(1, 5) ?: 3
                vehicleController.setSeatHeat(seatPos, lvl)
                return strings.get(StringRes.seat_heat_on, seatPos)
            }
            if (text.contains("طفي") || text.contains("أطفئ")) {
                vehicleController.setSeatHeat(seatPos, 0)
                return strings.get(StringRes.seat_heat_off, seatPos)
            }
            if (level != null) {
                vehicleController.setSeatHeat(seatPos, level.coerceIn(1,5))
                return strings.get(StringRes.seat_level_set, "تدفئة", seatPos, level.coerceIn(1,5))
            }
        } else if (feature == "vent") {
            if (text.contains("شغ") || text.contains("نعم") || text.contains("تبريد")) {
                val lvl = level?.coerceIn(1, 5) ?: 3
                vehicleController.setSeatVent(seatPos, lvl)
                return strings.get(StringRes.seat_vent_on, seatPos)
            }
            if (text.contains("طفي") || text.contains("أطفئ")) {
                vehicleController.setSeatVent(seatPos, 0)
                return strings.get(StringRes.seat_vent_off, seatPos)
            }
            if (level != null) {
                vehicleController.setSeatVent(seatPos, level.coerceIn(1,5))
                return strings.get(StringRes.seat_level_set, "تهوية", seatPos, level.coerceIn(1,5))
            }
        }
        return strings.get(StringRes.command_not_understood)
    }

    private suspend fun routeSteering(text: String): String {
        if (text.contains("شغ") || text.contains("نعم") || text.contains("سخن")) {
            vehicleController.setSteeringHeat(true)
            return strings.get(StringRes.steering_heat_on)
        }
        if (text.contains("طفي") || text.contains("أطفئ")) {
            vehicleController.setSteeringHeat(false)
            return strings.get(StringRes.steering_heat_off)
        }
        return strings.get(StringRes.command_not_understood)
    }

    private suspend fun routeDriveMode(text: String): String {
        val modeMap = mapOf(
            "اقتصادي" to "ECO", "اقتصاد" to "ECO", "إيكو" to "ECO",
            "رياضي" to "SPORT", "سبورت" to "SPORT",
            "عادي" to "NORMAL", "نورمال" to "NORMAL",
            "ثلج" to "SNOW", "سنو" to "SNOW",
            "رمل" to "SAND", "طين" to "MUD", "ماد" to "MUD",
            "جبل" to "MOUNTAIN", "ذكي" to "SMART", "سمارت" to "SMART",
        )

        val mode = modeMap.entries.firstOrNull { text.contains(it.key) }?.value
        if (mode != null) {
            vehicleController.setDriveMode(mode)
            return strings.get(StringRes.drive_mode_set, mode)
        }
        return strings.get(StringRes.command_not_understood)
    }

    private suspend fun routeTelemetry(text: String): String {
        val telemetry = vehicleController.readTelemetry()
            ?: return strings.get(StringRes.command_not_understood)

        if (text.contains("كم") || text.contains("كام") || text.contains("في المية") || text.contains("النسبة") || text.contains("بطارية")) {
            return strings.get(StringRes.telemetry_soc, telemetry.soc)
        }
        if (text.contains("المدى") || text.contains("المسافة") || text.contains("كم كيلو")) {
            val estRange = (telemetry.soc * 0.5).toInt()
            return strings.get(StringRes.telemetry_range, estRange)
        }

        if (text.contains("حرارة") || text.contains("درجة") || text.contains("داخل") || text.contains("خارج")) {
            if (text.contains("خارج") || text.contains("الخارج")) {
                return strings.get(StringRes.telemetry_outside_temp, telemetry.outsideTemp)
            }
            if (text.contains("بطارية") || text.contains("بطارية")) {
                return strings.get(StringRes.telemetry_battery_temp, telemetry.maxBatTemp)
            }
            return strings.get(StringRes.telemetry_inside_temp, telemetry.insideTemp)
        }

        if (text.contains("12") || text.contains("فولت") || text.contains("بطارية صغيرة")) {
            return strings.get(StringRes.telemetry_12v, telemetry.voltage12v)
        }

        if (text.contains("ضغط") || text.contains("إطارات") || text.contains("كفر")) {
            return "ضغط الإطارات: غير متاح حالياً"
        }

        if (text.contains("شحن") || text.contains("تشحن")) {
            return "جارٍ الشحن: غير متاح حالياً"
        }

        return strings.get(StringRes.command_not_understood)
    }
}