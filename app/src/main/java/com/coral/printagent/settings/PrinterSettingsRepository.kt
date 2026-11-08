package com.coral.printagent.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.coral.printagent.model.PrinterSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "printer_settings")

class PrinterSettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore
    private val defaults = PrinterSettings()

    val settings: Flow<PrinterSettings> = store.data.map { p ->
        PrinterSettings(
            ip = p[IP] ?: defaults.ip,
            port = p[PORT] ?: defaults.port,
            drawerPin = p[PIN] ?: defaults.drawerPin,
            allowedOrigin = p[ORIGIN] ?: defaults.allowedOrigin,
            bridgeEnabled = p[BRIDGE] ?: defaults.bridgeEnabled,
        )
    }

    suspend fun save(settings: PrinterSettings) {
        store.edit { p ->
            p[IP] = settings.ip
            p[PORT] = settings.port
            p[PIN] = settings.drawerPin
            p[ORIGIN] = settings.allowedOrigin
            p[BRIDGE] = settings.bridgeEnabled
        }
    }

    private companion object {
        val IP = stringPreferencesKey("printer_ip")
        val PORT = intPreferencesKey("printer_port")
        val PIN = intPreferencesKey("drawer_pin")
        val ORIGIN = stringPreferencesKey("bridge_origin")
        val BRIDGE = booleanPreferencesKey("bridge_enabled")
    }
}
