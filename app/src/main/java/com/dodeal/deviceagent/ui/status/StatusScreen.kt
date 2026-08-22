package com.dodeal.deviceagent.ui.status

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
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
        // Connect on open. "Sync now" stays, but as an override — a tracking device that only
        // reaches the server when somebody remembers to tap a button is not a tracking device.
        viewModel.syncInBackground()
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
            // The one thing a person opening this app wants to know, and the reason behind it.
            // Everything below is detail; this is the answer.
            TrackingHeroCard(state)

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
                Text(if (state.loading) "Syncing…" else "Sync now")
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

/**
 * A label and its value.
 *
 * Both sides are weighted and the value is right-aligned. Without that, a long value — the ROM
 * version is 30 characters — ran straight into the label with no gap at all, which is what the
 * screen looked like on a real S23.
 */
@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            // 45/55 in the value's favour. Labels top out at "Server Connection" and fit on one
            // line; values run to a 30-character ROM build string, which is allowed to wrap.
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            value,
            modifier = Modifier.weight(1.3f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * "Is it tracking, and if not why not" — stated once, at the top, in the colour of the answer.
 *
 * Green only when location is genuinely being recorded. Amber for a pause that is expected and will
 * end by itself (outside working hours). Red for something a person has to fix.
 */
@Composable
private fun TrackingHeroCard(state: StatusUiState) {
    val accent = when {
        state.trackingActive -> Color(0xFF1B873F)
        state.needsAttention -> MaterialTheme.colorScheme.error
        else -> Color(0xFFB26B00)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    state.trackingStatus,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (state.trackingReason.isNotBlank()) {
                Spacer(Modifier.size(6.dp))
                Text(
                    state.trackingReason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
