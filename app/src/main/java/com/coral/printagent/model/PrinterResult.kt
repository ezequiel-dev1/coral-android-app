package com.coral.printagent.model

sealed interface PrinterResult {
    val message: String

    data class Success(override val message: String) : PrinterResult
    data class Failure(override val message: String) : PrinterResult
}

/** Which physical printer a job is sent to. Only [REGISTER] is wired to the cash drawer. */
enum class PrinterTarget(val label: String, val jsonName: String) {
    KITCHEN("Kitchen", "kitchen"),
    REGISTER("Register", "register");

    companion object {
        fun fromJson(value: String): PrinterTarget =
            entries.firstOrNull { it.jsonName == value.lowercase() }
                ?: throw IllegalArgumentException("\"printer\" must be \"kitchen\" or \"register\"")
    }
}

data class PrinterEndpoint(val ip: String, val port: Int = 9100)

data class PrinterSettings(
    val kitchen: PrinterEndpoint = PrinterEndpoint("10.0.0.100"),
    val register: PrinterEndpoint = PrinterEndpoint("10.0.0.101"),
    /** Cash drawer pin on the register printer. */
    val drawerPin: Int = 0,
    /** Comma-separated exact web origins allowed to call the bridge. */
    val allowedOrigin: String = DEFAULT_ORIGINS,
    val bridgeEnabled: Boolean = false,
)

fun PrinterSettings.endpoint(target: PrinterTarget): PrinterEndpoint =
    when (target) {
        PrinterTarget.KITCHEN -> kitchen
        PrinterTarget.REGISTER -> register
    }

const val DEFAULT_ORIGINS =
    "https://admin.coral.restaurant,https://admin-uat.coral.restaurant,https://192.168.1.4:3000"
