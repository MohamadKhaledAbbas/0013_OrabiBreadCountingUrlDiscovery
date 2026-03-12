package com.khaledabbas.orabi.breadcounting.discovery

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
// Brand palette – Ocean Blue
// ──────────────────────────────────────────────────────────────
private val OrabiDarkBlue = Color(0xFF0D253F)
private val OrabiBlue = Color(0xFF1976D2)
private val OrabiLightBg = Color(0xFFF0F4FA)
private val OrabiSuccessGreen = Color(0xFF2E7D32)
private val OrabiErrorRed = Color(0xFFC62828)
private val OrabiWarnOrange = Color(0xFFE65100)
private val OrabiLightBlue = Color(0xFFBBDEFB)

// ──────────────────────────────────────────────────────────────
// All three discovery phases – used by the stepper
// ──────────────────────────────────────────────────────────────
private data class PhaseInfo(val label: String, val pendingDetail: String)

private val AllPhases = listOf(
    PhaseInfo(ArabicLabels.STEP_LOCAL, "فحص الشبكة المحلية…"),
    PhaseInfo(ArabicLabels.STEP_CACHED, "البحث في الاتصالات المحفوظة…"),
    PhaseInfo(ArabicLabels.STEP_CLOUD, "الاتصال بالخادم السحابي…"),
)

private fun phaseIndex(label: String): Int = when (label) {
    ArabicLabels.STEP_LOCAL -> 0
    ArabicLabels.STEP_CACHED -> 1
    ArabicLabels.STEP_CLOUD -> 2
    else -> -1
}

// ──────────────────────────────────────────────────────────────
// Main discovery screen – RTL Arabic UI
// ──────────────────────────────────────────────────────────────

