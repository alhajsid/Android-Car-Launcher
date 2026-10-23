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
            .clip(RoundedCornerShape(26.dp))
            .background(GlassCardFill)
            .border(
                width = if (isOverheating) 2.5.dp else 1.5.dp,
                color = cardBorderColor,
                shape = RoundedCornerShape(26.dp)
            )
            .padding(14.dp)
            .testTag("obd_live_metrics_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Card Header (Prominent)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = TextPrimaryWhite,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "OBD2 LIVE METRICS",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp,
                    color = TextPrimaryWhite
                )
            }
            Text(
                text = if (isConnected) telemetry.obdProtocol else "DISCONNECTED",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isConnected) TextSecondaryGrey else BmwCrimson
            )
        }

        // Critical Overheat Warning Banner if active
        if (isOverheating) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BmwCrimson.copy(alpha = 0.3f))
                    .border(1.5.dp, BmwCrimson, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = BmwCrimson,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CRITICAL: ENGINE COOLANT OVERHEAT",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = BmwCrimson
                    )
                }
            }
        }

        // Grid Row 1: Coolant ECT & Calculated Engine Load (Larger, readable while driving)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                status = if (isConnected) "LOAD" else "--",
                statusColor = TextSecondaryGrey,
                barProgress = if (isConnected) (telemetry.engLoadPercent / 100f).coerceIn(0f, 1f) else 0f,
                barColor = Color(0xFFFF7700)
            )
        }

        // Grid Row 2: Intake Air Temp (IAT) & Throttle Position (TPS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x9905070A))
                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
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
                        tint = BmwCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("BOOST", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryGrey)
                        Text(
                            text = if (isConnected) "${telemetry.turboBoostBar} bar" else "-- bar",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isConnected) TextPrimaryWhite else TextMuted
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(Color(0x22FFFFFF))
                )

                // MAF Air Flow
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Waves,
                        contentDescription = null,
                        tint = Color(0xFFFF9900),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("MAF FLOW", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryGrey)
                        Text(
                            text = if (isConnected) "${telemetry.mafFlowGs.toInt()} g/s" else "-- g/s",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isConnected) TextPrimaryWhite else TextMuted
                        )
                    }
                }
            }
        }

        // Row 4: Drive Mode Selector Pills (Roomy touch targets for bumpy road driving)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xE605070A))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DriveMode.values().forEach { mode ->
                val isSelected = (telemetry.driveMode == mode)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) mode.primaryColor else Color.Transparent)
                        .clickable { onSelectDriveMode(mode) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.title,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        fontSize = 14.sp,
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33FFB000))
                    .border(1.dp, Color(0x66FFB000), RoundedCornerShape(12.dp))
                    .clickable { onOpenObdScreen() }
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap to Connect ELM327 OBD-II Adapter",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
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
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x9905070A))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .padding(10.dp)
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
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = value,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                color = TextPrimaryWhite
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        // Progress Vector
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(3.5.dp))
                .background(Color(0x22FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barProgress)
                    .height(7.dp)
                    .clip(RoundedCornerShape(3.5.dp))
                    .background(barColor)
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
                color = TextSecondaryGrey
            )
            Text(
                text = status,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = statusColor
            )
        }
    }
}
