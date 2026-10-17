package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ObdAdapterDevice
import com.example.model.ObdConnectionStatus
import com.example.model.ObdConnectionType
import com.example.model.TelemetryData
import com.example.model.UserSettings
import com.example.ui.theme.BmwAmber
import com.example.ui.theme.BmwCrimson
import com.example.ui.theme.BmwCyan
import com.example.ui.theme.BmwOptimalGreen
import com.example.ui.theme.CockpitSurfaceLowest
import com.example.ui.theme.GlassCardBorder
import com.example.ui.theme.GlassCardFill
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondaryGrey

@Composable
fun ObdConnectionScreen(
    telemetry: TelemetryData,
    settings: UserSettings,
    adapters: List<ObdAdapterDevice>,
    obdStatus: ObdConnectionStatus,
    diagnosticLogs: List<String>,
    isScanning: Boolean,
    onTriggerScan: () -> Unit,
    onConnectAdapter: (String) -> Unit,
    onDisconnectAdapter: () -> Unit,
    onToggleSimulation: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = telemetry.driveMode.primaryColor
    val logListState = rememberLazyListState()

    // Request Bluetooth and Location permissions if needed
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onTriggerScan()
    }

    LaunchedEffect(diagnosticLogs.size) {
        if (diagnosticLogs.isNotEmpty()) {
            logListState.animateScrollToItem(diagnosticLogs.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CockpitSurfaceLowest)
            .testTag("obd_connection_screen")
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

                // Status Badge in Header
                val statusText = when (obdStatus) {
                    ObdConnectionStatus.CONNECTED -> "REAL OBD-II CAN LINKED"
                    ObdConnectionStatus.CONNECTING -> "ESTABLISHING RFCOMM / TCP..."
                    ObdConnectionStatus.INITIALIZING_ELM -> "ELM327 HANDSHAKING..."
                    ObdConnectionStatus.BUS_INIT_CAN -> "BUS AUTO-DETECT..."
                    ObdConnectionStatus.SCANNING -> "SCANNING BLUETOOTH..."
                    ObdConnectionStatus.ERROR -> "CONNECTION FAILED"
                    ObdConnectionStatus.DISCONNECTED -> "STANDALONE (DISCONNECTED)"
                }
                val statusColor = when (obdStatus) {
                    ObdConnectionStatus.CONNECTED -> BmwOptimalGreen
                    ObdConnectionStatus.CONNECTING, ObdConnectionStatus.INITIALIZING_ELM -> BmwCyan
                    ObdConnectionStatus.ERROR -> BmwCrimson
                    else -> TextSecondaryGrey
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(statusColor.copy(alpha = 0.2f))
                        .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp,
                            color = statusColor
                        )
                    }
                }

                // Scan / Refresh Button
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            )
                        } else {
                            onTriggerScan()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x334CC2FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = BmwCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SCANNING...", fontSize = 11.sp, color = BmwCyan)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan",
                            tint = BmwCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("RESCAN BLUETOOTH", fontSize = 11.sp, color = BmwCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Split View: Left (OBD Port Graphic & Live Diagnostic Terminal) / Right (Adapters List)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: OBD Diagnostic Port Diagram & Live Terminal Logs
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(GlassCardFill)
                        .border(1.dp, GlassCardBorder, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "VEHICLE DIAGNOSTIC PORT (SAE J1962)",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = TextSecondaryGrey
                    )

                    // 16-Pin OBD-II Port Visual Diagram
                    ObdPortDiagram()

                    // Desk Simulation Mode Toggle (for testing without vehicle connected)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x8005070A))
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Desk Simulation Telemetry",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = TextPrimaryWhite
                            )
                            Text(
                                text = "Use when testing app away from car",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 9.sp,
                                color = TextSecondaryGrey
                            )
                        }
                        Switch(
                            checked = settings.isSimulatingTelemetry,
                            onCheckedChange = onToggleSimulation,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BmwCyan
                            )
                        )
                    }

                    // Real Live Terminal Diagnostics Console
                    Text(
                        text = "LIVE CAN-BUS PROTOCOL STREAM",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = TextSecondaryGrey
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xE605070A))
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        LazyColumn(
                            state = logListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(diagnosticLogs) { logEntry ->
                                val color = when {
                                    logEntry.contains("RX:") -> BmwOptimalGreen
                                    logEntry.contains("TX:") -> BmwCyan
                                    logEntry.contains("ERROR") || logEntry.contains("Warning") -> BmwCrimson
                                    logEntry.contains("CAN:") -> Color(0xFFFFD54F)
                                    else -> TextSecondaryGrey
                                }
                                Text(
                                    text = logEntry,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = color,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }

                // Right Column: Detected & Paired Real Adapters
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(GlassCardFill)
                        .border(1.dp, GlassCardBorder, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AVAILABLE ADAPTERS (${adapters.size})",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = TextSecondaryGrey
                        )

                        Text(
                            text = "Pair in phone Bluetooth first",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(adapters, key = { it.id }) { device ->
                            val isCurrentConnected = (obdStatus == ObdConnectionStatus.CONNECTED || obdStatus == ObdConnectionStatus.INITIALIZING_ELM) &&
                                (telemetry.obdAdapterName == device.name)
                            ObdAdapterCard(
                                device = device,
                                isConnected = isCurrentConnected,
                                onConnect = { onConnectAdapter(device.id) },
                                onDisconnect = { onDisconnectAdapter() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObdPortDiagram() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF070B10))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
    ) {
        val w = size.width
        val h = size.height

        // Outer connector shell
        drawRoundRect(
            color = Color(0x33FFFFFF),
            topLeft = Offset(w * 0.08f, h * 0.15f),
            size = Size(w * 0.84f, h * 0.7f),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 2f)
        )

        // 16 Pins (2 rows of 8 pins)
        val startX = w * 0.15f
        val stepX = (w * 0.7f) / 7f
        val row1Y = h * 0.35f
        val row2Y = h * 0.65f

        for (i in 0 until 8) {
            val px = startX + i * stepX
            val isPin6CanHigh = (i == 5)
            val color1 = if (isPin6CanHigh) BmwCyan else Color(0x66FFFFFF)
            drawCircle(color = color1, radius = 4.5f, center = Offset(px, row1Y))

            val isPin14CanLow = (i == 5)
            val isPin16Power = (i == 7)
            val color2 = when {
                isPin14CanLow -> BmwCyan
                isPin16Power -> BmwCrimson
                else -> Color(0x66FFFFFF)
            }
            drawCircle(color = color2, radius = 4.5f, center = Offset(px, row2Y))
        }
    }
}

@Composable
private fun ObdAdapterCard(
    device: ObdAdapterDevice,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isConnected) Color(0x2610B981) else Color(0x8005070A))
            .border(
                1.dp,
                if (isConnected) BmwOptimalGreen.copy(alpha = 0.6f) else Color(0x1FFFFFFF),
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Connection icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isConnected) BmwOptimalGreen.copy(alpha = 0.2f) else Color(0x1AFFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (device.type == ObdConnectionType.WIFI) Icons.Default.Wifi else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (isConnected) BmwOptimalGreen else TextPrimaryWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.name,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TextPrimaryWhite
                        )
                        if (device.isPaired) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x334CC2FF))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("PAIRED", fontSize = 8.sp, color = BmwCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = device.address,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TextSecondaryGrey
                        )
                        Text(
                            text = "${device.signalDbm} dBm",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                    Text(
                        text = device.protocol,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 9.sp,
                        color = BmwCyan
                    )
                }
            }

            // Action Button
            if (isConnected) {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(containerColor = BmwCrimson.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("DISCONNECT", fontSize = 10.sp, color = BmwCrimson, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = BmwCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("CONNECT", fontSize = 10.sp, color = TextDark, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
