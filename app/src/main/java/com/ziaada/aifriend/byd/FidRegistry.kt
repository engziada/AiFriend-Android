package com.ziaada.aifriend.byd

import com.ziaada.aifriend.byd.BydConstants.DecoderType
import com.ziaada.aifriend.byd.BydConstants.Envelope

data class FidAddress(
    val deviceId: Int,
    val fid: Int,
    val decoder: String,
    val isWritable: Boolean,
    val scale: Float = 1f,
    val offset: Float = 0f,
    val minValue: Float = Float.NaN,
    val maxValue: Float = Float.NaN,
)

data class FidWrite(
    val deviceId: Int,
    val fid: Int,
    val value: Int,
)

object FidRegistry {
    private const val DEV_AC = 1000
    private const val DEV_BODYWORK = 1001
    private const val DEV_LIGHT = 1004
    private const val DEV_POWER = 1005
    private const val DEV_ENERGY = 1006
    private const val DEV_INSTRUMENT = 1007
    private const val DEV_CHARGING = 1009
    private const val DEV_GEARBOX = 1011
    private const val DEV_ENGINE = 1012
    private const val DEV_SPEED = 1013
    private const val DEV_STATISTIC = 1014
    private const val DEV_TYRE = 1016
    private const val DEV_SETTING = 1023
    private const val DEV_OTA = 1032
    private const val DEV_ADAS = 1038
    private const val DEV_GB = 1039
    private const val DEV_SAFETY_BELT = 1042
    private const val DEV_SENSOR = 1043
    private const val DEV_WIPER = 1046

    private val readFids = mutableMapOf<String, FidAddress>()
    private val writeFids = mutableMapOf<String, FidWrite>()

    init {
        registerLeopard3Baseline()
    }

