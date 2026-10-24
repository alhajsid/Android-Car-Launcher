package com.example.ui.components

import android.graphics.PathMeasure
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SpeedUnit
import com.example.model.TelemetryData
import com.example.ui.theme.BmwAmber
import com.example.ui.theme.BmwCrimson
import com.example.ui.theme.BmwCyan
import com.example.ui.theme.BmwOptimalGreen
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondaryGrey
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BmwCenterHeroGauge(
    telemetry: TelemetryData,
    speedUnit: SpeedUnit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val isConnected = telemetry.isObdConnected
    val maxRpm = telemetry.maxRpm.coerceAtLeast(8000)
    val redlineRpm = telemetry.redlineRpm.coerceIn(4500, maxRpm)
    val currentRpm = if (isConnected) telemetry.rpm.coerceIn(0, maxRpm) else 0
    val rpmFraction = (currentRpm.toFloat() / maxRpm.toFloat()).coerceIn(0f, 1f)
    val redlineFraction = (redlineRpm.toFloat() / maxRpm.toFloat()).coerceIn(0.6f, 1f)

    // Single smooth continuous linear interpolation for all RPM indicators
    val animRpm by animateFloatAsState(
        targetValue = if (isConnected) rpmFraction else 0f,
        animationSpec = tween(durationMillis = 110, easing = LinearOutSlowInEasing),
        label = "rpmLinearAnim"
    )

    val numberFormatter = remember { NumberFormat.getNumberInstance(Locale.US) }
    val formattedRpm = remember(currentRpm) { numberFormatter.format(currentRpm) }

    val displayedSpeed = if (speedUnit == SpeedUnit.MPH) {
        (telemetry.speed * SpeedUnit.MPH.factorFromKmh).toInt()
    } else {
        telemetry.speed
    }

    Column(
        modifier = modifier.testTag("center_hero_gauge"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 1. Shift Lights LED Strip (Linear progressive activation up to redline)
        ShiftLightBar(
            rpm = currentRpm,
            redline = redlineRpm,
            isConnected = isConnected,
            shiftLabel = telemetry.driveMode.shiftLabel
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Polygonal Blades Gauge + Focal Speedometer Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentAlignment = Alignment.Center
        ) {
            // Zero-allocation continuous linear PathMeasure blade gauge
            BladeGaugeCanvas(
                animRpm = animRpm,
                redlineFraction = redlineFraction,
                accentColor = accentColor,
                isConnected = isConnected,
                modifier = Modifier.fillMaxSize()
            )

            // Center Content: RPM + Linear Bar, Mode Badge, Speed, Gear, Throttle Bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // RPM Digital Readout
                if (isConnected) {
                    val isNearRedline = currentRpm >= redlineRpm
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = formattedRpm,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Black,
                            fontSize = 34.sp,
                            color = if (isNearRedline) BmwCrimson else accentColor,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "RPM",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextSecondaryGrey,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                } else {
                    Text(
                        text = "--- RPM",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Continuous Linear RPM Bar (aligned with center stack, 0 to 8000 RPM)
                LinearRpmRibbon(
                    animRpm = animRpm,
                    redlineFraction = redlineFraction,
                    accentColor = accentColor,
                    isConnected = isConnected
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Mode Micro-Badge (Automotive high-contrast)
                Box(
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isConnected) telemetry.driveMode.badge else "GPS STANDALONE",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        color = if (accentColor == BmwCyan) TextDark else TextPrimaryWhite
                    )
                }

                // Overspeed Limit Alert Warning Badge
                if (telemetry.overspeedAlert) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BmwCrimson)
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "SPEED LIMIT EXCEEDED",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Ultra-Large Digital Speed Display
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$displayedSpeed",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 126.sp,
                        lineHeight = 126.sp,
                        letterSpacing = (-4).sp,
                        color = if (telemetry.overspeedAlert) BmwCrimson else TextPrimaryWhite
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = speedUnit.label,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 26.sp,
                        color = if (telemetry.overspeedAlert) BmwCrimson else accentColor,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Gear Selector Pill [ P R N D S / 1-6 ]
                GearSelectorPill(
                    activeGear = if (isConnected) telemetry.currentGear else "-",
                    isEstimated = telemetry.isGearEstimated && isConnected,
                    accentColor = accentColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Throttle / Load Vector Bar
                ThrottleVectorBar(
                    throttlePercent = if (isConnected) telemetry.throttlePercent else 0,
                    accentColor = accentColor,
                    isConnected = isConnected
                )
            }
        }
    }
}

/**
 * High-precision continuous linear RPM ribbon directly below the RPM readout.
 * Renders via drawWithCache for zero object allocations during RPM sweeps.
 */
@Composable
private fun LinearRpmRibbon(
    animRpm: Float,
    redlineFraction: Float,
    accentColor: Color,
    isConnected: Boolean
) {
    Box(
        modifier = Modifier
            .width(230.dp)
            .height(10.dp)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val barHeight = 5.dp.toPx()
                val topY = (h - barHeight) / 2f
                val corner = CornerRadius(barHeight / 2f, barHeight / 2f)
                val activeBrush = Brush.horizontalGradient(
                    0.0f to accentColor.copy(alpha = 0.65f),
                    redlineFraction to accentColor,
                    1.0f to BmwCrimson
                )
                onDrawBehind {
                    // Background track
                    drawRoundRect(
                        color = Color(0x28FFFFFF),
                        topLeft = Offset(0f, topY),
                        size = Size(w, barHeight),
                        cornerRadius = corner
                    )

                    // Redline zone subtle marker
                    val redlineX = w * redlineFraction
                    drawRoundRect(
                        color = BmwCrimson.copy(alpha = 0.35f),
                        topLeft = Offset(redlineX, topY),
                        size = Size(w - redlineX, barHeight),
                        cornerRadius = corner
                    )

                    // Tick notches at 25%, 50%, 75%, Redline
                    val ticks = floatArrayOf(0.25f, 0.50f, 0.75f, redlineFraction)
                    for (t in ticks) {
                        val tx = w * t
                        drawLine(
                            color = if (t == redlineFraction) BmwCrimson else Color(0x55FFFFFF),
                            start = Offset(tx, 0f),
                            end = Offset(tx, h),
                            strokeWidth = 1.5f
                        )
                    }

                    // Continuous linear fill
                    if (isConnected && animRpm > 0.002f) {
                        val fillW = (w * animRpm).coerceIn(barHeight, w)
                        drawRoundRect(
                            brush = activeBrush,
                            topLeft = Offset(0f, topY),
                            size = Size(fillW, barHeight),
                            cornerRadius = corner
                        )
                        // Leading tip highlight
                        drawCircle(
                            color = if (animRpm >= redlineFraction) BmwCrimson else TextPrimaryWhite,
                            radius = barHeight * 0.75f,
                            center = Offset(fillW.coerceIn(barHeight / 2f, w - barHeight / 2f), h / 2f)
                        )
                    }
                }
            }
    )
}

