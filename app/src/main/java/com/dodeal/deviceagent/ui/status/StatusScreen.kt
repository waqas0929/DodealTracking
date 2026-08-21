package com.dodeal.deviceagent.ui.status

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dodeal.deviceagent.location.LocationPermissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(viewModel: StatusViewModel, onOpenDiagnostics: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Tracking starts automatically; we only need to make sure location permission is granted.
    // Request it silently on first open if it isn't already (no button, no user action needed).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }
    LaunchedEffect(Unit) {
        if (!LocationPermissions.hasForeground(context)) {
            permissionLauncher.launch(LocationPermissions.foregroundRequest())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dodeal Tracking", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
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
                    StatusRow("Device ID", state.deviceId)
                    Divider()
                    StatusRow("Assigned User", state.assignedUser)
                    Divider()
                    StatusRow("Registration", state.registrationStatus)
                    Divider()
                    StatusRow("Server Connection", state.serverConnection)
                    Divider()
                    StatusRow("Tracking Status", state.trackingStatus)
                    Divider()
                    StatusRow("Pending Uploads", state.pendingUploads.toString())
                    Divider()
                    StatusRow("Geofences", state.geofenceCount.toString())
                    Divider()
                    StatusRow("Policy Version", state.policyVersion)
                    Divider()
                    StatusRow("Last Sync", state.lastSync)
                    Divider()
                    StatusRow("Agent Version", state.agentVersion)
                    Divider()
                    StatusRow("ROM Version", state.romVersion)
                    Divider()
                    StatusRow("Runtime Mode", state.runtimeMode)
                }
            }

            Button(
                onClick = viewModel::syncNow,
                enabled = !state.loading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp).padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                }
                Text(if (state.loading) "Working…" else "Sync now")
            }

            state.message?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OutlinedButton(
                onClick = onOpenDiagnostics,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Diagnostics") }

            Text(
                "Location is collected only during authorized working hours per your company policy.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Divider() = HorizontalDivider(
    Modifier.padding(vertical = 8.dp),
    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
)

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
