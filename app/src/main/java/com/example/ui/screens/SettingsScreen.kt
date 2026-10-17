package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DisplayBrightnessMode
import com.example.model.SpeedUnit
import com.example.model.TelemetryData
import com.example.model.TempUnit
import com.example.model.UserSettings
import com.example.ui.theme.BmwAmber
import com.example.ui.theme.BmwCrimson
import com.example.ui.theme.BmwCyan
import com.example.ui.theme.BmwOptimalGreen
import com.example.ui.theme.CockpitSurfaceLowest
import com.example.ui.theme.GlassCardBorder
import com.example.ui.theme.GlassCardFill
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondaryGrey
import com.example.ui.theme.ThemeAccent
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: UserSettings,
    telemetry: TelemetryData,
    isDefaultLauncher: Boolean,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit,
    onCalibrateGear: (Int) -> Unit,
    onOpenHudMirror: () -> Unit,
    onRequestDefaultLauncher: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = settings.themeAccent.primary
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CockpitSurfaceLowest)
            .testTag("settings_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable { onBack() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "COCKPIT",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextPrimaryWhite
                    )
                }

                Text(
                    text = "COCKPIT SETTINGS",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp,
                    color = TextPrimaryWhite
                )

                // Quick Launch Mirror HUD button
                Button(
                    onClick = onOpenHudMirror,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x334CC2FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Flip,
                        contentDescription = null,
                        tint = BmwCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("HUD MIRROR", fontSize = 11.sp, color = BmwCyan, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Settings Content List
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Units & Driving Metrics
                SettingsSection(title = "UNITS & FORMAT", icon = Icons.Default.Speed) {
                    // Speed Unit Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Speedometer Units", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryWhite)
                            Text("Standard metric (km/h) or imperial (mph)", fontSize = 10.sp, color = TextSecondaryGrey)
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x8005070A))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SpeedUnit.values().forEach { unit ->
                                val isSel = (settings.speedUnit == unit)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) accentColor else Color.Transparent)
                                        .clickable { onUpdateSettings { it.copy(speedUnit = unit) } }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = unit.label.uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (isSel) TextDark else TextSecondaryGrey
                                    )
                                }
                            }
                        }
                    }

                    // Temperature Unit Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Temperature Units", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryWhite)
                            Text("Coolant & ambient Celsius or Fahrenheit", fontSize = 10.sp, color = TextSecondaryGrey)
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x8005070A))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TempUnit.values().forEach { unit ->
                                val isSel = (settings.tempUnit == unit)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) accentColor else Color.Transparent)
                                        .clickable { onUpdateSettings { it.copy(tempUnit = unit) } }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = unit.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (isSel) TextDark else TextSecondaryGrey
                                    )
                                }
                            }
                        }
                    }

                    // 24-Hour Digital Clock Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("24-Hour Digital Clock", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryWhite)
                            Text("Toggle between 24h (18:42) and 12h (6:42 PM)", fontSize = 10.sp, color = TextSecondaryGrey)
                        }
                        Switch(
                            checked = settings.is24HourClock,
                            onCheckedChange = { onUpdateSettings { s -> s.copy(is24HourClock = it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accentColor
                            )
                        )
                    }
                }

                // Section 2: Powertrain & Redline Safety Settings
                SettingsSection(title = "POWERTRAIN SAFETY & ALERTS", icon = Icons.Default.NotificationsActive) {
                    // Redline Slider
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tachometer Redline Limit", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryWhite)
                            Text("${settings.redlineRpm} RPM", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BmwCrimson)
                        }
                        Slider(
                            value = settings.redlineRpm.toFloat(),
                            onValueChange = { onUpdateSettings { s -> s.copy(redlineRpm = it.toInt()) } },
                            valueRange = 5500f..8500f,
                            steps = 11,
                            colors = SliderDefaults.colors(
                                thumbColor = BmwCrimson,
                                activeTrackColor = BmwCrimson,
                                inactiveTrackColor = Color(0x33FFFFFF)
                            )
                        )
                    }

                    // Coolant Alert Threshold Slider
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Coolant Overheat Alert Threshold", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryWhite)
                            Text("${settings.coolantAlertThreshold}°C", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BmwAmber)
                        }
                        Slider(
                            value = settings.coolantAlertThreshold.toFloat(),
                            onValueChange = { onUpdateSettings { s -> s.copy(coolantAlertThreshold = it.toInt()) } },
                            valueRange = 95f..120f,
                            steps = 24,
                            colors = SliderDefaults.colors(
                                thumbColor = BmwAmber,
                                activeTrackColor = BmwAmber,
                                inactiveTrackColor = Color(0x33FFFFFF)
                            )
                        )
                    }

                    // Speed Limit Alert Threshold
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Overspeed Alert Threshold", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryWhite)
                            Text(
                                text = if (settings.speedLimitAlertKmh > 0) "${settings.speedLimitAlertKmh} km/h" else "DISABLED",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (settings.speedLimitAlertKmh > 0) BmwCrimson else TextSecondaryGrey
                            )
                        }
                        Slider(
                            value = settings.speedLimitAlertKmh.toFloat(),
                            onValueChange = { onUpdateSettings { s -> s.copy(speedLimitAlertKmh = it.toInt()) } },
                            valueRange = 0f..200f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = if (settings.speedLimitAlertKmh > 0) BmwCrimson else TextSecondaryGrey,
                                activeTrackColor = if (settings.speedLimitAlertKmh > 0) BmwCrimson else Color(0x33FFFFFF),
                                inactiveTrackColor = Color(0x33FFFFFF)
                            )
                        )
                    }

                    // Coolant Warning Alert & Test Trigger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Test Emergency Overheat Glow", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryWhite)
                            Text("Simulates vehicle coolant warning beacon", fontSize = 10.sp, color = TextSecondaryGrey)
                        }
                        Switch(
                            checked = settings.testOverheatAlert,
                            onCheckedChange = { onUpdateSettings { s -> s.copy(testOverheatAlert = it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BmwCrimson
                            )
                        )
                    }
                }

                // Section 3: Gear Ratio Calibration Screen (Live Learn & Tuning)
                SettingsSection(title = "GEAR RATIO ESTIMATION & CALIBRATION", icon = Icons.Default.Build) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "OBD-II has no standard gear sensor. Vision HUD estimates gear from RPM ÷ Speed. Calibrate your car's transmission ratios below for precise gear recognition.",
                            fontSize = 10.sp,
                            color = TextSecondaryGrey,
                            lineHeight = 14.sp
                        )

                        // Live current driving ratio indicator
                        val currentSpeed = telemetry.speed
                        val currentRpm = telemetry.rpm
                        val liveRatio = if (currentSpeed > 5 && currentRpm > 600) {
                            String.format(Locale.US, "%.1f", currentRpm.toFloat() / currentSpeed)
                        } else {
                            "--"
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33FFFFFF))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("LIVE TELEMETRY RATIO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondaryGrey)
                                    Text("Current RPM / Speed: $liveRatio", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentColor)
                                }
                                Text("Gear: ${telemetry.currentGear} (EST)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimaryWhite)
                            }
                        }

                        // Table of Gears 1 through 6
                        (1..6).forEach { gear ->
                            val ratio = settings.gearRatios[gear] ?: 0f
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x2205070A))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(accentColor.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("G$gear", fontWeight = FontWeight.Black, fontSize = 11.sp, color = accentColor)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Ratio: ${String.format(Locale.US, "%.1f", ratio)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = TextPrimaryWhite
                                    )
                                }

                                // Button to learn current live ratio for this gear
                                Button(
                                    onClick = { onCalibrateGear(gear) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x334CC2FF)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("LEARN LIVE RATIO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BmwCyan)
                                }
                            }
                        }
                    }
                }

                // Section 4: Display Brightness & HUD Mirroring
                SettingsSection(title = "DISPLAY BRIGHTNESS & HUD PROJECTION", icon = Icons.Default.BrightnessMedium) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Screen Brightness Control", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryWhite)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x8005070A))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DisplayBrightnessMode.values().forEach { mode ->
                                val isSel = (settings.brightnessMode == mode)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) accentColor else Color.Transparent)
                                        .clickable { onUpdateSettings { it.copy(brightnessMode = mode) } }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = if (isSel) TextDark else TextSecondaryGrey,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // HUD Windshield Mirror Projection Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("HUD Mirrored Windshield Projection", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryWhite)
                                Text("Flips HUD text and dials horizontally for glass reflection", fontSize = 10.sp, color = TextSecondaryGrey)
                            }
                            Switch(
                                checked = settings.isHudMirrored,
                                onCheckedChange = { onUpdateSettings { s -> s.copy(isHudMirrored = it) } },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = accentColor
                                )
                            )
                        }
                    }
                }

                // Section 5: Theme Accent Options (BMW Neue Klasse & M-Power)
                SettingsSection(title = "NEUE KLASSE COCKPIT THEME", icon = Icons.Default.ColorLens) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ThemeAccent.values().forEach { accent ->
                            val isSel = (settings.themeAccent == accent)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x8005070A))
                                    .border(
                                        2.dp,
                                        if (isSel) accent.primary else Color(0x1AFFFFFF),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onUpdateSettings { it.copy(themeAccent = accent) } }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(accent.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSel) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = accent.label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimaryWhite,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 6: Android System & Default Launcher
                SettingsSection(title = "ANDROID HOME LAUNCHER (ROLEMANAGER)", icon = Icons.Default.Home) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val statusBorderColor = if (isDefaultLauncher) BmwOptimalGreen else BmwAmber
                        val statusBgColor = if (isDefaultLauncher) BmwOptimalGreen.copy(alpha = 0.15f) else BmwAmber.copy(alpha = 0.15f)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(statusBgColor)
                                .border(1.dp, statusBorderColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isDefaultLauncher) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = statusBorderColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isDefaultLauncher) "ROLE_HOME STATUS: ACTIVE DEFAULT" else "ROLE_HOME STATUS: NOT DEFAULT LAUNCHER",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.5.sp,
                                        color = statusBorderColor
                                    )
                                    Text(
                                        text = if (isDefaultLauncher) {
                                            "RoleManager confirms Vision HUD is the active default home app. The system Home button and boot will load Vision HUD."
                                        } else {
                                            "Another app is currently the default Android home screen. Assign Vision HUD as default for in-car dashboard operation."
                                        },
                                        fontSize = 10.sp,
                                        color = TextSecondaryGrey,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }

                        // Button 1: RoleManager Dialog Request
                        Button(
                            onClick = onRequestDefaultLauncher,
                            colors = ButtonDefaults.buttonColors(containerColor = BmwCyan),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = TextDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "REQUEST ROLE_HOME (SYSTEM ROLE DIALOG)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = TextDark
                            )
                        }

                        // Button 2: Direct System Settings Intent
                        OutlinedButton(
                            onClick = onOpenHomeSettings,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryWhite),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = BmwCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "OPEN SYSTEM DEFAULT HOME SETTINGS (INTENT)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = TextPrimaryWhite
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GlassCardFill)
            .border(1.dp, GlassCardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondaryGrey,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = TextSecondaryGrey
            )
        }

        content()
    }
}
