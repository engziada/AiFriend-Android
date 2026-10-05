package com.ziaada.aifriend.domain

import android.content.Context
import android.content.res.Resources

class StringRes(private val context: Context) {
    private val res: Resources = context.resources

    fun get(id: Int): String = res.getString(id)
    fun get(id: Int, vararg formatArgs: Any): String = res.getString(id, *formatArgs)

    companion object {
        const val voice_ready = 0x7f040000
        const val voice_listening = 0x7f040001
        const val voice_thinking = 0x7f040002
        const val voice_error = 0x7f040003
        const val help_text = 0x7f040004
        const val command_not_understood = 0x7f040005

        const val climate_on = 0x7f040006
        const val climate_off = 0x7f040007
        const val climate_auto_on = 0x7f040008
        const val climate_temp_set = 0x7f040009
        const val climate_fan_set = 0x7f04000a
        const val climate_recirc_on = 0x7f04000b
        const val climate_recirc_off = 0x7f04000c
        const val climate_defrost_on = 0x7f04000d
        const val climate_defrost_off = 0x7f04000e

        const val windows_all_open = 0x7f04000f
        const val windows_all_close = 0x7f040010
        const val window_open = 0x7f040011
        const val window_close = 0x7f040012
        const val window_vent = 0x7f040013
        const val window_pos_set = 0x7f040014

        const val sunroof_open = 0x7f040015
        const val sunroof_close = 0x7f040016
        const val sunroof_tilt = 0x7f040017
        const val sunshade_open = 0x7f040018
        const val sunshade_close = 0x7f040019

        const val doors_locked = 0x7f04001a
        const val doors_unlocked = 0x7f04001b
        const val frunk_open = 0x7f04001c
        const val frunk_close = 0x7f04001d

        const val seat_heat_on = 0x7f04001e
        const val seat_heat_off = 0x7f04001f
        const val seat_vent_on = 0x7f040020
        const val seat_vent_off = 0x7f040021
        const val seat_level_set = 0x7f040022

        const val steering_heat_on = 0x7f040023
        const val steering_heat_off = 0x7f040024

        const val drive_mode_set = 0x7f040025

        const val telemetry_soc = 0x7f040026
        const val telemetry_range = 0x7f040027
        const val telemetry_inside_temp = 0x7f040028
        const val telemetry_outside_temp = 0x7f040029
        const val telemetry_battery_temp = 0x7f04002a
        const val telemetry_12v = 0x7f04002b
        const val telemetry_tpms = 0x7f04002c
        const val telemetry_charging = 0x7f04002d

        const val confirm_action = 0x7f04002e
        const val action_cancelled = 0x7f04002f
        const val llm_thinking = 0x7f040030
        const val llm_error = 0x7f040031
        const val llm_offline = 0x7f040032
    }
}