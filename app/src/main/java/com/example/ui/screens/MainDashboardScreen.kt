package com.example.ui.screens

import android.content.res.Configuration
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DriveMode
import com.example.model.TelemetryData
import com.example.model.UserSettings
import com.example.ui.components.BmwCenterHeroGauge
import com.example.ui.components.CockpitDock
import com.example.ui.components.FuelAndMediaCard
import com.example.ui.components.ObdLiveMetricsCard
import com.example.ui.components.TopCockpitBar
import com.example.ui.theme.BmwAmber
import com.example.ui.theme.BmwCyan
import com.example.ui.theme.OledBlack
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondaryGrey
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainDashboardScreen(
    telemetry: TelemetryData,
    settings: UserSettings,
    isDefaultLauncher: Boolean,
    onSelectDriveMode: (DriveMode) -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onNavigate: (String) -> Unit,
    onOpenObdScreen: () -> Unit,
    onMakeDefaultLauncher: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val currentAccent = telemetry.driveMode.primaryColor

    var currentTimeString by remember { mutableStateOf("") }
    var isBannerDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(settings.is24HourClock) {
        while (true) {
            val now = Date()
            val format = if (settings.is24HourClock) SimpleDateFormat("HH:mm", Locale.getDefault()) else SimpleDateFormat("hh:mm a", Locale.getDefault())
            currentTimeString = format.format(now)
            delay(1000)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
            .testTag("main_dashboard_screen")
    ) {
        // Ambient Horizon Mesh Glow (subtle BMW i-Blue or M-Sport aura)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 600.dp, height = 300.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(telemetry.driveMode.glowColor.copy(alpha = 0.25f), Color.Transparent),
                        radius = 450f
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Persistent Top Cockpit Bar
            TopCockpitBar(
                telemetry = telemetry,
                currentTimeString = currentTimeString,
                onOpenObdScreen = onOpenObdScreen,
                isDefaultLauncher = isDefaultLauncher,
                isSimulating = settings.isSimulatingTelemetry,
                onMakeDefaultLauncher = onMakeDefaultLauncher,
                modifier = Modifier.statusBarsPadding()
            )

            // Optional Glanceable Default Launcher Prompt Banner (When not default)
            if (!isDefaultLauncher && !isBannerDismissed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xE60A1320))
                        .border(1.dp, BmwCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("launcher_prompt_banner")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = BmwCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "VISION HUD LAUNCHER COCKPIT",
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp,
                                    color = BmwCyan
                                )
                                Text(
                                    text = "Set Vision HUD as your Android Home screen to lock in the automotive cockpit.",
                                    fontSize = 10.sp,
                                    color = TextSecondaryGrey
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onMakeDefaultLauncher,
                                colors = ButtonDefaults.buttonColors(containerColor = BmwCyan),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "SET DEFAULT HOME",
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = TextDark
                                )
                            }

                            IconButton(
                                onClick = { isBannerDismissed = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = TextSecondaryGrey,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Main 3-Zone Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLandscape) {
                    // Landscape 3-Zone Layout
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Zone (OBD Live Engine Metrics)
                        ObdLiveMetricsCard(
                            telemetry = telemetry,
                            tempUnit = settings.tempUnit,
                            onSelectDriveMode = onSelectDriveMode,
                            onOpenObdScreen = onOpenObdScreen,
                            modifier = Modifier
                                .weight(1.05f)
                                .fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Center Zone (Hero Tachometer & Speedometer)
                        BmwCenterHeroGauge(
                            telemetry = telemetry,
                            speedUnit = settings.speedUnit,
                            accentColor = currentAccent,
                            modifier = Modifier
                                .weight(1.4f)
                                .fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Right Zone (Fuel, Consumption, Clock & Media)
                        FuelAndMediaCard(
                            telemetry = telemetry,
                            is24Hour = settings.is24HourClock,
                            accentColor = currentAccent,
                            onTogglePlayback = onTogglePlayback,
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSkipPrevious,
                            modifier = Modifier
                                .weight(1.05f)
                                .fillMaxWidth()
                        )
                    }
                } else {
                    // Portrait Responsive Layout
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        BmwCenterHeroGauge(
                            telemetry = telemetry,
                            speedUnit = settings.speedUnit,
                            accentColor = currentAccent,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ObdLiveMetricsCard(
                                telemetry = telemetry,
                                tempUnit = settings.tempUnit,
                                onSelectDriveMode = onSelectDriveMode,
                                onOpenObdScreen = onOpenObdScreen,
                                modifier = Modifier.weight(1f)
                            )

                            FuelAndMediaCard(
                                telemetry = telemetry,
                                is24Hour = settings.is24HourClock,
                                accentColor = currentAccent,
                                onTogglePlayback = onTogglePlayback,
                                onSkipNext = onSkipNext,
                                onSkipPrevious = onSkipPrevious,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }

            // Bottom Floating Cockpit Dock
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                CockpitDock(
                    accentColor = currentAccent,
                    isAllAppsActive = false,
                    onNavigate = onNavigate
                )
            }
        }
    }
}
