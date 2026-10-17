package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AppsRepository
import com.example.data.TelemetryRepository
import com.example.model.DriveMode
import com.example.ui.screens.AllAppsScreen
import com.example.ui.screens.HudMirrorScreen
import com.example.ui.screens.MainDashboardScreen
import com.example.ui.screens.ObdConnectionScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.VisionHudTheme
import kotlinx.coroutines.launch

enum class CockpitScreen {
    DASHBOARD,
    ALL_APPS,
    HUD_MIRROR,
    OBD_CONNECT,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private lateinit var telemetryRepository: TelemetryRepository
    private lateinit var appsRepository: AppsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen alive on vehicle mount
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        telemetryRepository = TelemetryRepository(applicationContext)
        appsRepository = AppsRepository(applicationContext)

        setContent {
            val telemetry by telemetryRepository.telemetry.collectAsState()
            val settings by telemetryRepository.settings.collectAsState()
            val adapters by telemetryRepository.detectedAdapters.collectAsState()
            val isScanning by telemetryRepository.isScanning.collectAsState()
            val obdStatus by telemetryRepository.obdStatus.collectAsState()
            val diagnosticLogs by telemetryRepository.diagnosticLogs.collectAsState()
            val installedApps by appsRepository.installedApps.collectAsState()

            var currentScreen by remember { mutableStateOf(CockpitScreen.DASHBOARD) }
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            var isDefaultLauncher by remember { mutableStateOf(appsRepository.isDefaultLauncher()) }
            val lifecycleOwner = LocalLifecycleOwner.current

            // Permission Request Launcher for Bluetooth & Location
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val btGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
                } else true

                if (btGranted) {
                    telemetryRepository.realObdManager.refreshDevices()
                    telemetryRepository.realObdManager.autoConnectIfAvailable()
                }
            }

            LaunchedEffect(Unit) {
                val neededPermissions = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        neededPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                        neededPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
                    }
                }
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                }

                if (neededPermissions.isNotEmpty()) {
                    permissionLauncher.launch(neededPermissions.toTypedArray())
                }
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        isDefaultLauncher = appsRepository.isDefaultLauncher()
                        appsRepository.loadAppsAsync()
                        telemetryRepository.realObdManager.refreshDevices()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // BackHandler for all secondary screens to return to main dashboard
            if (currentScreen != CockpitScreen.DASHBOARD) {
                BackHandler {
                    currentScreen = CockpitScreen.DASHBOARD
                }
            }

            VisionHudTheme(accentColor = settings.themeAccent.primary) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    val modifier = Modifier.fillMaxSize()

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screenTransition"
                    ) { screen ->
                        when (screen) {
                            CockpitScreen.DASHBOARD -> {
                                MainDashboardScreen(
                                    telemetry = telemetry,
                                    settings = settings,
                                    isDefaultLauncher = isDefaultLauncher,
                                    onSelectDriveMode = { mode ->
                                        telemetryRepository.setDriveMode(mode)
                                    },
                                    onTogglePlayback = {
                                        telemetryRepository.toggleMediaPlayback()
                                    },
                                    onNavigate = { destination ->
                                        when (destination) {
                                            "all_apps" -> currentScreen = CockpitScreen.ALL_APPS
                                            "hud_mirror" -> currentScreen = CockpitScreen.HUD_MIRROR
                                            "settings" -> currentScreen = CockpitScreen.SETTINGS
                                            "maps" -> {
                                                val launched = appsRepository.launchApp("com.google.android.apps.maps")
                                                if (!launched) {
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Opening Navigation...")
                                                    }
                                                }
                                            }
                                            "music" -> {
                                                val launched = appsRepository.launchApp("com.spotify.music")
                                                if (!launched) {
                                                    telemetryRepository.toggleMediaPlayback()
                                                }
                                            }
                                            "android_auto" -> {
                                                val launched = appsRepository.launchApp("com.google.android.projection.gearhead")
                                                if (!launched) {
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Android Auto ready")
                                                    }
                                                }
                                            }
                                            "carplay" -> {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Apple CarPlay wireless projection link ready")
                                                }
                                            }
                                        }
                                    },
                                    onOpenObdScreen = {
                                        currentScreen = CockpitScreen.OBD_CONNECT
                                    },
                                    onMakeDefaultLauncher = {
                                        appsRepository.makeDefaultLauncher(this@MainActivity)
                                    },
                                    onOpenHomeSettings = {
                                        appsRepository.openHomeSettings(this@MainActivity)
                                    },
                                    modifier = modifier
                                )
                            }

                            CockpitScreen.ALL_APPS -> {
                                AllAppsScreen(
                                    telemetry = telemetry,
                                    apps = if (installedApps.isNotEmpty()) installedApps else appsRepository.getInstalledApps(),
                                    isDefaultLauncher = isDefaultLauncher,
                                    onLaunchApp = { pkg ->
                                        appsRepository.launchApp(pkg)
                                    },
                                    onMakeDefaultLauncher = {
                                        appsRepository.makeDefaultLauncher(this@MainActivity)
                                    },
                                    onBackToCockpit = {
                                        currentScreen = CockpitScreen.DASHBOARD
                                    },
                                    modifier = modifier
                                )
                            }

                            CockpitScreen.HUD_MIRROR -> {
                                HudMirrorScreen(
                                    telemetry = telemetry,
                                    speedUnit = settings.speedUnit,
                                    onExit = {
                                        currentScreen = CockpitScreen.DASHBOARD
                                    },
                                    modifier = modifier
                                )
                            }

                            CockpitScreen.OBD_CONNECT -> {
                                ObdConnectionScreen(
                                    telemetry = telemetry,
                                    settings = settings,
                                    adapters = adapters,
                                    obdStatus = obdStatus,
                                    diagnosticLogs = diagnosticLogs,
                                    isScanning = isScanning,
                                    onTriggerScan = {
                                        telemetryRepository.triggerScan()
                                    },
                                    onConnectAdapter = { adapterId ->
                                        telemetryRepository.connectToAdapter(adapterId)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Connecting to OBD-II CAN-Bus...")
                                        }
                                    },
                                    onDisconnectAdapter = {
                                        telemetryRepository.disconnectAdapter()
                                        scope.launch {
                                            snackbarHostState.showSnackbar("OBD-II adapter disconnected")
                                        }
                                    },
                                    onToggleSimulation = { enable ->
                                        telemetryRepository.updateSettings { it.copy(isSimulatingTelemetry = enable) }
                                    },
                                    onBack = {
                                        currentScreen = CockpitScreen.DASHBOARD
                                    },
                                    modifier = modifier
                                )
                            }

                            CockpitScreen.SETTINGS -> {
                                SettingsScreen(
                                    settings = settings,
                                    isDefaultLauncher = isDefaultLauncher,
                                    onUpdateSettings = { transform ->
                                        telemetryRepository.updateSettings(transform)
                                    },
                                    onOpenHudMirror = {
                                        currentScreen = CockpitScreen.HUD_MIRROR
                                    },
                                    onRequestDefaultLauncher = {
                                        appsRepository.makeDefaultLauncher(this@MainActivity)
                                    },
                                    onOpenHomeSettings = {
                                        appsRepository.openHomeSettings(this@MainActivity)
                                    },
                                    onBack = {
                                        currentScreen = CockpitScreen.DASHBOARD
                                    },
                                    modifier = modifier
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
