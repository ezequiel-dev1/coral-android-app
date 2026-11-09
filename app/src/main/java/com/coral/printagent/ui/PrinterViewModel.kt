package com.coral.printagent.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coral.printagent.bridge.BridgeService
import com.coral.printagent.model.PrinterEndpoint
import com.coral.printagent.model.PrinterResult
import com.coral.printagent.model.PrinterTarget
import com.coral.printagent.model.PrinterSettings
import com.coral.printagent.printer.PrinterRepository
import com.coral.printagent.settings.PrinterSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PrinterViewModel(private val app: Application) : AndroidViewModel(app) {
    private val settingsRepo = PrinterSettingsRepository(app)
    private val printerRepo = PrinterRepository(app)

    var kitchenIp by mutableStateOf("")
        private set
    var kitchenPort by mutableStateOf("")
        private set
    var registerIp by mutableStateOf("")
        private set
    var registerPort by mutableStateOf("")
        private set
    var drawerPin by mutableStateOf(0)
        private set
    var origin by mutableStateOf("")
        private set
    var bridgeEnabled by mutableStateOf(false)
        private set
    var isBusy by mutableStateOf(false)
        private set
    var status by mutableStateOf<PrinterResult?>(null)
        private set
    var kitchenConnection by mutableStateOf("Not tested")
        private set
    var registerConnection by mutableStateOf("Not tested")
        private set

    init {
        viewModelScope.launch {
            val s = settingsRepo.settings.first()
            kitchenIp = s.kitchen.ip
            kitchenPort = s.kitchen.port.toString()
            registerIp = s.register.ip
            registerPort = s.register.port.toString()
            drawerPin = s.drawerPin
            origin = s.allowedOrigin
            bridgeEnabled = s.bridgeEnabled
            if (bridgeEnabled) BridgeService.start(app)
        }
    }

    fun onIpChange(target: PrinterTarget, value: String) {
        when (target) {
            PrinterTarget.KITCHEN -> kitchenIp = value.trim()
            PrinterTarget.REGISTER -> registerIp = value.trim()
        }
    }
    fun onPortChange(target: PrinterTarget, value: String) {
        val digits = value.filter(Char::isDigit).take(5)
        when (target) {
            PrinterTarget.KITCHEN -> kitchenPort = digits
            PrinterTarget.REGISTER -> registerPort = digits
        }
    }
    fun onPinChange(value: Int) { drawerPin = value }
    fun onOriginChange(value: String) { origin = value }

    fun setBridge(enabled: Boolean) {
        if (enabled && (!originValid() || parseOrigins(origin).isEmpty())) {
            status = PrinterResult.Failure("Enter at least one web app origin first, e.g. https://app.example.com")
            return
        }
        bridgeEnabled = enabled
        if (enabled) BridgeService.start(app) else BridgeService.stop(app)
        persist(if (enabled) "Web bridge enabled on localhost:${BridgeService.PORT}" else "Web bridge disabled")
    }

    fun save() {
        if (!originValid()) {
            status = PrinterResult.Failure("Each origin must look like https://app.example.com")
            return
        }
        launchOp(persistOnly = true) { PrinterResult.Success("Settings saved") }
    }

    fun testConnection(target: PrinterTarget) =
        launchOp(target) { printerRepo.testConnection(it, target) }
    fun testPrint(target: PrinterTarget) =
        launchOp(target) { printerRepo.testPrint(it, target) }
    /** Sends the test receipt to the kitchen printer, then the register printer. */
    fun testPrintBoth() {
        if (isBusy) return
        val settings = validate() ?: return
        isBusy = true
        viewModelScope.launch {
            try {
                settingsRepo.save(settings)
                val results = PrinterTarget.entries.map { t ->
                    val r = printerRepo.testPrint(settings, t)
                    val label = if (r is PrinterResult.Success) "Connected" else "Unreachable"
                    when (t) {
                        PrinterTarget.KITCHEN -> kitchenConnection = label
                        PrinterTarget.REGISTER -> registerConnection = label
                    }
                    t to r
                }
                val summary = results.joinToString("\n") { (t, r) -> "${t.label}: ${r.message}" }
                status = if (results.all { it.second is PrinterResult.Success }) {
                    PrinterResult.Success(summary)
                } else {
                    PrinterResult.Failure(summary)
                }
            } finally {
                isBusy = false
            }
        }
    }
    fun printLogo() = launchOp(PrinterTarget.KITCHEN) { printerRepo.printLogo(it) }
    fun openDrawer() = launchOp(PrinterTarget.REGISTER) { printerRepo.openCashDrawer(it) }

    private fun persist(message: String) {
        val s = validate() ?: return
        viewModelScope.launch {
            settingsRepo.save(s)
            status = PrinterResult.Success(message)
        }
    }

    private fun launchOp(
        target: PrinterTarget? = null,
        persistOnly: Boolean = false,
        action: suspend (PrinterSettings) -> PrinterResult,
    ) {
        if (isBusy) return
        val settings = validate() ?: return
        isBusy = true
        viewModelScope.launch {
            try {
                settingsRepo.save(settings)
                val result = action(settings)
                status = result
                if (!persistOnly && target != null) {
                    val label = if (result is PrinterResult.Success) "Connected" else "Unreachable"
                    when (target) {
                        PrinterTarget.KITCHEN -> kitchenConnection = label
                        PrinterTarget.REGISTER -> registerConnection = label
                    }
                }
            } finally {
                isBusy = false
            }
        }
    }

    private fun validate(): PrinterSettings? {
        val kitchen = validateEndpoint(PrinterTarget.KITCHEN, kitchenIp, kitchenPort) ?: return null
        val register = validateEndpoint(PrinterTarget.REGISTER, registerIp, registerPort) ?: return null
        val normalized = parseOrigins(origin).joinToString(",")
        origin = normalized
        return PrinterSettings(kitchen, register, drawerPin, normalized, bridgeEnabled)
    }

    private fun validateEndpoint(target: PrinterTarget, ip: String, port: String): PrinterEndpoint? {
        if (!IPV4.matches(ip)) {
            status = PrinterResult.Failure("${target.label}: enter a valid IPv4 address, e.g. 192.168.1.100")
            return null
        }
        val p = port.toIntOrNull()
        if (p == null || p !in 1..65535) {
            status = PrinterResult.Failure("${target.label}: port must be between 1 and 65535")
            return null
        }
        return PrinterEndpoint(ip, p)
    }

    private fun parseOrigins(raw: String): List<String> =
        raw.split(Regex("[,\\s]+")).filter { it.isNotBlank() }.map { it.trimEnd('/') }.distinct()

    private fun originValid() = parseOrigins(origin).all { ORIGIN.matches(it) }

    private companion object {
        val IPV4 = Regex(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$"
        )
        val ORIGIN = Regex("^https://[A-Za-z0-9.-]+(:\\d{1,5})?$")
    }
}
