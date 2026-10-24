package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.obd.DtcClearResult
import com.example.data.obd.DtcClearStatus
import com.example.model.ObdAdapterDevice
import com.example.model.ObdConnectionStatus
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
import kotlinx.coroutines.launch

/**
 * Validates whether the returned permission results map satisfies the Bluetooth scan & connect
 * requirements for the active Android SDK version.
 */
fun areBluetoothScanPermissionsGranted(
    permissions: Map<String, Boolean>,
    sdkInt: Int = Build.VERSION.SDK_INT
): Boolean {
    if (permissions.isEmpty()) return false
    return if (sdkInt >= Build.VERSION_CODES.S) {
        permissions[Manifest.permission.BLUETOOTH_CONNECT] == true &&
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true
    } else {
        permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
}

/**
 * Checks whether the Context already holds the required runtime permissions for Bluetooth scanning.
 */
fun hasBluetoothScanPermissions(
    context: Context,
    sdkInt: Int = Build.VERSION.SDK_INT
): Boolean {
    return if (sdkInt >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }
}

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
    onScanDtc: suspend () -> List<String> = { emptyList() },
    onClearDtc: suspend () -> DtcClearResult = {
        DtcClearResult(
            status = DtcClearStatus.NOT_CONNECTED,
            commandAccepted = false,
            verifiedClear = false,
            message = "Adapter not connected."
        )
    },
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentColor = telemetry.driveMode.primaryColor
    val logListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var isDtcScanning by remember { mutableStateOf(false) }
    var dtcStatusMessage by remember { mutableStateOf("") }
    var dtcClearStatus by remember { mutableStateOf<DtcClearStatus?>(null) }
    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (areBluetoothScanPermissionsGranted(permissions)) {
            permissionDeniedMessage = null
            onTriggerScan()
        } else {
            permissionDeniedMessage =
                "Bluetooth Scan & Connect permissions are required to discover OBD-II adapters."
        }
    }

    // Non-animating, user-scroll-aware live log follow:
    // Keys on the latest log entry (since the ring buffer is capped at 35 entries) and uses instant
    // scrollToItem() only when the user is already near the bottom and not actively scrolling.
    val latestLogEntry = diagnosticLogs.lastOrNull()
    LaunchedEffect(latestLogEntry) {
        if (diagnosticLogs.isNotEmpty() && !logListState.isScrollInProgress) {
            val visibleItems = logListState.layoutInfo.visibleItemsInfo
            val lastVisibleIndex = visibleItems.lastOrNull()?.index ?: -1
            val isNearBottom = visibleItems.isEmpty() || lastVisibleIndex >= (diagnosticLogs.lastIndex - 3)
            if (isNearBottom) {
                logListState.scrollToItem(diagnosticLogs.lastIndex)
            }
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
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                    ObdConnectionStatus.CONNECTING -> "ESTABLISHING LINK..."
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

                // Scan / Refresh Button with verified permission check
                Button(
                    onClick = {
                        if (hasBluetoothScanPermissions(context)) {
                            permissionDeniedMessage = null
                            onTriggerScan()
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            )
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x334CC2FF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("rescan_bluetooth_button")
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

            // Permission Denied Warning Banner if user declined Bluetooth permissions
            permissionDeniedMessage?.let { deniedMsg ->
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BmwCrimson.copy(alpha = 0.2f))
                        .border(1.dp, BmwCrimson, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("permission_denied_banner")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = BmwCrimson,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = deniedMsg,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFFFF8888)
                        )
                    }
                }
            }

            // Simulation Active Warning Banner
            if (settings.isSimulatingTelemetry) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BmwAmber.copy(alpha = 0.2f))
                        .border(1.dp, BmwAmber, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = BmwAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SIMULATION ACTIVE: Synthetic telemetry enabled for desk testing. Turn off when connected to vehicle.",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = BmwAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Split View: Left (Terminal & Port) / Right (Adapters & DTC Diagnostic Tools)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: OBD Diagnostic Port Diagram & Live Terminal Logs
                Column(
                    modifier = Modifier
                        .weight(1.05f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(GlassCardFill)
                        .border(1.dp, GlassCardBorder, RoundedCornerShape(20.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "VEHICLE DIAGNOSTIC PORT (SAE J1962)",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = TextSecondaryGrey
                    )

                    ObdPortDiagram()

                    // Desk Simulation Mode Toggle
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
                                text = "Simulates RPM, Speed & Load away from vehicle",
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
                                    logEntry.contains("ERROR") || logEntry.contains("Warning") || logEntry.contains("rejected") -> BmwCrimson
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

                // Right Column: Available Adapters + DTC Fault Code Tools
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(GlassCardFill)
                        .border(1.dp, GlassCardBorder, RoundedCornerShape(20.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // DTC Trouble Code Scanner Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xE605070A))
                            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(14.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = null,
                                        tint = if (telemetry.isMilActive || telemetry.dtcCodes.isNotEmpty()) BmwAmber else BmwOptimalGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ECU DIAGNOSTIC TROUBLE CODES (DTC)",
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = TextPrimaryWhite
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Scan DTC Button
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isDtcScanning = true
                                                val codes = onScanDtc()
                                                isDtcScanning = false
                                                dtcClearStatus = null
                                                dtcStatusMessage = if (codes.isEmpty()) {
                                                    "No fault codes found"
                                                } else {
                                                    "${codes.size} fault code(s) found"
                                                }
                                            }
                                        },
                                        enabled = !isDtcScanning,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x334CC2FF)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (isDtcScanning) "SCANNING..." else "SCAN DTC (MODE 03)",
                                            fontSize = 9.sp,
                                            color = BmwCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Clear DTC Button
                                    if (telemetry.dtcCodes.isNotEmpty() || telemetry.isMilActive) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    val clearResult = onClearDtc()
                                                    dtcClearStatus = clearResult.status
                                                    dtcStatusMessage = clearResult.message
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = BmwCrimson.copy(alpha = 0.3f)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = BmwCrimson,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("CLEAR (MODE 04)", fontSize = 9.sp, color = BmwCrimson, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // DTC Results & Clear Verification Status
                            if (telemetry.dtcCodes.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    telemetry.dtcCodes.forEach { code ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(BmwCrimson.copy(alpha = 0.25f))
                                                .border(1.dp, BmwCrimson, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(code, fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color(0xFFFF8888))
                                        }
                                    }
                                }
                            }

                            val statusColor = when (dtcClearStatus) {
                                DtcClearStatus.VERIFIED_CLEARED -> BmwOptimalGreen
                                DtcClearStatus.ACCEPTED_CODES_REMAIN -> BmwAmber
                                DtcClearStatus.REJECTED_OR_FAILED, DtcClearStatus.NOT_CONNECTED -> BmwCrimson
                                null -> TextSecondaryGrey
                            }
                            val fallbackText = if (obdStatus == ObdConnectionStatus.CONNECTED) {
                                "No active fault codes recorded in ECU."
                            } else {
                                "Connect to vehicle adapter to scan trouble codes."
                            }

                            Text(
                                text = dtcStatusMessage.ifEmpty { fallbackText },
                                fontSize = 10.sp,
                                fontWeight = if (dtcClearStatus != null) FontWeight.Bold else FontWeight.Normal,
                                color = statusColor
                            )
                        }
                    }

                    // Available Adapters List
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DETECTED ADAPTERS (${adapters.size})",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = TextSecondaryGrey
                        )

                        Text(
                            text = "Pair in phone Bluetooth settings first",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                    }

                    if (adapters.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x8005070A))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "NO PAIRED BLUETOOTH ADAPTERS FOUND",
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = TextPrimaryWhite
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Plug in your ELM327 / OBDLink adapter, open Android Bluetooth settings, pair with PIN (1234 or 0000), then tap Rescan.",
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 10.sp,
                                    color = TextSecondaryGrey,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(adapters, key = { it.id }) { device ->
                                val isCurrentConnected =
                                    (obdStatus == ObdConnectionStatus.CONNECTED || obdStatus == ObdConnectionStatus.INITIALIZING_ELM) &&
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
}