@Composable
fun DiscoveryScreen(
    state: DiscoveryState,
    onRetry: () -> Unit,
    onOpenBoard: () -> Unit = {},
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = OrabiLightBg,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(16.dp))

                // ── Logo + title ────────────────────────
                Image(
                    painter = painterResource(Res.drawable.orabi_logo),
                    contentDescription = "شعار عرابي",
                    modifier = Modifier.size(120.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "إحصاء الخبز",
                    style = MaterialTheme.typography.headlineSmall
                        .copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp),
                    color = OrabiDarkBlue,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "الاتصال بلوحة العدّ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OrabiDarkBlue.copy(alpha = 0.55f),
                )
                Spacer(Modifier.height(28.dp))

                // ── Content area with cross-fade ──────────
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                    },
                    contentKey = { it::class },
                    label = "state-transition",
                ) { target ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (target) {
                            is DiscoveryState.Idle -> IdleContent()
                            is DiscoveryState.Discovering -> DiscoveringContent(target)
                            is DiscoveryState.Connected -> ConnectedContent(target, onOpenBoard, onRetry)
                            is DiscoveryState.Failed -> FailureContent(target, onRetry)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Spacer(Modifier.height(24.dp))
                Text(
                    text = "منظومة إحصاءالخبز © ٢٠٢٦",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrabiDarkBlue.copy(alpha = 0.3f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Idle – preparing
// ──────────────────────────────────────────────────────────────

@Composable
private fun IdleContent() {
    StatusCard(containerColor = Color.White) {
        Spacer(Modifier.height(8.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            color = OrabiBlue,
            strokeWidth = 3.dp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "جارٍ التحضير للاتصال…",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = OrabiDarkBlue,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "يتم تجهيز النظام للبحث عن لوحة العدّ",
            style = MaterialTheme.typography.bodySmall,
            color = OrabiDarkBlue.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
    }
}

// ──────────────────────────────────────────────────────────────
// Discovering – vertical stepper
// ──────────────────────────────────────────────────────────────

@Composable
private fun DiscoveringContent(state: DiscoveryState.Discovering) {
    val activeIdx = phaseIndex(state.stepLabel)
    val resultMap = state.completedSteps.associateBy { it.label }

    StatusCard(containerColor = Color.White) {
        Text(
            "جارٍ البحث عن لوحة العدّ",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = OrabiDarkBlue,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "يرجى الانتظار بينما نحاول الاتصال بالجهاز",
            style = MaterialTheme.typography.bodySmall,
            color = OrabiDarkBlue.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        AllPhases.forEachIndexed { idx, phase ->
            val result = resultMap[phase.label]
            val isCurrent = idx == activeIdx

            StepperRow(
                isLast = idx == AllPhases.lastIndex,
                status = when {
                    result?.success == true -> StepStatus.Success
                    result?.success == false -> StepStatus.Failed
                    isCurrent -> StepStatus.Active
                    else -> StepStatus.Pending
                },
                label = phase.label,
                detail = when {
                    result != null -> result.detail
                    isCurrent -> state.stepDetail
                    else -> phase.pendingDetail
                },
            )

            if (isCurrent && state.localScanProgress != null) {
                LocalScanProgressSection(state.localScanProgress)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Connected – success
// ──────────────────────────────────────────────────────────────

@Composable
private fun ConnectedContent(
    state: DiscoveryState.Connected,
    onOpenBoard: () -> Unit,
    onRetry: () -> Unit,
) {
    // ── Derive Arabic label + icon for the source ───────────
    val (sourceLabel, sourceIcon, sourceColor) = when (state.source) {
        ConnectionSource.LOCAL  -> Triple("الشبكة المحلية", "🏠", Color(0xFF1B5E20))
        ConnectionSource.CACHED -> Triple("العنوان المحفوظ", "💾", Color(0xFF0D47A1))
        ConnectionSource.CLOUD  -> Triple("النفق السحابي",  "☁️", Color(0xFF4527A0))
    }

    // Strip scheme for a cleaner display ("http://192.168.1.50:8000" → "192.168.1.50:8000")
    val displayUrl = state.boardUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')

    StatusCard(containerColor = Color(0xFFE8F5E9)) {
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(OrabiSuccessGreen),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "تم الاتصال بنجاح!",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = OrabiSuccessGreen,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "لوحة العدّ جاهزة للاستخدام",
            style = MaterialTheme.typography.bodySmall,
            color = OrabiSuccessGreen.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = OrabiSuccessGreen.copy(alpha = 0.15f))
        Spacer(Modifier.height(14.dp))

        // ── Connection info chip ────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(sourceColor.copy(alpha = 0.08f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sourceIcon, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "طريقة الاتصال",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrabiDarkBlue.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    sourceLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = sourceColor,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Address display ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(OrabiDarkBlue.copy(alpha = 0.05f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔗", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "عنوان لوحة العدّ",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrabiDarkBlue.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(2.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        displayUrl,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = OrabiDarkBlue.copy(alpha = 0.85f),
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    Spacer(Modifier.height(20.dp))

    Button(
        onClick = onOpenBoard,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(6.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OrabiBlue),
    ) {
        Text(
            "فتح لوحة العدّ",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }

    Spacer(Modifier.height(12.dp))

    OutlinedButton(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(14.dp),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = OrabiDarkBlue,
        ),
    ) {
        Text(
            "↻",
            fontSize = 16.sp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "إعادة البحث",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        )
    }
}

// ──────────────────────────────────────────────────────────────
// Failure – results + guidance + retry
// ──────────────────────────────────────────────────────────────

@Composable
private fun FailureContent(
    state: DiscoveryState.Failed,
    onRetry: () -> Unit,
) {
    StatusCard(
        containerColor = Color(0xFFFFF8E1),
        borderColor = OrabiWarnOrange.copy(alpha = 0.25f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(OrabiWarnOrange.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", fontSize = 22.sp, color = OrabiWarnOrange, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "تعذّر الاتصال بلوحة العدّ",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = OrabiWarnOrange,
                )
                Text(
                    "فشلت جميع محاولات الاتصال",
                    style = MaterialTheme.typography.bodySmall,
                    color = OrabiWarnOrange.copy(alpha = 0.65f),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = OrabiWarnOrange.copy(alpha = 0.12f))
        Spacer(Modifier.height(14.dp))

        Text(
            "نتائج محاولات الاتصال:",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = OrabiDarkBlue,
        )
        Spacer(Modifier.height(10.dp))

        state.completedSteps.forEach { step ->
            FailedStepRow(step)
            Spacer(Modifier.height(6.dp))
        }
    }

    Spacer(Modifier.height(12.dp))

    StatusCard(containerColor = Color.White) {
        Text(
            "ماذا يمكنك أن تفعل؟",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = OrabiDarkBlue,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        GuidanceItem("١", "تأكد أن جهازك متصل بنفس شبكة الـ Wi-Fi أو الشبكة المحلية التي تتصل بها لوحة العدّ.")
        Spacer(Modifier.height(10.dp))
        GuidanceItem("٢", "تأكد أن لوحة العدّ تعمل وموصولة بالكهرباء والشبكة.")
        Spacer(Modifier.height(10.dp))
        GuidanceItem("٣", "إذا استمرت المشكلة، تواصل مع المشرف أو الدعم الفني.")
    }

    Spacer(Modifier.height(20.dp))

    Button(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(6.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OrabiBlue),
    ) {
        Text("↻", fontSize = 18.sp, color = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(
            "إعادة المحاولة",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }
}

// ──────────────────────────────────────────────────────────────
// Shared card wrapper
// ──────────────────────────────────────────────────────────────

@Composable
private fun StatusCard(
    containerColor: Color,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderMod = if (borderColor != null)
        Modifier.border(1.dp, borderColor, RoundedCornerShape(16.dp))
    else Modifier

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderMod),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

// ──────────────────────────────────────────────────────────────
// Vertical stepper row
// ──────────────────────────────────────────────────────────────

private enum class StepStatus { Pending, Active, Success, Failed }

@Composable
private fun StepperRow(
    isLast: Boolean,
    status: StepStatus,
    label: String,
    detail: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp),
        ) {
            StepCircle(status)
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                        .background(
                            when (status) {
                                StepStatus.Success -> OrabiSuccessGreen.copy(alpha = 0.35f)
                                StepStatus.Failed -> OrabiErrorRed.copy(alpha = 0.2f)
                                else -> OrabiDarkBlue.copy(alpha = 0.1f)
                            }
                        ),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (status == StepStatus.Active) FontWeight.Bold
                    else FontWeight.SemiBold,
                ),
                color = when (status) {
                    StepStatus.Success -> OrabiSuccessGreen
                    StepStatus.Failed -> OrabiErrorRed
                    StepStatus.Active -> OrabiDarkBlue
                    StepStatus.Pending -> OrabiDarkBlue.copy(alpha = 0.4f)
                },
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (status) {
                        StepStatus.Active -> OrabiDarkBlue.copy(alpha = 0.6f)
                        StepStatus.Pending -> OrabiDarkBlue.copy(alpha = 0.3f)
                        else -> OrabiDarkBlue.copy(alpha = 0.45f)
                    },
                )
            }
            if (!isLast) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StepCircle(status: StepStatus) {
    val size = 28.dp
    when (status) {
        StepStatus.Success -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(OrabiSuccessGreen),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        StepStatus.Failed -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(OrabiErrorRed.copy(alpha = 0.12f))
                    .border(1.5.dp, OrabiErrorRed.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", fontSize = 13.sp, color = OrabiErrorRed, fontWeight = FontWeight.Bold)
            }
        }

        StepStatus.Active -> {
            val infiniteTransition = rememberInfiniteTransition(label = "active-pulse")
            val ringAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "ring",
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(OrabiBlue.copy(alpha = 0.12f))
                    .border(2.dp, OrabiBlue.copy(alpha = ringAlpha), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(OrabiBlue),
                )
            }
        }

        StepStatus.Pending -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(OrabiDarkBlue.copy(alpha = 0.06f))
                    .border(1.dp, OrabiDarkBlue.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(OrabiDarkBlue.copy(alpha = 0.18f)),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Local scan progress (inside stepper)
// ──────────────────────────────────────────────────────────────

private const val TOTAL_SCAN_ADDRESSES = 254

@Composable
private fun LocalScanProgressSection(scanned: Int) {
    val progress = scanned / TOTAL_SCAN_ADDRESSES.toFloat()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "تقدّم البحث",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = OrabiDarkBlue.copy(alpha = 0.6f),
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = OrabiBlue,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = OrabiBlue,
            trackColor = OrabiLightBlue.copy(alpha = 0.5f),
        )
    }
}

// ──────────────────────────────────────────────────────────────
// Failed step row (in failure card)
// ──────────────────────────────────────────────────────────────

@Composable
private fun FailedStepRow(step: StepResult) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (step.success) OrabiSuccessGreen.copy(alpha = 0.06f)
                else OrabiErrorRed.copy(alpha = 0.04f)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (step.success) OrabiSuccessGreen else OrabiErrorRed.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (step.success) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (step.success) Color.White else OrabiErrorRed,
                modifier = Modifier.size(13.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                step.label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = OrabiDarkBlue,
            )
            if (step.detail.isNotBlank()) {
                Text(
                    step.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = OrabiDarkBlue.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Guidance item (in failure card)
// ──────────────────────────────────────────────────────────────

@Composable
private fun GuidanceItem(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(OrabiBlue.copy(alpha = 0.18f), OrabiBlue.copy(alpha = 0.08f))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = OrabiBlue,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = OrabiDarkBlue.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
        )
    }
}
