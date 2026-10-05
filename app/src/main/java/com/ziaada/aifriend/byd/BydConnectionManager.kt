package com.ziaada.aifriend.byd

import android.content.Context
import android.util.Log
import com.ziaada.aifriend.domain.VehicleController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

enum class BydConnectionState {
    DISCONNECTED,
    ADB_CONNECTING,
    ADB_AUTHENTICATING,
    DAEMON_STARTING,
    FID_SYNCING,
    READY,
    DEGRADED,
    ERROR,
}

class BydConnectionManager(
    private val context: Context,
) {
    private val TAG = "BydConnectionManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var adbTransport: AdbTransport? = null
    private var autoserviceClient: AutoserviceClient? = null
    private var fidCatalogManager: FidCatalogManager? = null
    private var _vehicleController: BydVehicleController? = null

    private val _state = MutableStateFlow<BydConnectionState>(BydConnectionState.DISCONNECTED)
    val stateFlow = _state.asSharedFlow()

    private val _telemetry = MutableSharedFlow<VehicleController.Telemetry>(replay = 1)
    val telemetryFlow = _telemetry.asSharedFlow()

    fun start() {
        scope.launch {
            connect()
        }
    }

    fun stop() {
        scope.coroutineContext[Job]?.cancel()
        adbTransport?.disconnect()
    }

    val vehicleController: BydVehicleController?
        get() = _vehicleController

    private suspend fun connect() {
        updateState(BydConnectionState.ADB_CONNECTING)

        adbTransport = AdbTransport(context)
        if (!adbTransport!!.connect()) {
            updateState(BydConnectionState.ERROR)
            return
        }

        updateState(BydConnectionState.ADB_AUTHENTICATING)

        updateState(BydConnectionState.DAEMON_STARTING)

        updateState(BydConnectionState.FID_SYNCING)
        autoserviceClient = AutoserviceClient(context, adbTransport!!)
        fidCatalogManager = FidCatalogManager(context, autoserviceClient!!)

        val syncSuccess = fidCatalogManager!!.syncCatalog()
        if (!syncSuccess) {
            updateState(BydConnectionState.DEGRADED)
        } else {
            updateState(BydConnectionState.READY)
        }

        _vehicleController = BydVehicleController(autoserviceClient!!)

        startTelemetryPolling()
    }

    private fun updateState(state: BydConnectionState) {
        scope.launch { _state.value = state }
        Log.d(TAG, "State: $state")
    }

    private fun startTelemetryPolling() {
        scope.launch {
            while (true) {
                val telemetry = _vehicleController?.readTelemetry()
                telemetry?.let { _telemetry.tryEmit(it) }
                delay(1000)
            }
        }
    }

    fun isReady(): Boolean {
        return _state.value == BydConnectionState.READY
    }
}