@Composable
private fun ShiftLightBar(
    rpm: Int,
    redline: Int,
    isConnected: Boolean,
    shiftLabel: String
) {
    val ratio = if (isConnected && redline > 0) (rpm.toFloat() / redline).coerceIn(0f, 1.1f) else 0f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE605070A))
            .border(1.5.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        val thresholds = remember {
            floatArrayOf(0.20f, 0.36f, 0.52f, 0.66f, 0.78f, 0.88f, 0.95f, 1.0f)
        }

        for (i in thresholds.indices) {
            val threshold = thresholds[i]
            val isActive = isConnected && ratio >= threshold
            val ledColor = when {
                i < 3 -> BmwOptimalGreen
                i < 6 -> BmwAmber
                else -> BmwCrimson
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.5.dp)
                    .size(width = 16.dp, height = 10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isActive) ledColor else Color(0x22FFFFFF))
            )
        }

        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = shiftLabel,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = TextSecondaryGrey
        )
    }
}

/**
 * Dual-wing BMW Neue Klasse polygonal tachometer blades.
 * Uses native Android PathMeasure inside drawWithCache so both left and right wings sweep
 * continuously and linearly from 0% (bottom) to 100% (top redline) with zero per-frame allocations.
 */
@Composable
private fun BladeGaugeCanvas(
    animRpm: Float,
    redlineFraction: Float,
    accentColor: Color,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height

            // Left Wing Track: Bottom (0 RPM) -> Outer Lower -> Outer Upper -> Top Shoulder -> Top Tip (Max RPM)
            val leftTrackPath = Path().apply {
                moveTo(w * 0.24f, h * 0.88f)
                lineTo(w * 0.08f, h * 0.64f)
                lineTo(w * 0.08f, h * 0.34f)
                lineTo(w * 0.22f, h * 0.10f)
                lineTo(w * 0.37f, h * 0.10f)
            }

            // Right Wing Track: Symmetric Bottom (0 RPM) -> Outer Lower -> Outer Upper -> Top Shoulder -> Top Tip (Max RPM)
            val rightTrackPath = Path().apply {
                moveTo(w * 0.76f, h * 0.88f)
                lineTo(w * 0.92f, h * 0.64f)
                lineTo(w * 0.92f, h * 0.34f)
                lineTo(w * 0.78f, h * 0.10f)
                lineTo(w * 0.63f, h * 0.10f)
            }

            // Native PathMeasure for exact continuous linear slicing (0.0 .. 1.0)
            val leftMeasure = PathMeasure(leftTrackPath.asAndroidPath(), false)
            val rightMeasure = PathMeasure(rightTrackPath.asAndroidPath(), false)
            val leftTotalLen = leftMeasure.length
            val rightTotalLen = rightMeasure.length

            // Pre-extract Redline sector overlays (top portion from redlineFraction to 1.0f)
            val leftRedlinePath = Path()
            val rightRedlinePath = Path()
            leftMeasure.getSegment(leftTotalLen * redlineFraction, leftTotalLen, leftRedlinePath.asAndroidPath(), true)
            rightMeasure.getSegment(rightTotalLen * redlineFraction, rightTotalLen, rightRedlinePath.asAndroidPath(), true)

            // Reusable active paths (rewound each frame — zero heap allocations during animation)
            val leftActivePath = Path()
            val rightActivePath = Path()
            val tipPos = FloatArray(2)
            val tipTan = FloatArray(2)

            // Pre-compute 9 linear tick positions (0%, 12.5%, 25% ... 100%) along both blades
            val tickFractions = floatArrayOf(0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, redlineFraction, 1f)
            val leftTickPoints = Array(tickFractions.size) { idx ->
                val pos = FloatArray(2)
                leftMeasure.getPosTan(leftTotalLen * tickFractions[idx], pos, null)
                Offset(pos[0], pos[1])
            }
            val rightTickPoints = Array(tickFractions.size) { idx ->
                val pos = FloatArray(2)
                rightMeasure.getPosTan(rightTotalLen * tickFractions[idx], pos, null)
                Offset(pos[0], pos[1])
            }

            val trackStroke = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val activeStroke = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val glowStroke = Stroke(width = 24f, cap = StrokeCap.Round, join = StrokeJoin.Round)

            val activeGradient = Brush.verticalGradient(
                0.0f to BmwCrimson,
                (1f - redlineFraction).coerceIn(0.1f, 0.4f) to accentColor,
                1.0f to accentColor.copy(alpha = 0.55f),
                startY = h * 0.10f,
                endY = h * 0.88f
            )

            onDrawBehind {
                // 1. Draw full background blade tracks
                drawPath(path = leftTrackPath, color = Color(0x22FFFFFF), style = trackStroke)
                drawPath(path = rightTrackPath, color = Color(0x22FFFFFF), style = trackStroke)

                // 2. Draw redline danger zone markers at top of both blades
                drawPath(path = leftRedlinePath, color = BmwCrimson.copy(alpha = 0.45f), style = trackStroke)
                drawPath(path = rightRedlinePath, color = BmwCrimson.copy(alpha = 0.45f), style = trackStroke)

                // 3. Draw linear scale reference nodes along blades
                for (i in leftTickPoints.indices) {
                    val isRed = tickFractions[i] >= redlineFraction
                    val nodeColor = if (isRed) BmwCrimson.copy(alpha = 0.7f) else Color(0x44FFFFFF)
                    drawCircle(color = nodeColor, radius = 3.5f, center = leftTickPoints[i])
                    drawCircle(color = nodeColor, radius = 3.5f, center = rightTickPoints[i])
                }

                // 4. Draw continuous linear active RPM sweep (0.0f .. 1.0f)
                if (isConnected && animRpm > 0.002f) {
                    val clamped = animRpm.coerceIn(0f, 1f)
                    val leftStop = leftTotalLen * clamped
                    val rightStop = rightTotalLen * clamped

                    leftActivePath.rewind()
                    rightActivePath.rewind()

                    leftMeasure.getSegment(0f, leftStop, leftActivePath.asAndroidPath(), true)
                    rightMeasure.getSegment(0f, rightStop, rightActivePath.asAndroidPath(), true)

                    val isRedlineActive = clamped >= redlineFraction
                    val glowColor = if (isRedlineActive) BmwCrimson.copy(alpha = 0.28f) else accentColor.copy(alpha = 0.24f)

                    // Subtle outer neon bloom
                    drawPath(path = leftActivePath, color = glowColor, style = glowStroke)
                    drawPath(path = rightActivePath, color = glowColor, style = glowStroke)

                    // Core high-contrast linear blade sweep
                    drawPath(path = leftActivePath, brush = activeGradient, style = activeStroke)
                    drawPath(path = rightActivePath, brush = activeGradient, style = activeStroke)

                    // Leading-edge precision indicator tips
                    val tipColor = if (isRedlineActive) BmwCrimson else TextPrimaryWhite
                    if (leftMeasure.getPosTan(leftStop, tipPos, tipTan)) {
                        drawCircle(color = tipColor, radius = 7.5f, center = Offset(tipPos[0], tipPos[1]))
                    }
                    if (rightMeasure.getPosTan(rightStop, tipPos, tipTan)) {
                        drawCircle(color = tipColor, radius = 7.5f, center = Offset(tipPos[0], tipPos[1]))
                    }
                }
            }
        }
    )
}

