package com.khaledabbas.orabi.breadcounting.discovery

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ──────────────────────────────────────────────────────────────
// Main discovery screen
// ──────────────────────────────────────────────────────────────

@Composable
fun DiscoveryScreen(
    state: DiscoveryState,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.2f))

            // ── Header ──────────────────────────────────
            Text(
                text = "🍞",
                fontSize = 64.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Orabi Bread Counter",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Board Connection",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))

            // ── Content depending on state ──────────────
            when (state) {
                is DiscoveryState.Idle -> {
                    PulsingIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Preparing…", style = MaterialTheme.typography.bodyLarge)
                }

                is DiscoveryState.Discovering -> {
                    DiscoveringContent(state)
                }

                is DiscoveryState.Connected -> {
                    // The caller switches to WebView before this is visible,
                    // but just in case show a quick confirmation.
                    Text(
                        "✅  Connected!",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF2E7D32),
                    )
                }

                is DiscoveryState.Failed -> {
                    FailureContent(state, onRetry)
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Discovering sub-content
// ──────────────────────────────────────────────────────────────

@Composable
private fun DiscoveringContent(state: DiscoveryState.Discovering) {
    // Completed steps
    state.completedSteps.forEach { step ->
        StepRow(
            icon = if (step.success) "✅" else "❌",
            label = step.label,
            detail = step.detail,
            isActive = false,
        )
        Spacer(Modifier.height(8.dp))
    }

    // Current step
    StepRow(
        icon = "🔄",
        label = state.stepLabel,
        detail = state.stepDetail,
        isActive = true,
    )

    // Local scan progress bar
    if (state.localScanProgress != null) {
        Spacer(Modifier.height(12.dp))
        val progress = state.localScanProgress / 254f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${state.localScanProgress} / 254 addresses scanned",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ──────────────────────────────────────────────────────────────
// Step row
// ──────────────────────────────────────────────────────────────

@Composable
private fun StepRow(
    icon: String,
    label: String,
    detail: String,
    isActive: Boolean,
) {
    val alpha = if (isActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val a by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "alpha",
        )
        a
    } else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Failure content
// ──────────────────────────────────────────────────────────────

@Composable
private fun FailureContent(
    state: DiscoveryState.Failed,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "⚠️  Connection Failed",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(12.dp))

            state.completedSteps.forEach { step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Text(
                        if (step.success) "✅" else "❌",
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            step.label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        if (step.detail.isNotBlank()) {
                            Text(
                                step.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))

            Text(
                "Please ensure your device is connected to the same Wi-Fi / local network as the board, then try again.\n\nIf the issue persists, contact your supervisor or technical support.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Start,
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text("Retry Connection", style = MaterialTheme.typography.titleSmall)
    }
}

// ──────────────────────────────────────────────────────────────
// Pulsing loading indicator
// ──────────────────────────────────────────────────────────────

@Composable
private fun PulsingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse-indicator")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    Box(
        modifier = Modifier
            .size((24 * scale).dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}