@Composable
private fun ObdPortDiagram() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF070B10))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
    ) {
        val w = size.width
        val h = size.height

        drawRoundRect(
            color = Color(0x33FFFFFF),
            topLeft = Offset(w * 0.08f, h * 0.15f),
            size = Size(w * 0.84f, h * 0.7f),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 2f)
        )

        val startX = w * 0.15f
        val stepX = (w * 0.7f) / 7f
        val row1Y = h * 0.35f
        val row2Y = h * 0.65f

        for (i in 0 until 8) {
            val px = startX + i * stepX
            val isPin6CanHigh = (i == 5)
            val color1 = if (isPin6CanHigh) BmwCyan else Color(0x66FFFFFF)
            drawCircle(color = color1, radius = 4f, center = Offset(px, row1Y))

            val isPin14CanLow = (i == 5)
            val isPin16Power = (i == 7)
            val color2 = when {
                isPin14CanLow -> BmwCyan
                isPin16Power -> BmwCrimson
                else -> Color(0x66FFFFFF)
            }
            drawCircle(color = color2, radius = 4f, center = Offset(px, row2Y))
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
            .clip(RoundedCornerShape(12.dp))
            .background(if (isConnected) Color(0x2610B981) else Color(0x8005070A))
            .border(
                1.dp,
                if (isConnected) BmwOptimalGreen.copy(alpha = 0.6f) else Color(0x1FFFFFFF),
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp)
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
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isConnected) BmwOptimalGreen.copy(alpha = 0.2f) else Color(0x1AFFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (isConnected) BmwOptimalGreen else TextPrimaryWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.name,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimaryWhite
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x334CC2FF))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(device.type.label, fontSize = 9.sp, color = BmwCyan, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = device.address,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextSecondaryGrey
                    )
                    Text(
                        text = device.protocol,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            if (isConnected) {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(containerColor = BmwCrimson.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("DISCONNECT", fontSize = 11.sp, color = BmwCrimson, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = BmwCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("CONNECT", fontSize = 11.sp, color = TextDark, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
