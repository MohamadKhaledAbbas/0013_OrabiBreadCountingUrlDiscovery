package com.khaledabbas.orabi.breadcounting.discovery

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import orabibreadcountingurldiscovery.composeapp.generated.resources.Res
import orabibreadcountingurldiscovery.composeapp.generated.resources.orabi_logo

// ──────────────────────────────────────────────────────────────
// Brand colors
// ──────────────────────────────────────────────────────────────
private val OrabiDarkBrown = Color(0xFF5A4A2C)
private val OrabiGold = Color(0xFFD4AF37)
private val OrabiCream = Color(0xFFF5EDDC)
private val OrabiSuccessGreen = Color(0xFF2E7D32)

// ──────────────────────────────────────────────────────────────
// Main discovery screen – RTL Arabic UI
// ──────────────────────────────────────────────────────────────

@Composable
fun DiscoveryScreen(
    state: DiscoveryState,
    onRetry: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = OrabiCream
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))

                // ── Orabi Logo ──────────────────────────────
                Image(
                    painter = painterResource(Res.drawable.orabi_logo),
                    contentDescription = "شعار عربي",
                    modifier = Modifier.size(140.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "عدّاد الخبز عربي",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = OrabiDarkBrown,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "الاتصال بلوحة العدّ",
                    style = MaterialTheme.typography.titleSmall,
                    color = OrabiDarkBrown.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(32.dp))

                // ── State content ───────────────────────────
                when (state) {
                    is DiscoveryState.Idle -> {
                        IdleContent()
                    }

                    is DiscoveryState.Discovering -> {
                        DiscoveringContent(state)
                    }

                    is DiscoveryState.Connected -> {
                        ConnectedContent()
                    }

                    is DiscoveryState.Failed -> {
                        FailureContent(state, onRetry)
                    }
                }

                Spacer(Modifier.weight(1f))

                // Footer
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "عربي لعدّ الخبز الآلي © ٢٠٢٦",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrabiDarkBrown.copy(alpha = 0.35f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Idle – initial preparing state
// ──────────────────────────────────────────────────────────────

@Composable
private fun IdleContent() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PulsingIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                "جارٍ التحضير للاتصال…",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = OrabiDarkBrown,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "يتم تجهيز النظام للبحث عن لوحة العدّ",
                style = MaterialTheme.typography.bodySmall,
                color = OrabiDarkBrown.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Discovering – step-by-step progress
// ──────────────────────────────────────────────────────────────

@Composable
private fun DiscoveringContent(state: DiscoveryState.Discovering) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Text(
                "جارٍ البحث عن لوحة العدّ…",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = OrabiDarkBrown,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "يرجى الانتظار بينما نحاول الاتصال بالجهاز",
                style = MaterialTheme.typography.bodySmall,
                color = OrabiDarkBrown.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(16.dp))

            // Completed steps
            state.completedSteps.forEach { step ->
                StepRow(
                    icon = if (step.success) "✅" else "❌",
                    label = step.label,
                    detail = step.detail,
                    isActive = false,
                    labelColor = if (step.success) OrabiSuccessGreen else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(10.dp))
            }

            // Current step
            StepRow(
                icon = "🔄",
                label = state.stepLabel,
                detail = state.stepDetail,
                isActive = true,
                labelColor = OrabiGold,
            )

            // Local scan progress
            if (state.localScanProgress != null) {
                Spacer(Modifier.height(14.dp))
                val progress = state.localScanProgress / 254f
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "تقدّم البحث",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = OrabiDarkBrown.copy(alpha = 0.7f),
                        )
                        // Switch to LTR for numbers
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                "${state.localScanProgress} / 254",
                                style = MaterialTheme.typography.labelSmall,
                                color = OrabiDarkBrown.copy(alpha = 0.5f),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = OrabiGold,
                        trackColor = OrabiCream,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "يتم فحص عناوين الشبكة المحلية بحثاً عن لوحة العدّ…",
                        style = MaterialTheme.typography.labelSmall,
                        color = OrabiDarkBrown.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Connected – success state (briefly shown before WebView loads)
// ──────────────────────────────────────────────────────────────

@Composable
private fun ConnectedContent() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("✅", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "تم الاتصال بنجاح!",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = OrabiSuccessGreen,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "جارٍ تحميل واجهة لوحة العدّ…",
                style = MaterialTheme.typography.bodyMedium,
                color = OrabiSuccessGreen.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
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
    labelColor: Color = OrabiDarkBrown,
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
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) OrabiCream.copy(alpha = 0.6f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = labelColor,
            )
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = OrabiDarkBrown.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Failure content – detailed Arabic guidance
// ──────────────────────────────────────────────────────────────

@Composable
private fun FailureContent(
    state: DiscoveryState.Failed,
    onRetry: () -> Unit,
) {
    // Error card with step results
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠️", fontSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "تعذّر الاتصال بلوحة العدّ",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFE65100),
                    )
                    Text(
                        "فشلت جميع محاولات الاتصال",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE65100).copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFE65100).copy(alpha = 0.15f))
            Spacer(Modifier.height(12.dp))

            // Step results
            Text(
                "نتائج محاولات الاتصال:",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = OrabiDarkBrown,
            )
            Spacer(Modifier.height(8.dp))

            state.completedSteps.forEach { step ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(vertical = 3.dp),
                ) {
                    Text(
                        if (step.success) "✅" else "❌",
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            step.label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = OrabiDarkBrown,
                        )
                        if (step.detail.isNotBlank()) {
                            Text(
                                step.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = OrabiDarkBrown.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // Guidance card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "💡 ماذا يمكنك أن تفعل؟",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = OrabiDarkBrown,
            )
            Spacer(Modifier.height(12.dp))
            GuidanceItem("١", "تأكد أن جهازك متصل بنفس شبكة الـ Wi-Fi أو الشبكة المحلية التي تتصل بها لوحة العدّ.")
            Spacer(Modifier.height(8.dp))
            GuidanceItem("٢", "تأكد أن لوحة العدّ تعمل وموصولة بالكهرباء والشبكة.")
            Spacer(Modifier.height(8.dp))
            GuidanceItem("٣", "إذا استمرت المشكلة، تواصل مع المشرف أو الدعم الفني.")
        }
    }

    Spacer(Modifier.height(24.dp))

    // Retry button
    Button(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OrabiGold),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
    ) {
        Text(
            "🔄  إعادة المحاولة",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }
}

@Composable
private fun GuidanceItem(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(OrabiGold.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = OrabiGold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = OrabiDarkBrown.copy(alpha = 0.75f),
            modifier = Modifier.weight(1f),
        )
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
            .background(OrabiGold),
    )
}
