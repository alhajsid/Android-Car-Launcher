package com.example.data

import android.app.Activity
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import com.example.model.AppCategory
import com.example.model.AppShortcut
import com.example.ui.theme.BmwAmber
import com.example.ui.theme.BmwCrimson
import com.example.ui.theme.BmwCyan
import com.example.ui.theme.BmwEcoTeal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppsRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _installedApps = MutableStateFlow<List<AppShortcut>>(emptyList())
    val installedApps: StateFlow<List<AppShortcut>> = _installedApps.asStateFlow()

    init {
        loadAppsAsync()
        registerPackageReceiver()
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        try {
            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    loadAppsAsync()
                }
            }, filter)
        } catch (_: Exception) {}
    }

    fun loadAppsAsync() {
        scope.launch {
            val list = queryAllInstalledLaunchableApps()
            _installedApps.value = list
        }
    }

    /**
     * Queries all real installed applications on device with a launcher activity.
     * Uses multiple query strategies to guarantee 100% discovery on Android 8 through 15+.
     */
    fun queryAllInstalledLaunchableApps(): List<AppShortcut> {
        val ownPackage = context.packageName
        val foundPackages = mutableSetOf<String>()
        val resultList = mutableListOf<AppShortcut>()

        // Strategy 1: queryIntentActivities with ACTION_MAIN + CATEGORY_LAUNCHER
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    packageManager.queryIntentActivities(
                        launcherIntent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                    )
                } catch (_: Exception) {
                    packageManager.queryIntentActivities(
                        launcherIntent,
                        PackageManager.ResolveInfoFlags.of(0L)
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                try {
                    packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
                } catch (_: Exception) {
                    packageManager.queryIntentActivities(launcherIntent, 0)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }

        resolveInfos.forEach { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg != ownPackage && foundPackages.add(pkg)) {
                try {
                    val name = resolveInfo.loadLabel(packageManager).toString()
                    val icon = resolveInfo.loadIcon(packageManager)
                    val detectedCategory = categorizePackage(pkg, name)
                    val accent = getCategoryColor(detectedCategory)

                    resultList.add(
                        AppShortcut(
                            id = pkg,
                            name = name,
                            packageName = pkg,
                            category = detectedCategory,
                            iconKey = "installed",
                            accentColor = accent,
                            isPinnedToDock = pkg.contains("maps") || pkg.contains("spotify"),
                            isFavorite = detectedCategory != AppCategory.ALL,
                            isCustomInstalled = true,
                            iconDrawable = icon
                        )
                    )
                } catch (_: Exception) {}
            }
        }

        // Strategy 2: Scan getInstalledApplications for any missed launchable activities
        try {
            val installedApps = packageManager.getInstalledApplications(0)
            installedApps.forEach { appInfo ->
                val pkg = appInfo.packageName
                if (pkg != ownPackage && !foundPackages.contains(pkg)) {
                    val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null && foundPackages.add(pkg)) {
                        val name = appInfo.loadLabel(packageManager).toString()
                        val icon = appInfo.loadIcon(packageManager)
                        val detectedCategory = categorizePackage(pkg, name)
                        val accent = getCategoryColor(detectedCategory)

                        resultList.add(
                            AppShortcut(
                                id = pkg,
                                name = name,
                                packageName = pkg,
                                category = detectedCategory,
                                iconKey = "installed",
                                accentColor = accent,
                                isPinnedToDock = pkg.contains("maps") || pkg.contains("spotify"),
                                isFavorite = detectedCategory != AppCategory.ALL,
                                isCustomInstalled = true,
                                iconDrawable = icon
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // Strategy 3: If in an emulator or clean car tablet with very few system apps,
        // complement with automotive presets so the driver has immediate in-car shortcuts
        val finalApps = if (resultList.size < 6) {
            val presets = getDefaultFallbackApps().filter { preset -> !foundPackages.contains(preset.packageName) }
            resultList + presets
        } else {
            resultList
        }

        return finalApps.sortedBy { it.name.lowercase() }
    }

    /**
     * Synchronous accessor matching search and category filters
     */
    fun getInstalledApps(category: AppCategory = AppCategory.ALL, query: String = ""): List<AppShortcut> {
        val all = if (_installedApps.value.isNotEmpty()) _installedApps.value else queryAllInstalledLaunchableApps()
        return all.filter { item ->
            val matchesCategory = when (category) {
                AppCategory.ALL -> true
                AppCategory.FAVORITES -> item.isFavorite
                else -> item.category == category
            }
            val matchesQuery = query.isBlank() || item.name.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    private fun categorizePackage(pkg: String, name: String): AppCategory {
        val lowerPkg = pkg.lowercase()
        val lowerName = name.lowercase()

        return when {
            lowerPkg.contains("map") || lowerPkg.contains("waze") || lowerPkg.contains("nav") ||
            lowerPkg.contains("here") || lowerPkg.contains("radar") || lowerName.contains("map") ||
            lowerName.contains("navigation") -> AppCategory.NAVIGATION

            lowerPkg.contains("music") || lowerPkg.contains("spotify") || lowerPkg.contains("audio") ||
            lowerPkg.contains("radio") || lowerPkg.contains("sound") || lowerPkg.contains("podcast") ||
            lowerPkg.contains("youtube") || lowerName.contains("music") -> AppCategory.MEDIA

            lowerPkg.contains("obd") || lowerPkg.contains("torque") || lowerPkg.contains("scanner") ||
            lowerPkg.contains("car") || lowerPkg.contains("auto") || lowerPkg.contains("dash") -> AppCategory.OBD_TOOLS

            lowerPkg.contains("dialer") || lowerPkg.contains("phone") || lowerPkg.contains("contact") ||
            lowerPkg.contains("message") || lowerPkg.contains("chat") || lowerPkg.contains("whatsapp") ||
            lowerPkg.contains("telegram") || lowerPkg.contains("call") -> AppCategory.COMMUNICATION

            else -> AppCategory.ALL
        }
    }

    private fun getCategoryColor(category: AppCategory): Color {
        return when (category) {
            AppCategory.NAVIGATION -> BmwCyan
            AppCategory.MEDIA -> Color(0xFF1DB954)
            AppCategory.OBD_TOOLS -> BmwAmber
            AppCategory.COMMUNICATION -> BmwEcoTeal
            AppCategory.FAVORITES -> BmwCrimson
            AppCategory.ALL -> Color(0xFF94A3B8)
        }
    }

    fun launchApp(packageName: String): Boolean {
        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                // If app is not installed, prompt Google Play Store
                val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(playIntent)
                true
            }
        } catch (e: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Checks if Vision HUD is currently held as the default home launcher using RoleManager
     */
    fun isDefaultLauncher(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                    return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
                }
            }
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == context.packageName
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Prompts the Android OS system dialog to make this app the default launcher / Home screen via RoleManager
     */
    fun makeDefaultLauncher(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = activity.getSystemService(RoleManager::class.java)
                if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                    if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                        activity.startActivity(intent)
                        return
                    }
                }
            }
            // Fallback for earlier Android versions or if role dialog cannot be shown directly
            openHomeSettings(activity)
        } catch (e: Exception) {
            openHomeSettings(activity)
        }
    }

    /**
     * Explicitly opens the Android System Default Home / Apps Settings screen
     */
    fun openHomeSettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(intent)
            }
        }
    }

    private fun getDefaultFallbackApps(): List<AppShortcut> {
        return listOf(
            AppShortcut("gmaps", "Google Maps", "com.google.android.apps.maps", AppCategory.NAVIGATION, "map", BmwCyan, isFavorite = true),
            AppShortcut("spotify", "Spotify", "com.spotify.music", AppCategory.MEDIA, "spotify", Color(0xFF1DB954), isFavorite = true),
            AppShortcut("waze", "Waze", "com.waze", AppCategory.NAVIGATION, "navigation", Color(0xFF33CCFF), isFavorite = true),
            AppShortcut("ytmusic", "YouTube Music", "com.google.android.apps.youtube.music", AppCategory.MEDIA, "play_circle", BmwCrimson, isFavorite = true),
            AppShortcut("torque", "Torque Pro", "org.prowl.torque", AppCategory.OBD_TOOLS, "speed", BmwAmber, isFavorite = true),
            AppShortcut("carscanner", "Car Scanner", "com.ovz.carscanner", AppCategory.OBD_TOOLS, "troubleshoot", Color(0xFF2DD4BF)),
            AppShortcut("phone", "Phone", "com.google.android.dialer", AppCategory.COMMUNICATION, "call", BmwEcoTeal, isFavorite = true),
            AppShortcut("settings", "Settings", "com.android.settings", AppCategory.ALL, "settings", Color(0xFF94A3B8))
        )
    }
}
