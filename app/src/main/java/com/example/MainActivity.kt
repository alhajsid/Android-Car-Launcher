package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
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
import com.example.model.DisplayBrightnessMode
import com.example.model.DriveMode
import com.example.ui.components.PhoneProjectionDialog
import com.example.ui.components.ProjectionType
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

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var telemetryRepository: TelemetryRepository
    private lateinit var appsRepository: AppsRepository
    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var currentBrightnessMode: DisplayBrightnessMode = DisplayBrightnessMode.NIGHT_OLED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen awake for vehicle mount
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        telemetryRepository = TelemetryRepository(applicationContext)
        appsRepository = AppsRepository(applicationContext)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

        setContent {
            val telemetry by telemetryRepository.telemetry.collectAsState()
            val settings by telemetryRepository.settings.collectAsState()
            val adapters by telemetryRepository.detectedAdapters.collectAsState()
            val isScanning by telemetryRepository.isScanning.collectAsState()
            val obdStatus by telemetryRepository.obdStatus.collectAsState()
            val diagnosticLogs by telemetryRepository.diagnosticLogs.collectAsState()
            val installedApps by appsRepository.installedApps.collectAsState()

            var currentScreen by remember { mutableStateOf(CockpitScreen.DASHBOARD) }
            var activeProjectionDialog by remember { mutableStateOf<ProjectionType?>(null) }
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            var isDefaultLauncher by remember { mutableStateOf(appsRepository.isDefaultLauncher()) }
            val lifecycleOwner = LocalLifecycleOwner.current

            // Update brightness based on user settings
            LaunchedEffect(settings.brightnessMode) {
                currentBrightnessMode = settings.brightnessMode
                applyBrightnessMode(settings.brightnessMode)
            }

            // Permission Request Launcher for Bluetooth & Location
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val locGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                if (locGranted) {
                    telemetryRepository.registerGpsListener()
                }

                val btGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
                } else true

                if (btGranted) {
                    telemetryRepository.startObdServiceIfPermitted()
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
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
                        telemetryRepository.registerGpsListener()
                        if (currentBrightnessMode == DisplayBrightnessMode.AUTO && lightSensor != null) {
                            sensorManager?.registerListener(this@MainActivity, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
                        }
                    } else if (event == Lifecycle.Event.ON_PAUSE) {
                        sensorManager?.unregisterListener(this@MainActivity)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    sensorManager?.unregisterListener(this@MainActivity)
                }
            }

            // BackHandler for all secondary screens to return to main dashboard
            if (currentScreen != CockpitScreen.DASHBOARD) {
                BackHandler {
                    currentScreen = CockpitScreen.DASHBOARD
                }
            }

            VisionHudTheme(
                accentColor = settings.themeAccent.primary,
                scaleFactor = settings.headUnitScale.scaleFactor
            ) {
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
                                    onSkipNext = {
                                        telemetryRepository.skipNextMedia()
                                    },
                                    onSkipPrevious = {
                                        telemetryRepository.skipPrevMedia()
                                    },
                                    onNavigate = { destination ->
                                        when (destination) {
                                            "all_apps" -> currentScreen = CockpitScreen.ALL_APPS
                                            "hud_mirror" -> currentScreen = CockpitScreen.HUD_MIRROR
                                            "settings" -> currentScreen = CockpitScreen.SETTINGS
                                            "obd_diagnostics" -> currentScreen = CockpitScreen.OBD_CONNECT
                                            "maps" -> {
                                                val launched = appsRepository.launchApp("com.google.android.apps.maps")
                                                    || appsRepository.launchApp("com.waze")
                                                if (!launched) {
                                                    try {
                                                        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=gas+station")).apply {
                                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                        }
                                                        startActivity(geoIntent)
                                                    } catch (_: Exception) {
                                                        scope.launch { snackbarHostState.showSnackbar("No maps app found") }
                                                    }
                                                }
                                            }
                                            "music" -> {
                                                val launched = appsRepository.launchApp("com.spotify.music")
                                                    || appsRepository.launchApp("com.google.android.apps.youtube.music")
                                                if (!launched) {
                                                    telemetryRepository.toggleMediaPlayback()
                                                }
                                            }
                                            "phone" -> {
                                                try {
                                                    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    }
                                                    startActivity(dialIntent)
                                                } catch (_: Exception) {
                                                    scope.launch { snackbarHostState.showSnackbar("Dialer unavailable") }
                                                }
                                            }
                                            "carplay" -> {
                                                val launched = appsRepository.launchCarPlay(settings.customCarPlayComponent)
                                                if (!launched) {
                                                    activeProjectionDialog = ProjectionType.CARPLAY
                                                }
                                            }
                                            "android_auto" -> {
                                                val launched = appsRepository.launchAndroidAuto(settings.customAndroidAutoComponent)
                                                if (!launched) {
                                                    activeProjectionDialog = ProjectionType.ANDROID_AUTO
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
                                    settings = settings,
                                    apps = if (installedApps.isNotEmpty()) installedApps else appsRepository.getInstalledApps(),
                                    isDefaultLauncher = isDefaultLauncher,
                                    onLaunchApp = { pkg ->
                                        when {
                                            pkg == "carplay" -> {
                                                val launched = appsRepository.launchCarPlay(settings.customCarPlayComponent)
                                                if (!launched) activeProjectionDialog = ProjectionType.CARPLAY
                                            }
                                            pkg == "android_auto" -> {
                                                val launched = appsRepository.launchAndroidAuto(settings.customAndroidAutoComponent)
                                                if (!launched) activeProjectionDialog = ProjectionType.ANDROID_AUTO
                                            }
                                            else -> {
                                                // Launch the exact installed activity ComponentName directly
                                                val directLaunched = appsRepository.launchApp(pkg)
                                                if (!directLaunched) {
                                                    if (pkg.contains("zlink", ignoreCase = true) || pkg.contains("carplay", ignoreCase = true)) {
                                                        val cpLaunched = appsRepository.launchCarPlay(settings.customCarPlayComponent)
                                                        if (!cpLaunched) activeProjectionDialog = ProjectionType.CARPLAY
                                                    } else if (pkg.contains("gearhead", ignoreCase = true) || pkg.contains("auto", ignoreCase = true)) {
                                                        val aaLaunched = appsRepository.launchAndroidAuto(settings.customAndroidAutoComponent)
                                                        if (!aaLaunched) activeProjectionDialog = ProjectionType.ANDROID_AUTO
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onToggleFavorite = { pkg ->
                                        telemetryRepository.updateSettings { current ->
                                            val favs = current.favoritePackages.toMutableSet()
                                            if (favs.contains(pkg)) favs.remove(pkg) else favs.add(pkg)
                                            current.copy(favoritePackages = favs)
                                        }
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
                                    isMirrored = settings.isHudMirrored,
                                    onToggleMirror = { mirrored ->
                                        telemetryRepository.updateSettings { it.copy(isHudMirrored = mirrored) }
                                    },
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
                                    onScanDtc = {
                                        telemetryRepository.scanDtc()
                                    },
                                    onClearDtc = {
                                        telemetryRepository.clearDtc()
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
                                    telemetry = telemetry,
                                    isDefaultLauncher = isDefaultLauncher,
                                    onUpdateSettings = { transform ->
                                        telemetryRepository.updateSettings(transform)
                                    },
                                    onCalibrateGear = { gear ->
                                        scope.launch {
                                            telemetryRepository.calibrateCurrentGear(gear)
                                            snackbarHostState.showSnackbar("Calibrated Gear $gear to current speed/RPM")
                                        }
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

                    // Dialog helper for Apple CarPlay and Android Auto on car head units
                    activeProjectionDialog?.let { type ->
                        val allAppsList = if (installedApps.isNotEmpty()) installedApps else appsRepository.getInstalledApps()
                        val currentBound = if (type == ProjectionType.CARPLAY) {
                            settings.customCarPlayComponent
                        } else {
                            settings.customAndroidAutoComponent
                        }

                        PhoneProjectionDialog(
                            type = type,
                            installedApps = allAppsList,
                            currentBoundComponent = currentBound,
                            onSelectAndLaunchApp = { selectedApp ->
                                val componentStr = if (!selectedApp.activityName.isNullOrBlank()) {
                                    "${selectedApp.packageName}/${selectedApp.activityName}"
                                } else {
                                    selectedApp.packageName
                                }
                                telemetryRepository.updateSettings { current ->
                                    if (type == ProjectionType.CARPLAY) {
                                        current.copy(customCarPlayComponent = componentStr)
                                    } else {
                                        current.copy(customAndroidAutoComponent = componentStr)
                                    }
                                }
                                activeProjectionDialog = null
                                appsRepository.launchShortcut(selectedApp)
                            },
                            onDismiss = { activeProjectionDialog = null },
                            onOpenBluetooth = {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    startActivity(intent)
                                } catch (_: Exception) {}
                                activeProjectionDialog = null
                            },
                            onOpenConnectionSettings = {
                                val launchedAuto = if (type == ProjectionType.ANDROID_AUTO) {
                                    appsRepository.launchAndroidAuto()
                                } else {
                                    appsRepository.launchCarPlay()
                                }
                                if (!launchedAuto) {
                                    try {
                                        val castIntent = Intent(android.provider.Settings.ACTION_CAST_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        startActivity(castIntent)
                                    } catch (_: Exception) {
                                        try {
                                            val wirelessIntent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            startActivity(wirelessIntent)
                                        } catch (_: Exception) {
                                            try {
                                                val settingsIntent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                startActivity(settingsIntent)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                                activeProjectionDialog = null
                            }
                        )
                    }
                }
            }
        }
    }

    private fun applyBrightnessMode(mode: DisplayBrightnessMode) {
        val lp = window.attributes
        when (mode) {
            DisplayBrightnessMode.DAY -> {
                sensorManager?.unregisterListener(this)
                lp.screenBrightness = 1.0f
                window.attributes = lp
            }
            DisplayBrightnessMode.NIGHT_OLED -> {
                sensorManager?.unregisterListener(this)
                lp.screenBrightness = 0.25f
                window.attributes = lp
            }
            DisplayBrightnessMode.AUTO -> {
                if (lightSensor != null) {
                    sensorManager?.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
                } else {
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.attributes = lp
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT && currentBrightnessMode == DisplayBrightnessMode.AUTO) {
            val lux = event.values[0]
            val brightness = when {
                lux > 1000f -> 1.0f
                lux > 300f -> 0.75f
                lux > 80f -> 0.50f
                lux > 20f -> 0.35f
                else -> 0.20f
            }
            val lp = window.attributes
            lp.screenBrightness = brightness
            window.attributes = lp
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
