package com.ziaada.aifriend.byd

import com.ziaada.aifriend.domain.VehicleController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BydVehicleController(
    private val autoserviceClient: AutoserviceClient,
) : VehicleController {

    override suspend fun setClimatePower(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            val write = FidRegistry.getWrite("ac_power_set") ?: return@withContext
            autoserviceClient.writeInt(write.deviceId, write.fid, if (enabled) 1 else 0)
        }
    }

    override suspend fun setClimateTemperature(celsius: Int) {
        withContext(Dispatchers.IO) {
            val temp = celsius.coerceIn(16, 30)
            val write = FidRegistry.getWrite("ac_temp_main_set") ?: return@withContext
            autoserviceClient.writeInt(write.deviceId, write.fid, temp)
        }
    }

    override suspend fun openWindows() {
        withContext(Dispatchers.IO) {
            listOf(
                "window_fl_ctrl" to 1,
                "window_fr_ctrl" to 1,
                "window_rl_ctrl" to 1,
                "window_rr_ctrl" to 1,
            ).forEach { (name, value) ->
                FidRegistry.getWrite(name)?.let { w ->
                    autoserviceClient.writeInt(w.deviceId, w.fid, value)
                }
            }
        }
    }

    override suspend fun closeWindows() {
        withContext(Dispatchers.IO) {
            listOf(
                "window_fl_ctrl" to 2,
                "window_fr_ctrl" to 2,
                "window_rl_ctrl" to 2,
                "window_rr_ctrl" to 2,
            ).forEach { (name, value) ->
                FidRegistry.getWrite(name)?.let { w ->
                    autoserviceClient.writeInt(w.deviceId, w.fid, value)
                }
            }
        }
    }

    override suspend fun getSocPercent(): Int? {
        return withContext(Dispatchers.IO) {
            val read = FidRegistry.getRead("soc") ?: return@withContext null
            val raw = autoserviceClient.readInt(read.deviceId, read.fid)
            if (autoserviceClient.isSentinel("soc", raw)) return@withContext null
            autoserviceClient.decodeValue("soc", raw) as? Int
        }
    }

    override suspend fun setClimateAuto(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            val write = FidRegistry.getWrite("ac_ctrl_mode_set") ?: return@withContext
            autoserviceClient.writeInt(write.deviceId, write.fid, if (enabled) 0 else 1)
        }
    }

    override suspend fun setClimateFanLevel(level: Int) {
        withContext(Dispatchers.IO) {
            val write = FidRegistry.getWrite("ac_fan_level_set") ?: return@withContext
            autoserviceClient.writeInt(write.deviceId, write.fid, level.coerceIn(1, 7))
        }
    }

    override suspend fun setRecirculation(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            val write = FidRegistry.getWrite("ac_cycle_mode_set") ?: return@withContext
            autoserviceClient.writeInt(write.deviceId, write.fid, if (enabled) 1 else 0)
        }
    }

    override suspend fun setRearDefrost(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            val write = FidRegistry.getWrite("ac_defrost_rear_set") ?: return@withContext
            autoserviceClient.writeInt(write.deviceId, write.fid, if (enabled) 1 else 0)
        }
    }

    override suspend fun setWindowPosition(window: String, percent: Int) {
        withContext(Dispatchers.IO) {
            val (pctWrite, resetWrite) = when (window) {
                "fl" -> "window_fl_pct" to "window_fl_pct"
                "fr" -> "window_fr_pct" to "window_fr_pct"
                "rl" -> "window_rl_pct" to "window_rl_pct"
                "rr" -> "window_rr_pct" to "window_rr_pct"
                else -> return@withContext
            }
            val pct = percent.coerceIn(0, 100)
            val pctResult = FidRegistry.getWrite(pctWrite)?.let { w ->
                autoserviceClient.writeInt(w.deviceId, w.fid, pct)
            } ?: false
            Thread.sleep(300)
            val resetResult = FidRegistry.getWrite(resetWrite)?.let { w ->
                autoserviceClient.writeInt(w.deviceId, w.fid, 255)
            } ?: false
            // Return value not used
        }
    }

    override suspend fun openSunroof() {
        withContext(Dispatchers.IO) {
            FidRegistry.getWrite("sunroof_ctrl")?.let { w ->
                autoserviceClient.writeInt(w.deviceId, w.fid, 1)
            }
        }
    }

    override suspend fun closeSunroof() {
        withContext(Dispatchers.IO) {
            FidRegistry.getWrite("sunroof_ctrl")?.let { w ->
                autoserviceClient.writeInt(w.deviceId, w.fid, 2)
            }
        }
    }

    override suspend fun tiltSunroof() {
        withContext(Dispatchers.IO) {
            FidRegistry.getWrite("sunroof_ctrl")?.let { w ->
                autoserviceClient.writeInt(w.deviceId, w.fid, 3)
            }
        }
    }

    override suspend fun openSunshade() {
        withContext(Dispatchers.IO) {
            FidRegistry.getWrite("sunshade_ctrl")?.let { w ->
                autoserviceClient.writeInt(w.deviceId, w.fid, 1)
            }
        }
    }

    override suspend fun closeSunshade() {
        withContext(Dispatchers.IO) {
            FidRegistry.getWrite("sunshade_ctrl")?.let { w ->
                autoserviceClient.writeInt(w.deviceId, w.fid, 2)
            }
        }
    }

    override suspend fun setDoorLocks(lock: Boolean) {
        withContext(Dispatchers.IO) {
            FidRegistry.getWrite("doors_lock_set")?.let { w ->
                autoserviceClient.writeInt(w.deviceId, w.fid, if (lock) 2 else 1)
            }
        }
    }

    override suspend fun openFrunk() {
        withContext(Dispatchers.IO) {
            FidRegistry.getWrite("frunk_ctrl")?.let { w ->
                autoserviceClient.writeInt(w.deviceId, w.fid, 1)
            }
        }
    }

    override suspend fun closeFrunk() {
        withContext(Dispatchers.IO) {
            FidRegistry.getWrite("frunk_ctrl")?.let { w ->
                autoserviceClient.writeInt(w.deviceId, w.fid, 3)
            }
        }
    }

    override suspend fun setSeatHeat(position: String, level: Int) {
        // TODO: implement seat heat
    }

    override suspend fun setSeatVent(position: String, level: Int) {
        // TODO: implement seat vent
    }

    override suspend fun setSteeringHeat(enabled: Boolean) {
        // TODO: implement steering heat
    }

    override suspend fun setDriveMode(mode: String) {
        // TODO: implement drive mode
    }

    override suspend fun readTelemetry(): VehicleController.Telemetry? {
        return withContext(Dispatchers.IO) {
            val reads = mapOf(
                "soc" to FidRegistry.getRead("soc"),
                "speed" to FidRegistry.getRead("speed"),
                "power_kw" to FidRegistry.getRead("power_kw"),
                "soh" to FidRegistry.getRead("soh"),
                "inside_temp" to FidRegistry.getRead("ac_inside_temp"),
                "outside_temp" to FidRegistry.getRead("ac_outside_temp"),
                "voltage_12v" to FidRegistry.getRead("voltage_12v"),
                "max_bat_temp" to FidRegistry.getRead("max_bat_temp"),
                "min_bat_temp" to FidRegistry.getRead("min_bat_temp"),
            ).mapValues { (_, addr) ->
                addr?.let { r ->
                    val raw = if (r.decoder.startsWith("FLOAT")) {
                        autoserviceClient.readFloat(r.deviceId, r.fid)
                    } else {
                        autoserviceClient.readInt(r.deviceId, r.fid).toFloat()
                    }
                    if (autoserviceClient.isSentinel(r.decoder, raw.toInt())) null
                    else autoserviceClient.decodeValue(r.decoder, raw.toInt())
                }
            }.filterValues { it != null }

            if (reads.isEmpty()) return@withContext null

            VehicleController.Telemetry(
                soc = (reads["soc"] as? Float ?: 0f).toInt(),
                speedKph = (reads["speed"] as? Float ?: 0f),
                powerKw = (reads["power_kw"] as? Float ?: 0f),
                soh = (reads["soh"] as? Int ?: 0),
                insideTemp = (reads["inside_temp"] as? Float ?: 0f),
                outsideTemp = (reads["outside_temp"] as? Float ?: 0f),
                voltage12v = (reads["voltage_12v"] as? Float ?: 0f),
                maxBatTemp = (reads["max_bat_temp"] as? Float ?: 0f),
                minBatTemp = (reads["min_bat_temp"] as? Float ?: 0f),
            )
        }
    }
}