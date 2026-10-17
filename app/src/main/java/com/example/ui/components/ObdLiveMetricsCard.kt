package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DriveMode
import com.example.model.TelemetryData
import com.example.model.TempUnit
import com.example.ui.theme.BmwAmber
import com.example.ui.theme.BmwCrimson
import com.example.ui.theme.BmwCyan
import com.example.ui.theme.BmwOptimalGreen
import com.example.ui.theme.GlassCardBorder
import com.example.ui.theme.GlassCardFill
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondaryGrey

@Composable
fun ObdLiveMetricsCard(
    telemetry: TelemetryData,
    tempUnit: TempUnit,
    onSelectDriveMode: (DriveMode) -> Unit,
    onOpenObdScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = telemetry.isObdConnected
    val isOverheating = telemetry.isOverheating || (telemetry.coolantTemp >= telemetry.coolantThreshold)

    val infiniteTransition = rememberInfiniteTransition(label = "overheatPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val cardBorderColor = when {
        isOverheating -> BmwCrimson.copy(alpha = pulseAlpha)
        else -> GlassCardBorder
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(GlassCardFill)
            .border(
                width = if (isOverheating) 2.dp else 1.dp,
                color = cardBorderColor,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(14.dp)
            .testTag("obd_live_metrics_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Card Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = TextSecondaryGrey,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "OBD2 LIVE METRICS",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = TextPrimaryWhite
                )
            }
            Text(
                text = if (isConnected) telemetry.obdProtocol else "DISCONNECTED",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                color = if (isConnected) TextMuted else BmwCrimson
            )
        }

        // Critical Overheat Warning Banner if active
        if (isOverheating) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BmwCrimson.copy(alpha = 0.25f))
                    .border(1.dp, BmwCrimson, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = BmwCrimson,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CRITICAL: ENGINE COOLANT OVERHEAT",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = BmwCrimson
                    )
                }
            }
        }

        // Grid Row 1: Coolant ECT & Calculated Engine Load
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Engine Coolant Temp
            val ectDisplay = if (tempUnit == TempUnit.FAHRENHEIT) {
                "${(telemetry.coolantTemp * 9 / 5) + 32}°F"
            } else {
                "${telemetry.coolantTemp}°C"
            }
            val (ectStatus, ectColor) = when {
                !isConnected -> Pair("--", TextMuted)
                telemetry.coolantTemp < 65 -> Pair("COLD", BmwCyan)
                telemetry.coolantTemp > 105 -> Pair("OVERHEAT", BmwCrimson)
                telemetry.coolantTemp > 98 -> Pair("WARM", BmwAmber)
                else -> Pair("OPTIMAL", BmwOptimalGreen)
            }

            MetricBox(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.DeviceThermostat,
                iconTint = ectColor,
                title = "ECT",
                value = if (isConnected) ectDisplay else "--",
                status = ectStatus,
                statusColor = ectColor,
                barProgress = if (isConnected) (telemetry.coolantTemp / 120f).coerceIn(0f, 1f) else 0f,
                barColor = ectColor
            )

            // Calculated Engine Load
            MetricBox(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Speed,
                iconTint = Color(0xFFFF7700),
                title = "ENG LOAD",
                value = if (isConnected) "${telemetry.engLoadPercent}%" else "--",
                status = if (isConnected) "CALCULATED" else "--",
                statusColor = TextSecondaryGrey,
                barProgress = if (isConnected) (telemetry.engLoadPercent / 100f).coerceIn(0f, 1f) else 0f,
                barColor = Color(0xFFFF7700)
            )
        }

        // Grid Row 2: Intake Air Temp (IAT) & Throttle Position (TPS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val iatDisplay = if (tempUnit == TempUnit.FAHRENHEIT) {
                "${(telemetry.intakeAirTemp * 9 / 5) + 32}°F"
            } else {
                "${telemetry.intakeAirTemp}°C"
            }

            MetricBox(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Air,
                iconTint = BmwCyan,
                title = "IAT (INTAKE)",
                value = if (isConnected) iatDisplay else "--",
                status = if (isConnected) "NORMAL" else "--",
                statusColor = BmwCyan,
                barProgress = if (isConnected) 0.38f else 0f,
                barColor = BmwCyan
            )

            MetricBox(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Tune,
                iconTint = Color(0xFFA855F7),
                title = "TPS (THROTTLE)",
                value = if (isConnected) "${telemetry.throttlePercent}%" else "--",
                status = if (isConnected) "POS" else "--",
                statusColor = Color(0xFFA855F7),
                barProgress = if (isConnected) (telemetry.throttlePercent / 100f).coerceIn(0f, 1f) else 0f,
                barColor = Color(0xFFA855F7)
            )
        }

        // Row 3: Turbo Boost & Mass Air Flow (MAF)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x8005070A))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Boost
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Compress,
                        contentDescription = null,
                        tint = BmwCrimson,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "TURBO BOOST",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                        Text(
                            text = if (isConnected) "+${String.format("%.2f", telemetry.turboBoostBar)} BAR" else "-- BAR",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TextPrimaryWhite
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(Color(0x22FFFFFF))
                )

                // MAF Flow
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "MAF FLOW",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                        Text(
                            text = if (isConnected) "${String.format("%.1f", telemetry.mafFlowGs)} G/S" else "-- G/S",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TextPrimaryWhite
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Waves,
                        contentDescription = null,
                        tint = BmwOptimalGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Drive Mode Selector Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x8005070A))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DriveMode.values().forEach { mode ->
                val isSelected = (mode == telemetry.driveMode)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) mode.primaryColor else Color.Transparent)
                        .clickable { onSelectDriveMode(mode) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.title,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp,
                        color = if (isSelected) {
                            if (mode == DriveMode.COMFORT) Color(0xFF05070A) else TextPrimaryWhite
                        } else {
                            TextSecondaryGrey
                        }
                    )
                }
            }
        }

        // Disconnected Notice / Tap prompt
        if (!isConnected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x33FFB000))
                    .clickable { onOpenObdScreen() }
                    .padding(vertical = 5.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap to Connect ELM327 OBD-II Adapter",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = BmwAmber
                )
            }
        }
    }
}

@Composable
private fun MetricBox(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    status: String,
    statusColor: Color,
    barProgress: Float,
    barColor: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x9905070A))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
            .padding(9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = value,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimaryWhite
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                color = TextMuted
            )
            Text(
                text = status,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = statusColor
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        // Progress line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color(0x22FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barProgress)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor)
            )
        }
    }
}
