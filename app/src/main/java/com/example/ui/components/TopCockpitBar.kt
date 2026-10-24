package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TelemetryData
import com.example.ui.theme.BmwAmber
import com.example.ui.theme.BmwCrimson
import com.example.ui.theme.BmwCyan
import com.example.ui.theme.BmwDeepBlue
import com.example.ui.theme.BmwOptimalGreen
import com.example.ui.theme.BmwPureBlue
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondaryGrey

@Composable
fun TopCockpitBar(
    telemetry: TelemetryData,
    currentTimeString: String,
    onOpenObdScreen: () -> Unit,
    isDefaultLauncher: Boolean = false,
    isSimulating: Boolean = false,
    onMakeDefaultLauncher: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xE605070A))
            .border(1.dp, Color(0x22FFFFFF))
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("top_cockpit_bar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: BMW Neue Klasse Brand Identity & Launcher Status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Iconic 3-stripe Motorsport angled emblem
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(modifier = Modifier.size(width = 6.dp, height = 22.dp).background(BmwPureBlue))
                Box(modifier = Modifier.size(width = 6.dp, height = 22.dp).background(BmwDeepBlue))
                Box(modifier = Modifier.size(width = 6.dp, height = 22.dp).background(BmwCrimson))
            }

            Text(
                text = "NEUE KLASSE",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                letterSpacing = 1.2.sp,
                color = TextPrimaryWhite
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x33FFFFFF))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "M-TELEMETRY v${com.example.BuildConfig.VERSION_NAME}",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = TextSecondaryGrey
                )
            }

            // Launcher Default Status Badge or Action
            if (isDefaultLauncher) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x2610B981))
                        .border(1.dp, Color(0x6610B981), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(BmwOptimalGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "HOME DEFAULT",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = BmwOptimalGreen
                        )
                    }
                }
            } else if (onMakeDefaultLauncher != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x334CC2FF))
                        .border(1.dp, BmwCyan.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                        .clickable { onMakeDefaultLauncher() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("set_default_launcher_top_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = BmwCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SET DEFAULT LAUNCHER",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp,
                            color = BmwCyan
                        )
                    }
                }
            }
        }

        // Center: OBD Connection Chip & GPS Indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Prominent Simulation Active Badge if enabled
            if (isSimulating) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(BmwAmber.copy(alpha = 0.3f))
                        .border(1.5.dp, BmwAmber, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("simulation_mode_badge")
                ) {
                    Text(
                        text = "SIMULATION ON",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        color = BmwAmber
                    )
                }
            }

            // OBD Chip (Direct tap opens OBD scanning / diagnostics - Extra readable)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (telemetry.isObdConnected) Color(0x3310B981) else Color(0x33FF2A38))
                    .border(
                        1.5.dp,
                        if (telemetry.isObdConnected) Color(0x8010B981) else Color(0x80FF2A38),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable { onOpenObdScreen() }
                    .padding(horizontal = 16.dp, vertical = 7.dp)
                    .testTag("obd_status_chip")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (telemetry.isObdConnected) BmwOptimalGreen else BmwCrimson)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (telemetry.isObdConnected) Icons.Default.Sensors else Icons.Default.LinkOff,
                        contentDescription = null,
                        tint = if (telemetry.isObdConnected) TextPrimaryWhite else BmwCrimson,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (telemetry.isObdConnected) telemetry.obdAdapterName.uppercase() else "OBD NOT CONNECTED",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp,
                        color = if (telemetry.isObdConnected) TextPrimaryWhite else Color(0xFFFF8888)
                    )
                }
            }

            // GPS Signal Icon & Fix State
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (telemetry.hasGpsFix) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                    contentDescription = "GPS Status",
                    tint = if (telemetry.hasGpsFix) BmwCyan else TextMuted,
                    modifier = Modifier.size(22.dp)
                )
                if (telemetry.hasGpsFix && telemetry.gpsAccuracyMeters != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "±${telemetry.gpsAccuracyMeters.toInt()}m",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = BmwCyan
                    )
                }
            }

            // Check Engine MIL Indicator if active
            if (telemetry.isMilActive || telemetry.dtcCodes.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(BmwAmber.copy(alpha = 0.25f))
                        .border(1.5.dp, BmwAmber, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "CEL ${telemetry.dtcCodes.size} DTC",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = BmwAmber
                    )
                }
            }
        }

        // Right: Telemetry Vital Context (Alternator, Network, Clock)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Alternator Pin 16 Voltage
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x26FFFFFF))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                val hasVoltage = telemetry.isObdConnected && telemetry.alternatorVoltage != null
                Icon(
                    imageVector = Icons.Default.ElectricBolt,
                    contentDescription = "Voltage",
                    tint = if (hasVoltage) BmwAmber else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (hasVoltage) "${telemetry.alternatorVoltage}V" else "--V",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (hasVoltage) TextPrimaryWhite else TextMuted
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "ALT",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            // Real Network Signal
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = telemetry.networkLabel,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (telemetry.cellularSignalLevel > 0) TextPrimaryWhite else TextMuted,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                val heights = listOf(7.dp, 11.dp, 15.dp, 20.dp)
                heights.forEachIndexed { index, h ->
                    val active = index < telemetry.cellularSignalLevel
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(h)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (active) TextPrimaryWhite else Color(0x33FFFFFF))
                    )
                }
            }

            // Top Clock (Extra Bold and Crisp for car glances)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x22FFFFFF))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = currentTimeString.ifEmpty { "17:07" },
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = TextPrimaryWhite
                )
            }
        }
    }
}
