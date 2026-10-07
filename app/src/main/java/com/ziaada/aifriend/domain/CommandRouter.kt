package com.ziaada.aifriend.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CommandRouter(
    private val context: Context,
    private val vehicleController: VehicleController,
    private val llmFallback: LlmFallback?,
    private val appLauncher: AppLauncher,
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
        val cleaned = Regex("[0-9٠-٩]+").find(text)?.value ?: return null
        return cleaned.replace('٠', '0').replace('١', '1').replace('٢', '2')
            .replace('٣', '3').replace('٤', '4').replace('٥', '5')
            .replace('٦', '6').replace('٧', '7').replace('٨', '8').replace('٩', '9')
            .toIntOrNull()
    }

    private fun extractNumberAfter(text: String, vararg markers: String): Int? {
        val markerIndex = markers.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return null
        return Regex("[0-9٠-٩]+").find(text.substring(markerIndex + 1))?.value
            ?.replace('٠', '0')?.replace('١', '1')?.replace('٢', '2')
            ?.replace('٣', '3')?.replace('٤', '4')?.replace('٥', '5')
            ?.replace('٦', '6')?.replace('٧', '7')?.replace('٨', '8')?.replace('٩', '9')
            ?.toIntOrNull()
    }

    private fun extractWindowPosition(text: String): String? {
        val positions = mapOf(
            "يمين خلف" to "rr", "اليمين خلف" to "rr", "يسار خلف" to "rl", "اليسار خلف" to "rl",
            "السائق" to "fl", "سائق" to "fl", "يسار" to "fl",
            "الراكب" to "fr", "راكب" to "fr", "يمين" to "fr", "الضيف" to "fr",
            "الخلف" to "rl", "خلفي" to "rl",
        )
        return positions.entries.sortedByDescending { it.key.length }.firstOrNull { text.contains(it.key) }?.value
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

    suspend fun route(command: String, vehicleConnected: Boolean): String {
        val normalized = normalize(command)

        if (normalized.contains("ساعدني") || normalized.contains("مساعدة") || normalized.contains("الأوامر")) {
            return strings.get(StringRes.help_text)
        }

        extractAppRequest(normalized)?.let { return appLauncher.launch(it).message }

        if (!vehicleConnected && isVehicleTelemetryRequest(normalized)) {
            return "مش قادر أقرأ بيانات السيارة الحية دلوقتي لأن السيارة مش متصلة."
        }

        val informationQuestion = isInformationQuestion(normalized)
        if (!vehicleConnected && !informationQuestion && isVehicleSettingIntent(normalized)) {
            return "أنا آدم، بس السيارة مش متصلة بخدمة التحكم دلوقتي. أقدر أجاوبك على سؤال عام أو أفتح تطبيق لحد ما الاتصال يرجع."
        }

        val localCommand = if (informationQuestion) null else when {
            hasAny(normalized, "مكيف", "التكييف", "كليمت", "تبريد") -> routeClimate(normalized)
            hasAny(normalized, "شباك", "شبابيك", "نافذة", "زجاج") -> routeWindows(normalized)
            hasAny(normalized, "سقف", "فتحة سقف", "ستارة سقف") -> routeSunroof(normalized)
            hasAny(normalized, "باب", "أبواب", "قفل", "اقفال") -> routeDoors(normalized)
            hasAny(normalized, "كرسي", "مقعد", "تدفئة", "تهوية كرسي") -> routeSeats(normalized)
            hasAny(normalized, "دركسيون", "المقود", "ستيرينج") -> routeSteering(normalized)
            normalized.contains("وضع قيادة") || normalized.contains("قيادة") && hasAny(normalized, "اقتصادي", "رياضي", "ثلج", "رمل", "طين", "جبل", "ذكي", "عادي") -> routeDriveMode(normalized)
            hasAny(normalized, "شنطة أمامية", "شنطة قدامي", "شنطة أمام") -> routeFrunk(normalized)
            isVehicleTelemetryRequest(normalized) -> routeTelemetry(normalized)
            else -> null
        }

        if (localCommand != null && localCommand != strings.get(StringRes.command_not_understood)) return localCommand

        return llmFallback?.query(normalized, vehicleConnected) ?: strings.get(StringRes.command_not_understood)
    }

    private fun extractAppRequest(text: String): String? {
        val match = Regex("^(?:افتح(?:ي|لي)?|شغل(?:ي|لي)?|launch|open|start)\\s+(?:(?:تطبيق|البرنامج|برنامج|app|application)\\s+)?(.+?)[.!؟?]*$")
            .matchEntire(text) ?: return null
        val app = match.groupValues[1].trim().removePrefix("ال").replace(Regex("^(?:the|a)\\s+"), "")
        if (app.isBlank() || hasAny(
                app, "باب", "أبواب", "شباك", "شبابيك", "نافذة", "زجاج", "مكيف", "التكييف", "سقف", "فتحة سقف", "شنطة", "العربية", "السيارة",
                "window", "windows", "door", "doors", "sunroof", "climate", "trunk", "frunk", "car", "vehicle",
            )
        ) {
            return null
        }
        return app
    }

    private fun isVehicleActionIntent(text: String): Boolean {
        val hasCarTarget = hasAny(
            text, "مكيف", "التكييف", "شباك", "شبابيك", "نافذة", "زجاج", "سقف", "باب", "أبواب",
            "كرسي", "مقعد", "دركسيون", "المقود", "الشنطة", "شنطة", "وضع قيادة", "السيارة",
            "window", "windows", "door", "doors", "sunroof", "climate", " air con", "trunk", "frunk", "vehicle",
        )
        val hasAction = hasAny(
            text, "افتح", "شغل", "تشغيل", "اطفي", "طفي", "اقفل", "قفل", "اغلق", "أغلق", "اضبط",
            "خلي", "ظبط", "ارفع", "نزل", "سخن", "برد", "زود", "قلل", "غير", "اختار", "set", "turn on", "turn off",
        )
        return hasCarTarget && hasAction
    }

    private fun isVehicleTelemetryRequest(text: String): Boolean =
        hasAny(text, "بطارية", "شحن", "soc", "مدى السيارة", "نسبة الشحن", "ضغط الكاوتش", "ضغط الإطارات") ||
            hasAny(text, "كم", "كام", "النسبة", "المدى", "حرارة", "ضغط") &&
            hasAny(text, "السيارة", "بطارية", "الكابينة", "الداخل", "الخارج", "الكفر", "الإطارات")

    private fun isVehicleSettingIntent(text: String): Boolean {
        if (hasAny(text, "وضع القيادة", "وضع قيادة") && hasAny(text, "اقتصادي", "اقتصاد", "ايكو", "رياضي", "سبورت", "عادي", "ثلج", "رمل", "طين", "جبل", "ذكي", "سمارت")) {
            return true
        }
        val hasTarget = hasAny(
            text, "مكيف", "التكييف", "تبريد", "شباك", "شبابيك", "نافذة", "زجاج", "سقف", "ستارة",
            "باب", "أبواب", "قفل", "كرسي", "مقعد", "تدفئة", "تهوية", "مروحة", "هواء", "تدوير",
            "ضباب", "دركسيون", "المقود", "شنطة", "السيارة", "window", "door", "climate", "sunroof", "trunk",
        )
        return hasTarget && (isVehicleActionIntent(text) || extractNumber(text) != null)
    }

    private fun isInformationQuestion(text: String): Boolean = hasAny(
        text, "ازاي", "إزاي", "كيف", "ليه", "لماذا", "هل", "ايه", "إيه", "ما هو", "ما هي", "اشرح",
        "what ", "why ", "how ", "where ", "when ", "is ", "can you", "can i", "tell me", "explain",
    )

    private fun hasAny(text: String, vararg values: String): Boolean = values.any(text::contains)

    private suspend fun routeClimate(text: String): String {
        val temp = extractNumberAfter(text, "درجة", "حرارة", "على") ?: extractNumber(text)
        val isAuto = hasAny(text, "أوتوماتيك", "أوتو", "تلقائي")
        val turnOn = hasAny(text, "شغ", "تشغيل", "فتّح", "افتح")
        val turnOff = hasAny(text, "طفي", "أطفئ", "قفل", "أوقف")
        var changed = false

        if (isAuto && (turnOn || turnOff || hasAny(text, "أوتو", "تلقائي"))) {
            vehicleController.setClimateAuto(!turnOff)
            changed = true
        } else if (turnOn || turnOff) {
            vehicleController.setClimatePower(turnOn)
            changed = true
        }
        if (temp != null && temp in 16..30) {
            vehicleController.setClimateTemperature(temp)
            changed = true
        }
        val fanLevel = if (hasAny(text, "مروحة", "سرعة", "هواء")) {
            extractNumberAfter(text, "مروحة", "سرعة", "هواء") ?: temp
        } else null
        if (fanLevel != null && fanLevel in 1..7) {
            vehicleController.setClimateFanLevel(fanLevel)
            changed = true
        }
        if (hasAny(text, "تدوير", "داخلي", "دورة") && (turnOn || turnOff || hasAny(text, "داخلي", "خارجي"))) {
            vehicleController.setRecirculation(!hasAny(text, "خارجي") && !turnOff)
            changed = true
        }
        if (hasAny(text, "ضباب", "تجمد", "ازالة") && (turnOn || turnOff)) {
            vehicleController.setRearDefrost(turnOn)
            changed = true
        }
        if (!changed) return strings.get(StringRes.command_not_understood)

        val response = mutableListOf<String>()
        if (isAuto) response += if (turnOff) "أوقفت الوضع التلقائي" else "فعلت الوضع التلقائي"
        else if (turnOn || turnOff) response += if (turnOn) "شغلت التكييف" else "طفيت التكييف"
        if (temp != null && temp in 16..30 && fanLevel == null) response += "ضبطت الحرارة على $temp درجة"
        if (fanLevel != null && fanLevel in 1..7) response += "ضبطت المروحة على المستوى $fanLevel"
        if (text.contains("تدوير")) response += if (turnOff || text.contains("خارجي")) "أوقفت التدوير الداخلي" else "فعلت التدوير الداخلي"
        if (hasAny(text, "ضباب", "تجمد", "ازالة")) response += if (turnOff) "أوقفت إزالة الضباب" else "فعلت إزالة الضباب"
        return response.joinToString("، ") + "."
    }

    private suspend fun routeWindows(text: String): String {
        val pos = extractWindowPosition(text)
        val target = pos ?: "all"

        if (text.contains("افتح") || text.contains("شغ") || text.contains("انزل") || text.contains("فك")) {
            val requestedPercent = extractNumber(text)?.takeIf { it in 0..100 }
            if (text.contains("تهوية") || text.contains("نص") || text.contains("نصف")) {
                if (target == "all") {
                    listOf("fl", "fr", "rl", "rr").forEach { vehicleController.setWindowPosition(it, 15) }
                    return "فتحت الشبابيك فتحة تهوية بسيطة"
                }
                vehicleController.setWindowPosition(target, 15)
                return strings.get(StringRes.window_vent, target)
            }
            if (target == "all") {
                if (requestedPercent == null || requestedPercent == 100) vehicleController.openWindows()
                else listOf("fl", "fr", "rl", "rr").forEach { vehicleController.setWindowPosition(it, requestedPercent) }
                return strings.get(StringRes.windows_all_open)
            }
            vehicleController.setWindowPosition(target, requestedPercent ?: 100)
            return strings.get(StringRes.window_open, target)
        }

        if (text.contains("قفل") || text.contains("أغلق") || text.contains("ارفع") || text.contains("طفي")) {
            if (target == "all") {
                vehicleController.closeWindows()
                return strings.get(StringRes.windows_all_close)
            }
            vehicleController.setWindowPosition(target, 0)
            return strings.get(StringRes.window_close, target)
        }

        val pct = extractNumber(text)
        if (pct != null && pct in 0..100) {
            if (target == "all") listOf("fl", "fr", "rl", "rr").forEach { vehicleController.setWindowPosition(it, pct) }
            else vehicleController.setWindowPosition(target, pct)
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

        if ((text.contains("ميل") || text.contains("تهوية") || text.contains("نص")) &&
            hasAny(text, "ميل", "افتح", "شغ", "ارفع", "خلي")
        ) {
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

        if (hasAny(text, "مدى", "المسافة", "range")) {
            return "قراءة المدى المتبقي مش متاحة من بيانات السيارة الحالية."
        }
        if (hasAny(text, "سرعة", "ماشي", "speed")) {
            return "سرعة السيارة ${telemetry.speedKph} كم/س."
        }
        if (hasAny(text, "قوة", "قدرة", "power")) {
            return "قدرة المحرك الحالية ${telemetry.powerKw} كيلوواط."
        }
        if (hasAny(text, "صحة البطارية", "soh")) {
            return "صحة البطارية ${telemetry.soh}٪."
        }
        if (hasAny(text, "حرارة البطارية", "حرارة البطاريه")) {
            return strings.get(StringRes.telemetry_battery_temp, telemetry.maxBatTemp)
        }
        if (hasAny(text, "خارج", "الخارج", "outside")) {
            return strings.get(StringRes.telemetry_outside_temp, telemetry.outsideTemp)
        }
        if (hasAny(text, "حرارة", "درجة", "داخل", "الكابينة", "inside")) {
            telemetry.climateSetpointC?.let { return "درجة التكييف المضبوطة $it درجة." }
            return strings.get(StringRes.telemetry_inside_temp, telemetry.insideTemp)
        }
        if (hasAny(text, "12", "فولت", "بطارية صغيرة")) {
            return strings.get(StringRes.telemetry_12v, telemetry.voltage12v)
        }
        if (hasAny(text, "ضغط", "إطارات", "كفر", "كاوتش", "tpms")) {
            if (telemetry.tirePressureKpa.isEmpty()) return "قراءات ضغط الإطارات مش متاحة حالياً."
            return telemetry.tirePressureKpa.entries.joinToString("، ") { (name, value) -> "$name: ${value.toInt()} كيلوباسكال" } + "."
        }
        if (hasAny(text, "بطارية", "شحن", "soc", "نسبة")) {
            return strings.get(StringRes.telemetry_soc, telemetry.soc)
        }
        return strings.get(StringRes.command_not_understood)
    }
}
