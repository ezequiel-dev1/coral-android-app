package com.coral.printagent.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.coral.printagent.R
import com.coral.printagent.bridge.BridgeService
import com.coral.printagent.model.PrinterResult

@Composable
fun PrinterScreen(vm: PrinterViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .widthIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.coral_logo),
            contentDescription = "Coral Reef & Beef",
            modifier = Modifier.height(72.dp),
        )
        Text(
            "Coral Printer Bridge",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        OutlinedTextField(
            value = vm.ip,
            onValueChange = vm::onIpChange,
            label = { Text("Printer IP Address") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vm.port,
            onValueChange = vm::onPortChange,
            label = { Text("TCP Port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Cash drawer pin", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 1).forEach { pin ->
                FilterChip(
                    selected = vm.drawerPin == pin,
                    onClick = { vm.onPinChange(pin) },
                    label = { Text("Pin $pin" + if (pin == 0) " (default)" else "") },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::save, enabled = !vm.isBusy) { Text("Save") }
            OutlinedButton(onClick = vm::testConnection, enabled = !vm.isBusy) {
                Text("Test Connection")
            }
        }
        Text("Connection: ${vm.connectionLabel}", style = MaterialTheme.typography.bodyLarge)

        Text("Printer actions", style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = vm::testPrint,
            enabled = !vm.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Test Print") }
        Button(
            onClick = vm::openDrawer,
            enabled = !vm.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open Cash Drawer") }

        Text("Web bridge (Chrome on this tablet)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = vm.origin,
            onValueChange = vm::onOriginChange,
            label = { Text("Allowed web app origins (comma separated)") },
            placeholder = { Text("https://app.example.com, https://192.168.1.4:3000") },
            minLines = 2,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Switch(checked = vm.bridgeEnabled, onCheckedChange = vm::setBridge)
            Text("Listen on http://127.0.0.1:${BridgeService.PORT}")
        }

        if (vm.isBusy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                Text("Working...")
            }
        }

        vm.status?.let { result ->
            Text(
                text = result.message,
                color = if (result is PrinterResult.Failure) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
