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
// Brand palette
// ──────────────────────────────────────────────────────────────
private val OrabiDarkBrown = Color(0xFF5A4A2C)
private val OrabiGold = Color(0xFFD4AF37)
private val OrabiCream = Color(0xFFF5EDDC)
private val OrabiSuccessGreen = Color(0xFF388E3C)
private val OrabiErrorRed = Color(0xFFC62828)
private val OrabiWarnOrange = Color(0xFFE65100)
private val OrabiLightGold = Color(0xFFF5E6B8)

// ──────────────────────────────────────────────────────────────
// All three discovery phases – used by the stepper
// ──────────────────────────────────────────────────────────────
private data class PhaseInfo(val label: String, val pendingDetail: String)

private val AllPhases = listOf(
    PhaseInfo(ArabicLabels.STEP_CACHED, "البحث في الاتصالات المحفوظة…"),
    PhaseInfo(ArabicLabels.STEP_CLOUD, "الاتصال بالخادم السحابي…"),
    PhaseInfo(ArabicLabels.STEP_LOCAL, "فحص الشبكة المحلية…"),
)

private fun phaseIndex(label: String): Int = when (label) {
    ArabicLabels.STEP_CACHED -> 0
    ArabicLabels.STEP_CLOUD -> 1
    ArabicLabels.STEP_LOCAL -> 2
    else -> -1
}

// ──────────────────────────────────────────────────────────────
// Main discovery screen – RTL Arabic UI
// ──────────────────────────────────────────────────────────────

@Composable
fun DiscoveryScreen(
    state: DiscoveryState,
    onRetry: () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = OrabiCream,
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
                    contentDescription = "شعار عربي",
                    modifier = Modifier.size(120.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "عدّاد الخبز عربي",
                    style = MaterialTheme.typography.headlineSmall
                        .copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp),
                    color = OrabiDarkBrown,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "الاتصال بلوحة العدّ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OrabiDarkBrown.copy(alpha = 0.55f),
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
                            is DiscoveryState.Connected -> ConnectedContent()
                            is DiscoveryState.Failed -> FailureContent(target, onRetry)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Spacer(Modifier.height(24.dp))
                Text(
                    text = "عربي لعدّ الخبز الآلي © ٢٠٢٦",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrabiDarkBrown.copy(alpha = 0.3f),
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
            color = OrabiGold,
            strokeWidth = 3.dp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "جارٍ التحضير للاتصال…",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = OrabiDarkBrown,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "يتم تجهيز النظام للبحث عن لوحة العدّ",
            style = MaterialTheme.typography.bodySmall,
            color = OrabiDarkBrown.copy(alpha = 0.5f),
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
            color = OrabiDarkBrown,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "يرجى الانتظار بينما نحاول الاتصال بالجهاز",
            style = MaterialTheme.typography.bodySmall,
            color = OrabiDarkBrown.copy(alpha = 0.45f),
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
private fun ConnectedContent() {
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
            "جارٍ تحميل واجهة لوحة العدّ…",
            style = MaterialTheme.typography.bodySmall,
            color = OrabiSuccessGreen.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = OrabiSuccessGreen,
            trackColor = OrabiSuccessGreen.copy(alpha = 0.15f),
        )
        Spacer(Modifier.height(8.dp))
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
            color = OrabiDarkBrown,
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
            color = OrabiDarkBrown,
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
        colors = ButtonDefaults.buttonColors(containerColor = OrabiGold),
    ) {
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
                                else -> OrabiDarkBrown.copy(alpha = 0.1f)
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
                    StepStatus.Active -> OrabiDarkBrown
                    StepStatus.Pending -> OrabiDarkBrown.copy(alpha = 0.4f)
                },
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (status) {
                        StepStatus.Active -> OrabiDarkBrown.copy(alpha = 0.6f)
                        StepStatus.Pending -> OrabiDarkBrown.copy(alpha = 0.3f)
                        else -> OrabiDarkBrown.copy(alpha = 0.45f)
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
                    .background(OrabiGold.copy(alpha = 0.12f))
                    .border(2.dp, OrabiGold.copy(alpha = ringAlpha), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(OrabiGold),
                )
            }
        }

        StepStatus.Pending -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(OrabiDarkBrown.copy(alpha = 0.06f))
                    .border(1.dp, OrabiDarkBrown.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(OrabiDarkBrown.copy(alpha = 0.18f)),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Local scan progress (inside stepper)
// ──────────────────────────────────────────────────────────────

@Composable
private fun LocalScanProgressSection(scanned: Int) {
    val progress = scanned / 254f
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
                color = OrabiDarkBrown.copy(alpha = 0.6f),
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = OrabiGold,
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
            color = OrabiGold,
            trackColor = OrabiLightGold.copy(alpha = 0.5f),
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
            Text(
                if (step.success) "✓" else "✕",
                fontSize = 11.sp,
                color = if (step.success) Color.White else OrabiErrorRed,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
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
                        listOf(OrabiGold.copy(alpha = 0.18f), OrabiGold.copy(alpha = 0.08f))
                    )
                ),
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
            color = OrabiDarkBrown.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
        )
    }
}
