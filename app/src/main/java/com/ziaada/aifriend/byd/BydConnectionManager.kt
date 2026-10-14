package com.ziaada.aifriend.byd

import android.content.Context
import com.ziaada.aifriend.domain.VehicleController
import com.ziaada.aifriend.pilot.PilotLogger
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

    private var job: Job? = null

    fun start() {
        stop()
        job = scope.launch {
            var attempt = 0
            val maxAttempts = 5
            while (attempt < maxAttempts) {
                attempt++
                PilotLogger.i(TAG, "Connection attempt $attempt/$maxAttempts")
                val connected = connect()
                if (connected) {
                    attempt = 0
                    // Stay here; telemetry polling runs inside connect()
                    while (_state.value == BydConnectionState.READY || _state.value == BydConnectionState.DEGRADED) {
                        delay(2000)
                    }
                    PilotLogger.w(TAG, "Connection lost, will retry")
                } else {
                    delay(2000L * attempt)
                }
            }
            updateState(BydConnectionState.ERROR)
            PilotLogger.e(TAG, "Exceeded max connection attempts")
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        adbTransport?.disconnect()
    }

    /**
     * Revokes the current ADB authorization: stops the connection and deletes the locally
     * stored RSA key pair, so the next connect re-triggers the head unit's
     * "Allow USB debugging?" prompt.
     */
    fun revoke() {
        stop()
        runCatching {
            val keyDir = context.getDir("adb_keys", Context.MODE_PRIVATE)
            keyDir.listFiles()?.forEach { it.delete() }
            PilotLogger.i(TAG, "ADB authorization revoked (local keys cleared)")
        }.onFailure { PilotLogger.e(TAG, "Failed to clear ADB keys: ${it.message}") }
        adbTransport = null
        autoserviceClient = null
        fidCatalogManager = null
        _vehicleController = null
        updateState(BydConnectionState.DISCONNECTED)
    }

    val vehicleController: BydVehicleController?
        get() = _vehicleController

    private suspend fun connect(): Boolean {
        updateState(BydConnectionState.ADB_CONNECTING)

        val host = AdbConfig.getHost(context)
        val port = AdbConfig.getPort(context)
        PilotLogger.i(TAG, "Connecting via ADB to $host:$port")
        adbTransport = AdbTransport(context, host, port)
        if (!adbTransport!!.connect()) {
            updateState(BydConnectionState.ERROR)
            return false
        }
        updateState(BydConnectionState.ADB_AUTHENTICATING)
        // AdbTransport.connect() already performs handshake + auth; authentication success means we're on.

        updateState(BydConnectionState.DAEMON_STARTING)
        // HelperDaemon spawn is currently a no-op placeholder; assume autoservice is reachable via exec.

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
        return true
    }

    private fun updateState(state: BydConnectionState) {
        scope.launch { _state.value = state }
        PilotLogger.i(TAG, "State: $state")
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

    /** Sends a raw shell command through ADB if connected; returns output or null. */
    fun testShell(command: String): String? {
        return try {
            adbTransport?.executeCommand(command)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            PilotLogger.e(TAG, "testShell failed", e)
            null
        }
    }
}
