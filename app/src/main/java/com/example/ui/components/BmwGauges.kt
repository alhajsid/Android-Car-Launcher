package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import com.example.ui.theme.OledBlack
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
    val currentRpm = if (isConnected) telemetry.rpm else 0
    val rpmFraction = (currentRpm.toFloat() / telemetry.maxRpm.coerceAtLeast(8000)).coerceIn(0f, 1f)

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
        // 1. Shift Lights LED Strip
        ShiftLightBar(
            rpm = currentRpm,
            redline = telemetry.redlineRpm,
            isConnected = isConnected,
            shiftLabel = telemetry.driveMode.shiftLabel
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Polygonal Blades Gauge + Focal Speedometer Container
        Box(
            modifier = Modifier
                .size(width = 380.dp, height = 310.dp),
            contentAlignment = Alignment.Center
        ) {
            // Automotive Dual Polygonal Blade Geometry Canvas
            BladeGaugeCanvas(
                rpmFraction = rpmFraction,
                accentColor = accentColor,
                isConnected = isConnected
            )

            // Center Content: RPM, Speed, Gear, Throttle Bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // RPM Digital Readout
                if (isConnected) {
                    val formattedRpm = NumberFormat.getNumberInstance(Locale.US).format(currentRpm)
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = formattedRpm,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = accentColor,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "RPM",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = TextSecondaryGrey.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = "--- RPM",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = TextMuted
                    )
                }

                // Mode Micro-Badge
                Box(
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isConnected) telemetry.driveMode.badge else "GPS STANDALONE",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = if (accentColor == BmwCyan) TextDark else TextPrimaryWhite
                    )
                }

                // Ultra-Large Digital Speed Display
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 0.dp)
                ) {
                    Text(
                        text = "$displayedSpeed",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 94.sp,
                        lineHeight = 94.sp,
                        letterSpacing = (-4).sp,
                        color = TextPrimaryWhite
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = speedUnit.label,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = accentColor,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // Gear Selector Pill [ P R N D S ]
                GearSelectorPill(
                    activeGear = if (isConnected) telemetry.currentGear else "-",
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

@Composable
private fun ShiftLightBar(
    rpm: Int,
    redline: Int,
    isConnected: Boolean,
    shiftLabel: String
) {
    val ratio = if (isConnected) (rpm.toFloat() / redline).coerceIn(0f, 1.1f) else 0f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xE605070A))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        val leds = listOf(
            BmwOptimalGreen to 0.40f,
            BmwOptimalGreen to 0.55f,
            BmwAmber to 0.70f,
            BmwAmber to 0.82f,
            BmwCrimson to 0.92f,
            BmwCrimson to 0.98f
        )

        leds.forEach { (color, threshold) ->
            val isActive = isConnected && ratio >= threshold
            val displayColor = if (isActive) color else Color(0x22FFFFFF)
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(width = 10.dp, height = 6.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(displayColor)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = shiftLabel,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp,
            color = TextSecondaryGrey.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun BladeGaugeCanvas(
    rpmFraction: Float,
    accentColor: Color,
    isConnected: Boolean
) {
    val animRpm by animateFloatAsState(
        targetValue = rpmFraction,
        animationSpec = tween(180),
        label = "rpmAnim"
    )

    Canvas(modifier = Modifier.size(width = 380.dp, height = 310.dp)) {
        val w = size.width
        val h = size.height

        // Left blade path: 0 to 4000 RPM
        val leftBladePath = Path().apply {
            moveTo(w * 0.24f, h * 0.84f)
            lineTo(w * 0.12f, h * 0.62f)
            lineTo(w * 0.12f, h * 0.34f)
            lineTo(w * 0.26f, h * 0.14f)
            lineTo(w * 0.38f, h * 0.14f)
        }

        // Right blade path: 4000 to 7000+ Redline
        val rightBladePath = Path().apply {
            moveTo(w * 0.76f, h * 0.84f)
            lineTo(w * 0.88f, h * 0.62f)
            lineTo(w * 0.88f, h * 0.34f)
            lineTo(w * 0.74f, h * 0.14f)
            lineTo(w * 0.62f, h * 0.14f)
        }

        // Draw background inactive tracks
        drawPath(
            path = leftBladePath,
            color = Color(0x1AFFFFFF),
            style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = rightBladePath,
            color = Color(0x1AFFFFFF),
            style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw Redline danger sector on top right
        val redlineSector = Path().apply {
            moveTo(w * 0.88f, h * 0.34f)
            lineTo(w * 0.74f, h * 0.14f)
            lineTo(w * 0.66f, h * 0.14f)
        }
        drawPath(
            path = redlineSector,
            color = BmwCrimson.copy(alpha = 0.8f),
            style = Stroke(width = 8f, cap = StrokeCap.Square, join = StrokeJoin.Round)
        )

        // Draw Active dynamic sweeps if connected
        if (isConnected && animRpm > 0.05f) {
            val leftSweepProgress = (animRpm / 0.5f).coerceIn(0f, 1f)
            // Left active path portion
            val leftActivePath = Path().apply {
                moveTo(w * 0.24f, h * 0.84f)
                if (leftSweepProgress > 0.3f) lineTo(w * 0.12f, h * 0.62f)
                if (leftSweepProgress > 0.6f) lineTo(w * 0.12f, h * 0.34f)
                if (leftSweepProgress >= 0.9f) lineTo(w * 0.26f, h * 0.14f)
            }
            drawPath(
                path = leftActivePath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x334CC2FF), accentColor, TextPrimaryWhite),
                    start = Offset(0f, h),
                    end = Offset(w * 0.4f, 0f)
                ),
                style = Stroke(width = 8f, cap = StrokeCap.Square, join = StrokeJoin.Round)
            )

            // Right active path if RPM exceeds 4000
            if (animRpm > 0.5f) {
                val rightSweepProgress = ((animRpm - 0.5f) / 0.5f).coerceIn(0f, 1f)
                val rightColor = if (animRpm > 0.85f) BmwCrimson else accentColor
                val rightActivePath = Path().apply {
                    moveTo(w * 0.62f, h * 0.14f)
                    if (rightSweepProgress > 0.3f) lineTo(w * 0.74f, h * 0.14f)
                    if (rightSweepProgress > 0.6f) lineTo(w * 0.88f, h * 0.34f)
                    if (rightSweepProgress >= 0.9f) lineTo(w * 0.88f, h * 0.62f)
                }
                drawPath(
                    path = rightActivePath,
                    color = rightColor,
                    style = Stroke(width = 8f, cap = StrokeCap.Square, join = StrokeJoin.Round)
                )
            }
        }
    }
}

@Composable
private fun GearSelectorPill(
    activeGear: String,
    accentColor: Color
) {
    val gears = listOf("P", "R", "N", "D", "S")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xD905070A))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(30.dp))
            .padding(horizontal = 14.dp, vertical = 3.dp)
    ) {
        gears.forEach { gear ->
            val isSelected = (gear == activeGear)
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = gear,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = if (accentColor == BmwCyan) TextDark else TextPrimaryWhite
                    )
                }
            } else {
                Text(
                    text = gear,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = TextMuted.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 5.dp)
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
        targetValue = throttlePercent / 100f,
        animationSpec = tween(150),
        label = "throttleAnim"
    )

    Column(
        modifier = Modifier.width(180.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Track Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x22FFFFFF))
        ) {
            if (isConnected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animPercent)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFF9900), accentColor)
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "IDLE",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                color = TextSecondaryGrey.copy(alpha = 0.6f)
            )
            Text(
                text = if (isConnected) "THROTTLE $throttlePercent%" else "THROTTLE --%",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = if (isConnected) TextPrimaryWhite else TextMuted
            )
            Text(
                text = "WOT",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                color = BmwCrimson.copy(alpha = 0.8f)
            )
        }
    }
}