@Composable
private fun GearSelectorPill(
    activeGear: String,
    isEstimated: Boolean = false,
    accentColor: Color
) {
    val gears = remember { listOf("P", "R", "N", "D", "S") }
    val isNumberedGear = activeGear in listOf("1", "2", "3", "4", "5", "6")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xD905070A))
            .border(1.5.dp, Color(0x33FFFFFF), RoundedCornerShape(30.dp))
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        gears.forEach { gear ->
            val isSelected = (gear == activeGear)
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = gear,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 21.sp,
                        color = if (accentColor == BmwCyan) TextDark else TextPrimaryWhite
                    )
                }
            } else {
                Text(
                    text = gear,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextMuted.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }

        if (isNumberedGear) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "G$activeGear",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 21.sp,
                    color = if (accentColor == BmwCyan) TextDark else TextPrimaryWhite
                )
            }
        }

        if (isEstimated && activeGear != "-") {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x40FFFFFF))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "EST",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = TextPrimaryWhite
                )
            }
        }
    }
}

@Composable
private fun ThrottleVectorBar(
    throttlePercent: Int,
    accentColor: Color,
    isConnected: Boolean
) {
    val animPercent by animateFloatAsState(
        targetValue = if (isConnected) (throttlePercent / 100f).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 110, easing = LinearOutSlowInEasing),
        label = "throttleAnim"
    )

    Column(
        modifier = Modifier.width(260.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(4.5.dp))
                .background(Color(0x26FFFFFF))
        ) {
            if (isConnected && animPercent > 0.005f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animPercent)
                        .height(9.dp)
                        .clip(RoundedCornerShape(4.5.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFF9900), accentColor)
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "IDLE",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = TextSecondaryGrey.copy(alpha = 0.8f)
            )
            Text(
                text = if (isConnected) "THROTTLE $throttlePercent%" else "THROTTLE --%",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isConnected) TextPrimaryWhite else TextMuted
            )
            Text(
                text = "WOT",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = BmwCrimson
            )
        }
    }
}
