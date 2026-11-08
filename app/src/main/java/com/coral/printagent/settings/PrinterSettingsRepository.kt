package com.coral.printagent.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.coral.printagent.model.PrinterEndpoint
import com.coral.printagent.model.PrinterSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "printer_settings")

class PrinterSettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore
    private val defaults = PrinterSettings()

    val settings: Flow<PrinterSettings> = store.data.map { p ->
        PrinterSettings(
            kitchen = PrinterEndpoint(
                ip = p[IP] ?: defaults.kitchen.ip,
                port = p[PORT] ?: defaults.kitchen.port,
            ),
            register = PrinterEndpoint(
                ip = p[REGISTER_IP] ?: defaults.register.ip,
                port = p[REGISTER_PORT] ?: defaults.register.port,
            ),
            drawerPin = p[PIN] ?: defaults.drawerPin,
            allowedOrigin = p[ORIGIN] ?: defaults.allowedOrigin,
            bridgeEnabled = p[BRIDGE] ?: defaults.bridgeEnabled,
        )
    }

    suspend fun save(settings: PrinterSettings) {
        store.edit { p ->
            p[IP] = settings.kitchen.ip
            p[PORT] = settings.kitchen.port
            p[REGISTER_IP] = settings.register.ip
            p[REGISTER_PORT] = settings.register.port
            p[PIN] = settings.drawerPin
            p[ORIGIN] = settings.allowedOrigin
            p[BRIDGE] = settings.bridgeEnabled
        }
    }

    private companion object {
        // Legacy single-printer keys now hold the kitchen printer.
        val IP = stringPreferencesKey("printer_ip")
        val PORT = intPreferencesKey("printer_port")
        val REGISTER_IP = stringPreferencesKey("register_ip")
        val REGISTER_PORT = intPreferencesKey("register_port")
        val PIN = intPreferencesKey("drawer_pin")
        val ORIGIN = stringPreferencesKey("bridge_origin")
        val BRIDGE = booleanPreferencesKey("bridge_enabled")
    }
}
