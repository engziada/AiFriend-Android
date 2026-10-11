package com.ziaada.aifriend.domain

import android.content.Context
import android.content.res.Resources
import com.ziaada.aifriend.R

class StringRes(private val context: Context) {
    private val res: Resources = context.resources

    fun get(id: Int): String = res.getString(id)
    fun get(id: Int, vararg formatArgs: Any): String = res.getString(id, *formatArgs)

    companion object {
        val voice_ready = R.string.voice_ready
        val voice_listening = R.string.voice_listening
        val voice_thinking = R.string.voice_thinking
        val voice_error = R.string.voice_error
        val help_text = R.string.help_text
        val command_not_understood = R.string.command_not_understood

        val climate_on = R.string.climate_on
        val climate_off = R.string.climate_off
        val climate_auto_on = R.string.climate_auto_on
        val climate_temp_set = R.string.climate_temp_set
        val climate_fan_set = R.string.climate_fan_set
        val climate_recirc_on = R.string.climate_recirc_on
        val climate_recirc_off = R.string.climate_recirc_off
        val climate_defrost_on = R.string.climate_defrost_on
        val climate_defrost_off = R.string.climate_defrost_off

        val windows_all_open = R.string.windows_all_open
        val windows_all_close = R.string.windows_all_close
        val window_open = R.string.window_open
        val window_close = R.string.window_close
        val window_vent = R.string.window_vent
        val window_pos_set = R.string.window_pos_set

        val sunroof_open = R.string.sunroof_open
        val sunroof_close = R.string.sunroof_close
        val sunroof_tilt = R.string.sunroof_tilt
        val sunshade_open = R.string.sunshade_open
        val sunshade_close = R.string.sunshade_close

        val doors_locked = R.string.doors_locked
        val doors_unlocked = R.string.doors_unlocked
        val frunk_open = R.string.frunk_open
        val frunk_close = R.string.frunk_close

        val seat_heat_on = R.string.seat_heat_on
        val seat_heat_off = R.string.seat_heat_off
        val seat_vent_on = R.string.seat_vent_on
        val seat_vent_off = R.string.seat_vent_off
        val seat_level_set = R.string.seat_level_set

        val steering_heat_on = R.string.steering_heat_on
        val steering_heat_off = R.string.steering_heat_off

        val drive_mode_set = R.string.drive_mode_set

        val telemetry_soc = R.string.telemetry_soc
        val telemetry_range = R.string.telemetry_range
        val telemetry_inside_temp = R.string.telemetry_inside_temp
        val telemetry_outside_temp = R.string.telemetry_outside_temp
        val telemetry_battery_temp = R.string.telemetry_battery_temp
        val telemetry_12v = R.string.telemetry_12v
        val telemetry_tpms = R.string.telemetry_tpms
        val telemetry_charging = R.string.telemetry_charging

        val confirm_action = R.string.confirm_action
        val action_cancelled = R.string.action_cancelled
        val llm_thinking = R.string.llm_thinking
        val llm_error = R.string.llm_error
        val llm_offline = R.string.llm_offline
    }
}