    private fun registerLeopard3Baseline() {
        // ========== READ FIDs ==========

        // Climate reads (DEV_AC)
        read("ac_power_state", FidAddress(DEV_AC, 1077936144, DecoderType.INT_PERCENT, false))
        read("ac_temp_main", FidAddress(DEV_AC, 1077936168, DecoderType.INT_TEMP_C, false))
        read("ac_fan_level", FidAddress(DEV_AC, 1077936156, "INT_RAW", false))
        read("ac_cycle_mode", FidAddress(DEV_AC, 1077936148, "INT_ENUM", false))
        read("ac_inside_temp", FidAddress(DEV_AC, 1031798832, DecoderType.INT_TEMP_C, false))
        read("ac_outside_temp", FidAddress(DEV_AC, 1077936184, DecoderType.INT_TEMP_C, false))
        read("ac_defrost_front", FidAddress(DEV_AC, 1077936150, "INT_ENUM", false))
        read("ac_wind_mode", FidAddress(DEV_AC, 1077936152, "INT_ENUM", false))
        read("ac_ctrl_mode", FidAddress(DEV_AC, 1077936146, "INT_ENUM", false))
        read("ac_compressor_w", FidAddress(DEV_AC, 1031798840, "INT_RAW", false))

        // Window position reads (DEV_BODYWORK) - percent 0-100
        read("window_fl_pos", FidAddress(DEV_BODYWORK, 947912728, DecoderType.INT_PERCENT, false))
        read("window_fr_pos", FidAddress(DEV_BODYWORK, 1267728400, DecoderType.INT_PERCENT, false))
        read("window_rl_pos", FidAddress(DEV_BODYWORK, 947912736, DecoderType.INT_PERCENT, false))
        read("window_rr_pos", FidAddress(DEV_BODYWORK, 947912752, DecoderType.INT_PERCENT, false))

        // Door/lock reads
        read("door_fl_lock", FidAddress(DEV_OTA, 1081081864, "INT_ENUM", false))
        read("door_fl", FidAddress(DEV_BODYWORK, 692060168, "INT_ENUM", false))
        read("door_fr", FidAddress(DEV_BODYWORK, 692060170, "INT_ENUM", false))
        read("door_rl", FidAddress(DEV_BODYWORK, 692060172, "INT_ENUM", false))
        read("door_rr", FidAddress(DEV_BODYWORK, 692060174, "INT_ENUM", false))
        read("hood", FidAddress(DEV_BODYWORK, 692060188, "INT_ENUM", false))
        read("trunk", FidAddress(DEV_BODYWORK, 1074790416, "INT_ENUM", false))
        read("frunk", FidAddress(DEV_BODYWORK, 1156579344, "INT_ENUM", false))

        // Sunroof
        read("sunroof_pos", FidAddress(DEV_BODYWORK, 1101004808, DecoderType.INT_PERCENT, false))

        // Seat reads (DEV_AC)
        read("seat_driver_heat_level", FidAddress(DEV_AC, 702545948, "INT_RAW", false))
        read("seat_driver_vent_level", FidAddress(DEV_AC, 702545944, "INT_RAW", false))
        read("seat_pass_heat_level", FidAddress(DEV_AC, 711983132, "INT_RAW", false))
        read("seat_pass_vent_level", FidAddress(DEV_AC, 711983128, "INT_RAW", false))
        read("seat_driver_vent_status", FidAddress(DEV_AC, 702545928, "INT_ENUM", false))
        read("seat_driver_heat_status", FidAddress(DEV_AC, 702545932, "INT_ENUM", false))

        // SOC / Battery / Speed / Power (DEV_STATISTIC, DEV_SPEED, DEV_ENGINE, DEV_POWER, DEV_CHARGING)
        read("soc", FidAddress(DEV_STATISTIC, 1246777400, DecoderType.FLOAT_PERCENT, false))
        read("speed", FidAddress(DEV_SPEED, -1807745016, DecoderType.FLOAT_AMP, false)) // FLOAT_KPH in practice
        read("power_kw", FidAddress(DEV_ENGINE, 339738656, "INT_RAW", false))
        read("soh", FidAddress(DEV_STATISTIC, 1145045032, DecoderType.INT_PERCENT, false))
        read("mileage_km10", FidAddress(DEV_STATISTIC, 1246765072, "INT_SCALED", false, scale = 0.1f))
        read("total_elec_kwh", FidAddress(DEV_STATISTIC, 1032871984, DecoderType.FLOAT_KWH, false))
        read("avg_phm", FidAddress(DEV_STATISTIC, 1246761008, DecoderType.FLOAT_KWH, false))
        read("battery_remain_kwh", FidAddress(DEV_POWER, 882901008, DecoderType.FLOAT_KWH, false))
        read("voltage_12v", FidAddress(DEV_BODYWORK, 1128267816, DecoderType.FLOAT_VOLT, false))
        read("max_cell_volt", FidAddress(DEV_STATISTIC, 1147142192, "INT_SCALED", false, scale = 0.001f))
        read("min_cell_volt", FidAddress(DEV_STATISTIC, 1147142160, "INT_SCALED", false, scale = 0.001f))
        read("hv_voltage", FidAddress(DEV_CHARGING, 1145045000, "INT_RAW", false))
        read("hv_current", FidAddress(DEV_CHARGING, 1145045016, DecoderType.FLOAT_AMP, false))
        read("max_bat_temp", FidAddress(DEV_STATISTIC, 1148190752, DecoderType.INT_TEMP_C_OFS40, false))
        read("min_bat_temp", FidAddress(DEV_STATISTIC, 1148190736, DecoderType.INT_TEMP_C_OFS40, false))
        read("charge_gun_state", FidAddress(DEV_CHARGING, 876609586, "INT_ENUM", false))
        read("charging_type", FidAddress(DEV_CHARGING, 876609592, "INT_ENUM", false))
        read("bms_state", FidAddress(DEV_CHARGING, 876609560, "INT_ENUM", false))
        read("charging_capacity_kwh", FidAddress(DEV_CHARGING, 666894360, DecoderType.FLOAT_KWH, false))
        read("battery_type", FidAddress(DEV_CHARGING, -1728053169, "INT_ENUM", false))
        read("gear", FidAddress(DEV_GEARBOX, 555745336, "INT_ENUM", false))
        read("drive_mode", FidAddress(DEV_SETTING, 255852712, "INT_ENUM", false))

        // TPMS
        read("tpms_fl", FidAddress(DEV_TYRE, -1728052956, DecoderType.INT_KPA, false))
        read("tpms_fr", FidAddress(DEV_TYRE, -1728052952, DecoderType.INT_KPA, false))
        read("tpms_rl", FidAddress(DEV_TYRE, -1728052948, DecoderType.INT_KPA, false))
        read("tpms_rr", FidAddress(DEV_TYRE, -1728052944, DecoderType.INT_KPA, false))

        // ========== WRITE FIDs ==========
        // Climate writes
        write("ac_power_set", DEV_AC, 501219364)        // 1=on, 0=off
        write("ac_ctrl_mode_set", DEV_AC, 501219352)    // 0=auto, 1=manual (INVERTED!)
        write("ac_temp_main_set", DEV_AC, 501219368)    // 16..30
        write("ac_cycle_mode_set", DEV_AC, 501219355)   // 1=inner, 0=outer
        write("ac_defrost_rear_set", DEV_AC, 501219357) // 1=on, 0=off (mirror heat)
        write("ac_fan_level_set", DEV_AC, 501219340)    // 1..7
        write("ac_wind_mode_set", DEV_AC, 501219336)    // 1..5

        // Window CTRL writes (1=open, 2=close, 3=stop, 4=half, 5=vent)
        write("window_fl_ctrl", DEV_BODYWORK, 1125122104)
        write("window_fr_ctrl", DEV_BODYWORK, 1125122107)
        write("window_rl_ctrl", DEV_BODYWORK, 1125122112)
        write("window_rr_ctrl", DEV_BODYWORK, 1125122115)

        // Window percent writes (preferred path)
        write("window_fl_pct", DEV_BODYWORK, 1276219408)
        write("window_rl_pct", DEV_BODYWORK, 1276219416)
        write("window_fr_pct", DEV_BODYWORK, 1276219424)
        write("window_rr_pct", DEV_BODYWORK, 1276219432)
        write("window_pct_reset", DEV_BODYWORK, 1276219408) // reset value 255

        // Door locks
        write("doors_lock_set", DEV_BODYWORK, 1276141590) // 1=unlock, 2=lock

        // Sunroof
        write("sunroof_ctrl", DEV_BODYWORK, 1125122056)   // 1=open, 2=close, 3=tilt, 4=stop
        write("sunshade_ctrl", DEV_BODYWORK, 1125122060)  // 1=open, 2=close

        // Frunk
        write("frunk_ctrl", DEV_BODYWORK, 1276182560)     // 1=open, 3=close

        // Seats (two-write protocol: switch=1 then level=N to turn on; switch=2 to turn off)
        write("seat_driver_heat_switch", DEV_AC, 1276248084)
        write("seat_driver_heat_level", DEV_AC, 1276252180)
        write("seat_driver_vent_switch", DEV_AC, 1276248080)
        write("seat_driver_vent_level", DEV_AC, 1276252176)
        write("seat_pass_heat_switch", DEV_AC, 1276248092)
        write("seat_pass_heat_level", DEV_AC, 1276252188)
        write("seat_pass_vent_switch", DEV_AC, 1276248088)
        write("seat_pass_vent_level", DEV_AC, 1276252184)

        // Fallback seat writes (DEV_BODYWORK, single write 1=off, 2..6=lvl1..5)
        write("seat_driver_vent_fb", DEV_BODYWORK, 1125122064)
        write("seat_driver_heat_fb", DEV_BODYWORK, 1125122068)
        write("seat_pass_vent_fb", DEV_BODYWORK, 1125122072)
        write("seat_pass_heat_fb", DEV_BODYWORK, 1125122076)

        // Steering heat
        write("steering_heat_set", DEV_SETTING, 944767029) // 2=on, 1=off

        // Drive mode
        write("drive_mode_set", DEV_SETTING, 1276260400)  // 1,2,3,4,5,6,7,21

        // Interior light
        write("interior_light_set", DEV_SETTING, 1330643002) // 1=off, 2=on
        write("ambient_brightness_set", DEV_SETTING, 1069547536) // 1=off, 2..5=lvl1..4

        // Lights
        write("drl_set", DEV_LIGHT, 1125122118)         // 1=on, 2=off
        write("hazard_set", DEV_LIGHT, 871366669)       // 0=off, 1=hazard

        // Fridge
        write("fridge_mode_set", DEV_SETTING, 850427920)    // 1=cool, 2=heat, 3=off
        write("fridge_temp_set", DEV_SETTING, 850427928)    // cool: °C+19 (13..25), heat: 35..50
    }

    private fun read(name: String, addr: FidAddress) {
        readFids[name] = addr
    }

    private fun write(name: String, deviceId: Int, fid: Int) {
        writeFids[name] = FidWrite(deviceId, fid, 0)
    }

    fun getRead(name: String): FidAddress? = readFids[name]

    fun getWrite(name: String): FidWrite? = writeFids[name]

    fun allReadNames() = readFids.keys

    fun allWriteNames() = writeFids.keys

    fun updateRead(name: String, addr: FidAddress) {
        readFids[name] = addr
    }

    fun updateWrite(name: String, deviceId: Int, fid: Int) {
        writeFids[name] = FidWrite(deviceId, fid, 0)
    }
}