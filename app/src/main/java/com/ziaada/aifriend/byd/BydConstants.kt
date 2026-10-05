package com.ziaada.aifriend.byd

object BydConstants {
    const val AUTOSERVICE_NAME = "autoservice"
    const val HELPER_SERVICE_NAME = "aifriend_helper"
    const val HELPER_INTERFACE = "com.ziaada.aifriend.byd.IHelper"
    const val ADB_HOST = "127.0.0.1"
    const val ADB_PORT = 5555
    const val ADB_AUTH_TIMEOUT_MS = 60000
    const val ADB_SOCKET_TIMEOUT_MS = 5000
    const val HELPER_SPAWN_TIMEOUT_MS = 10000
    const val FID_DUMP_CHUNK_MAX = 65536
    const val MAX_BATCH_ITEMS = 128

    object TransactionCode {
        const val GET_INT = 5
        const val SET_INT = 6
        const val GET_FLOAT = 7
        const val GET_BUFFER = 9
        const val SET_BUFFER = 14
    }

    object HelperTransaction {
        const val TX_PING = 1
        const val TX_READ = 2
        const val TX_WRITE = 3
        const val TX_READ_BATCH = 21
        const val TX_DUMP_FIDS = 32
        const val TX_WRITE_BUFFER = 57
        const val TX_GET_VERSION = 29
        const val TX_REGISTER_CLIENT = 44
    }

    object Sentinel {
        const val FEATURE_LINK_ERROR = 0x0000FFFF
        const val NOT_INITIALIZED_20BIT = 0x000FFFFF
        const val WRONG_TRANSACT_CODE = -10013
        const val NOT_WRITABLE = -10011
        const val FLOAT_NOT_INIT = -1.0f
    }

    object DecoderType {
        const val INT_TEMP_C = "INT_TEMP_C"
        const val INT_TEMP_C_OFS40 = "INT_TEMP_C_OFS40"
        const val INT_SCALED = "INT_SCALED"
        const val INT_PERCENT = "INT_PERCENT"
        const val INT_KPA = "INT_KPA"
        const val FLOAT_PERCENT = "FLOAT_PERCENT"
        const val FLOAT_VOLT = "FLOAT_VOLT"
        const val FLOAT_AMP = "FLOAT_AMP"
        const val FLOAT_KWH = "FLOAT_KWH"
        const val FLOAT_TEMP = "FLOAT_TEMP"
    }

    object Envelope {
        const val INT_TEMP_C_MIN = -50
        const val INT_TEMP_C_MAX = 80
        const val INT_TEMP_C_OFS40_MIN = -40
        const val INT_TEMP_C_OFS40_MAX = 120
        const val INT_PERCENT_MIN = 0
        const val INT_PERCENT_MAX = 100
        const val INT_KPA_MIN = 0
        const val INT_KPA_MAX = 600
        const val FLOAT_PERCENT_MIN = 0f
        const val FLOAT_PERCENT_MAX = 100f
        const val FLOAT_VOLT_MIN = 0f
        const val FLOAT_VOLT_MAX = 60f
    }
}