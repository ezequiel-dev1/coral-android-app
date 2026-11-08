package com.coral.printagent.model

sealed interface PrinterResult {
    val message: String

    data class Success(override val message: String) : PrinterResult
    data class Failure(override val message: String) : PrinterResult
}

data class PrinterSettings(
    val ip: String = "192.168.1.100",
    val port: Int = 9100,
    val drawerPin: Int = 0,
    /** Comma-separated exact web origins allowed to call the bridge. */
    val allowedOrigin: String = DEFAULT_ORIGINS,
    val bridgeEnabled: Boolean = false,
)

const val DEFAULT_ORIGINS =
    "https://admin.coral.restaurant,https://admin-uat.coral.restaurant,https://192.168.1.4:3000"
