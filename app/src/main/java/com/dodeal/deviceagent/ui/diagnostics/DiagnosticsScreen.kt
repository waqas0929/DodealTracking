package com.dodeal.deviceagent.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dodeal.deviceagent.health.DeviceHealth
import com.dodeal.deviceagent.health.DeviceHealthProvider
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(healthProvider: DeviceHealthProvider, onBack: () -> Unit) {
    var health by remember { mutableStateOf(healthProvider.snapshot()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    YesNo("Privileged App", health.privileged)
                    Grant("Fine Location", health.fineLocation)
                    Grant("Coarse Location", health.coarseLocation)
                    Grant("Background Location", health.backgroundLocation)
                    Grant("Privileged Phone State", health.privilegedPhoneState)
                    YesNo("Battery Optimization Exempt", health.batteryExempt)
                    Ready("Foreground Location Service", health.foregroundServiceReady)
                    Ready("Boot Receiver", health.bootReceiverReady)
                    Conn("Backend Connection", health.backendConnected)
                    Sync("Policy Sync", health.policySynced, health.policyStale)
                    Plain("Session State", health.sessionState.name)
                    Plain("Last Location", lastLoc(health))
                    Plain("Last Upload", health.lastUploadAt?.let(::fmt) ?: "never")
                    Plain("Offline Queue", health.offlineQueueSize.toString())
                    Plain("ROM Version", health.romVersion)
                    Plain("App Version", health.appVersion, last = health.lastError == null)
                    health.lastError?.let { Plain("Last Error", it, last = true) }
                }
            }
            OutlinedButton(
                onClick = { health = healthProvider.snapshot() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Refresh") }
        }
    }
}

private fun lastLoc(h: DeviceHealth): String {
    val at = h.lastLocationAt?.let(::fmt) ?: "never"
    val acc = h.lastLocationAccuracy?.let { " · ±${"%.0f".format(it)}m" } ?: ""
    return at + acc
}

private fun fmt(instant: java.time.Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant)

private val GREEN = Color(0xFF1B8A3A)
private val RED = Color(0xFFB3261E)
private val AMBER = Color(0xFFB26A00)

@Composable private fun YesNo(label: String, v: Boolean, last: Boolean = false) = Line(label, if (v) "YES" else "NO", if (v) GREEN else RED, last)
@Composable private fun Grant(label: String, v: Boolean, last: Boolean = false) = Line(label, if (v) "GRANTED" else "NOT GRANTED", if (v) GREEN else RED, last)
@Composable private fun Ready(label: String, v: Boolean, last: Boolean = false) = Line(label, if (v) "READY" else "NOT READY", if (v) GREEN else RED, last)
@Composable private fun Conn(label: String, v: Boolean, last: Boolean = false) = Line(label, if (v) "CONNECTED" else "NOT CONNECTED", if (v) GREEN else RED, last)
@Composable private fun Plain(label: String, value: String, last: Boolean = false) =
    Line(label, value, MaterialTheme.colorScheme.onSurfaceVariant, last)

@Composable
private fun Sync(label: String, synced: Boolean, stale: Boolean, last: Boolean = false) {
    val (text, color) = when {
        !synced -> "NOT SYNCED" to RED
        stale -> "SYNCED (STALE)" to AMBER
        else -> "SYNCED" to GREEN
    }
    Line(label, text, color, last)
}

@Composable
private fun Line(label: String, value: String, valueColor: Color, last: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = valueColor, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
    if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}
