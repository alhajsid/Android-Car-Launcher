package com.example.data

import android.app.Activity
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
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
     * Queries all real installed applications and activities on the device.
     * Preserves distinct activities within the same package (critical for Android Car Head Units
     * where CarPlay, Android Auto, Radio, BT, DSP are separate activities inside one system APK).
     */
    fun queryAllInstalledLaunchableApps(): List<AppShortcut> {
        val ownPackage = context.packageName
        val foundComponents = mutableSetOf<String>()
        val foundPackages = mutableSetOf<String>()
        val resultList = mutableListOf<AppShortcut>()

        // Strategy 1: Query ACTION_MAIN across LAUNCHER, CAR_MODE, CAR_DOCK, and INFO categories
        val categoriesToScan = listOf(
            Intent.CATEGORY_LAUNCHER,
            Intent.CATEGORY_CAR_MODE,
            Intent.CATEGORY_CAR_DOCK,
            Intent.CATEGORY_INFO
        )

        for (category in categoriesToScan) {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(category)
            }
            val resolveInfos = queryIntentActivitiesCompat(intent)
            resolveInfos.forEach { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@forEach
                val pkg = activityInfo.packageName ?: return@forEach
                val actName = activityInfo.name ?: return@forEach
                val componentKey = "$pkg/$actName"

                if (pkg != ownPackage && foundComponents.add(componentKey)) {
                    foundPackages.add(pkg)
                    try {
                        val name = resolveInfo.loadLabel(packageManager).toString().ifBlank {
                            activityInfo.loadLabel(packageManager).toString()
                        }
                        val icon = try {
                            resolveInfo.loadIcon(packageManager)
                        } catch (_: Exception) {
                            activityInfo.loadIcon(packageManager)
                        }
                        val detectedCategory = categorizePackage(pkg, name, actName)
                        val accent = getCategoryColor(detectedCategory)
                        val iconKey = resolveSpecialIconKey(pkg, name, actName)

                        resultList.add(
                            AppShortcut(
                                id = componentKey,
                                name = name,
                                packageName = pkg,
                                category = detectedCategory,
                                iconKey = iconKey,
                                accentColor = accent,
                                isPinnedToDock = pkg.contains("maps") || pkg.contains("spotify"),
                                isFavorite = detectedCategory != AppCategory.ALL,
                                isCustomInstalled = true,
                                iconDrawable = icon,
                                activityName = actName
                            )
                        )
                    } catch (_: Exception) {}
                }
            }
        }

        // Strategy 2: Scan getInstalledApplications for any missed launchable packages
        // (including built-in Android Auto on Android 10+ where CATEGORY_LAUNCHER is disabled)
        try {
            val installedApps = packageManager.getInstalledApplications(0)
            installedApps.forEach { appInfo ->
                val pkg = appInfo.packageName
                if (pkg != ownPackage && !foundPackages.contains(pkg)) {
                    val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                    val resolvedActivity = launchIntent?.component?.className
                        ?: findExportedEntryActivity(pkg)

                    val isProjectionPkg = isKnownProjectionPackage(pkg)
                    if ((launchIntent != null || isProjectionPkg) && resolvedActivity != null) {
                        val componentKey = "$pkg/$resolvedActivity"
                        if (foundComponents.add(componentKey)) {
                            foundPackages.add(pkg)
                            val name = appInfo.loadLabel(packageManager).toString()
                            val icon = try { appInfo.loadIcon(packageManager) } catch (_: Exception) { null }
                            val detectedCategory = categorizePackage(pkg, name, resolvedActivity)
                            val accent = getCategoryColor(detectedCategory)
                            val iconKey = resolveSpecialIconKey(pkg, name, resolvedActivity)

                            resultList.add(
                                AppShortcut(
                                    id = componentKey,
                                    name = name,
                                    packageName = pkg,
                                    category = detectedCategory,
                                    iconKey = iconKey,
                                    accentColor = accent,
                                    isPinnedToDock = pkg.contains("maps") || pkg.contains("spotify"),
                                    isFavorite = detectedCategory != AppCategory.ALL,
                                    isCustomInstalled = true,
                                    iconDrawable = icon,
                                    activityName = resolvedActivity
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Strategy 3: Ensure Apple CarPlay and Android Auto shortcuts are always present in the drawer
        val hasCarPlayTile = resultList.any {
            it.iconKey == "carplay" ||
            it.name.contains("carplay", ignoreCase = true) ||
            it.name.contains("car play", ignoreCase = true)
        }
        val hasAndroidAutoTile = resultList.any {
            it.iconKey == "android_auto" ||
            it.name.contains("android auto", ignoreCase = true)
        }

        if (!hasCarPlayTile) {
            resultList.add(
                AppShortcut(
                    id = "carplay",
                    name = "Apple CarPlay",
                    packageName = "carplay",
                    category = AppCategory.COMMUNICATION,
                    iconKey = "carplay",
                    accentColor = Color.White,
                    isFavorite = true,
                    isCustomInstalled = false
                )
            )
        }
        if (!hasAndroidAutoTile) {
            resultList.add(
                AppShortcut(
                    id = "android_auto",
                    name = "Android Auto",
                    packageName = "android_auto",
                    category = AppCategory.COMMUNICATION,
                    iconKey = "android_auto",
                    accentColor = Color(0xFF00E5FF),
                    isFavorite = true,
                    isCustomInstalled = false
                )
            )
        }

        // Strategy 4: If in an emulator with very few apps, add fallback presets
        val finalApps = if (resultList.size < 6) {
            val presets = getDefaultFallbackApps().filter { preset ->
                !foundPackages.contains(preset.packageName) &&
                resultList.none { it.id == preset.id || it.name.equals(preset.name, ignoreCase = true) }
            }
            resultList + presets
        } else {
            resultList
        }

        return finalApps.sortedWith(
            compareByDescending<AppShortcut> {
                it.iconKey == "carplay" || it.iconKey == "android_auto"
            }.thenBy { it.name.lowercase() }
        )
    }

    private fun queryIntentActivitiesCompat(intent: Intent): List<ResolveInfo> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    packageManager.queryIntentActivities(
                        intent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                    )
                } catch (_: Exception) {
                    packageManager.queryIntentActivities(
                        intent,
                        PackageManager.ResolveInfoFlags.of(0L)
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                try {
                    packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                } catch (_: Exception) {
                    packageManager.queryIntentActivities(intent, 0)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun resolveSpecialIconKey(pkg: String, name: String, actName: String): String {
        val lowerPkg = pkg.lowercase()
        val lowerName = name.lowercase()
        val lowerAct = actName.lowercase()
        return when {
            lowerName.contains("carplay") || lowerName.contains("car play") ||
            lowerAct.contains("carplay") || lowerPkg.contains("carplay") -> "carplay"

            lowerName.contains("android auto") || lowerName.contains("androidauto") ||
            lowerAct.contains("androidauto") || lowerPkg == "com.google.android.projection.gearhead" ||
            lowerPkg == "com.google.android.apps.auto" -> "android_auto"

            else -> "installed"
        }
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

    private fun categorizePackage(pkg: String, name: String, actName: String = ""): AppCategory {
        val lowerPkg = pkg.lowercase()
        val lowerName = name.lowercase()
        val lowerAct = actName.lowercase()

        return when {
            lowerPkg.contains("map") || lowerPkg.contains("waze") || lowerPkg.contains("nav") ||
            lowerPkg.contains("here") || lowerPkg.contains("radar") || lowerName.contains("map") ||
            lowerName.contains("navigation") -> AppCategory.NAVIGATION

            lowerPkg.contains("music") || lowerPkg.contains("spotify") || lowerPkg.contains("audio") ||
            lowerPkg.contains("radio") || lowerPkg.contains("sound") || lowerPkg.contains("podcast") ||
            lowerPkg.contains("youtube") || lowerName.contains("music") || lowerName.contains("radio") -> AppCategory.MEDIA

            lowerPkg.contains("obd") || lowerPkg.contains("torque") || lowerPkg.contains("scanner") ||
            lowerPkg.contains("diagnostics") || lowerName.contains("obd") -> AppCategory.OBD_TOOLS

            isKnownProjectionPackage(lowerPkg) ||
            lowerName.contains("carplay") || lowerName.contains("car play") ||
            lowerName.contains("android auto") || lowerName.contains("zlink") ||
            lowerName.contains("tlink") || lowerName.contains("carlink") ||
            lowerName.contains("speedplay") || lowerName.contains("autokit") ||
            lowerName.contains("headunit") || lowerName.contains("phonelink") ||
            lowerAct.contains("carplay") || lowerAct.contains("androidauto") ||
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

    /**
     * Launches an AppShortcut directly using its exact ComponentName (packageName + activityName)
     * just like standard Android home launchers, without ever redirecting to the Play Store.
     */
    fun launchShortcut(shortcut: AppShortcut, customCarPlay: String = "", customAndroidAuto: String = ""): Boolean {
        if (shortcut.id == "carplay" || shortcut.packageName == "carplay") {
            return launchCarPlay(customCarPlay)
        }
        if (shortcut.id == "android_auto" || shortcut.packageName == "android_auto") {
            return launchAndroidAuto(customAndroidAuto)
        }
        if (!shortcut.activityName.isNullOrBlank()) {
            if (startComponentDirectly(shortcut.packageName, shortcut.activityName)) {
                return true
            }
        }
        return launchApp(shortcut.packageName, shortcut.activityName)
    }

    /**
     * Directly launches an installed package or "package/activity" string on the device.
     * Never redirects the user to Google Play Store.
     */
    fun launchApp(packageOrComponent: String, explicitActivityName: String? = null): Boolean {
        if (packageOrComponent.isBlank()) return false

        if (packageOrComponent == "carplay") return launchCarPlay()
        if (packageOrComponent == "android_auto") return launchAndroidAuto()

        // 1. Check if formatted as "packageName/activityName"
        if (packageOrComponent.contains("/")) {
            val parts = packageOrComponent.split("/", limit = 2)
            val pkg = parts[0]
            val act = parts[1]
            if (act.isNotBlank() && startComponentDirectly(pkg, act)) {
                return true
            }
            return launchPackageDirectly(pkg)
        }

        // 2. If an explicit activityName was supplied, launch that exact ComponentName first
        if (!explicitActivityName.isNullOrBlank()) {
            if (startComponentDirectly(packageOrComponent, explicitActivityName)) {
                return true
            }
        }

        // 3. Check if we already discovered a specific activityName for this package
        val cachedMatch = _installedApps.value.firstOrNull {
            it.packageName == packageOrComponent && !it.activityName.isNullOrBlank()
        }
        if (cachedMatch?.activityName != null) {
            if (startComponentDirectly(cachedMatch.packageName, cachedMatch.activityName)) {
                return true
            }
        }

        // 4. Deep package launch (handles standard apps, system apps, and Android Auto Gearhead)
        return launchPackageDirectly(packageOrComponent)
    }

    private fun startComponentDirectly(packageName: String, activityName: String): Boolean {
        val fullActivityName = if (activityName.startsWith(".")) {
            "$packageName$activityName"
        } else {
            activityName
        }
        val component = ComponentName(packageName, fullActivityName)

        // Attempt 1: Standard Launcher MAIN + LAUNCHER component intent (how AOSP Launcher3 starts apps)
        try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                this.component = component
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(launcherIntent)
            return true
        } catch (_: Exception) {}

        // Attempt 2: Direct explicit ComponentName intent without category restriction
        try {
            val explicitIntent = Intent().apply {
                this.component = component
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(explicitIntent)
            return true
        } catch (_: Exception) {}

        // Attempt 3: ACTION_MAIN without category
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                this.component = component
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(mainIntent)
            return true
        } catch (_: Exception) {}

        return false
    }

    private fun launchPackageDirectly(packageName: String): Boolean {
        // Special deep handling if the package is official Google Android Auto
        if (packageName == "com.google.android.projection.gearhead" || packageName == "com.google.android.apps.auto") {
            if (launchOfficialAndroidAuto(packageName)) return true
        }

        // Step 1: Standard getLaunchIntentForPackage
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(launchIntent)
                return true
            }
        } catch (_: Exception) {}

        // Step 2: Leanback / TV / Car launch intent
        try {
            val leanbackIntent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            if (leanbackIntent != null) {
                leanbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(leanbackIntent)
                return true
            }
        } catch (_: Exception) {}

        // Step 3: Query ACTION_MAIN + CATEGORY_LAUNCHER with MATCH_ALL for this package
        val launcherMatches = queryIntentActivitiesCompat(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
        )
        for (info in launcherMatches) {
            val actName = info.activityInfo?.name ?: continue
            if (startComponentDirectly(packageName, actName)) return true
        }

        // Step 4: Query any ACTION_MAIN activity in this package (DEFAULT, CAR_MODE, CAR_DOCK, INFO)
        val mainMatches = queryIntentActivitiesCompat(
            Intent(Intent.ACTION_MAIN).setPackage(packageName)
        )
        for (info in mainMatches) {
            val actName = info.activityInfo?.name ?: continue
            if (startComponentDirectly(packageName, actName)) return true
        }

        // Step 5: Query ACTION_APPLICATION_PREFERENCES for this package
        val prefMatches = queryIntentActivitiesCompat(
            Intent(Intent.ACTION_APPLICATION_PREFERENCES).setPackage(packageName)
        )
        for (info in prefMatches) {
            val actName = info.activityInfo?.name ?: continue
            if (startComponentDirectly(packageName, actName)) return true
        }

        // Step 6: Inspect PackageInfo.activities for any exported activity
        val exportedActivity = findExportedEntryActivity(packageName)
        if (exportedActivity != null && startComponentDirectly(packageName, exportedActivity)) {
            return true
        }

        return false
    }

    /**
     * Launches official Google Android Auto (com.google.android.projection.gearhead)
     * even on Android 10-15+ where its CATEGORY_LAUNCHER icon is hidden by the OS.
     */
    private fun launchOfficialAndroidAuto(pkg: String = "com.google.android.projection.gearhead"): Boolean {
        // 1. Check if standard launcher activity is enabled
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        } catch (_: Exception) {}

        // 2. Try ACTION_MAIN with CAR_MODE / CAR_DOCK /LAUNCHER
        for (cat in listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_CAR_MODE, Intent.CATEGORY_CAR_DOCK, Intent.CATEGORY_INFO)) {
            val matches = queryIntentActivitiesCompat(Intent(Intent.ACTION_MAIN).addCategory(cat).setPackage(pkg))
            for (info in matches) {
                val act = info.activityInfo?.name ?: continue
                if (startComponentDirectly(pkg, act)) return true
            }
        }

        // 3. Try ACTION_APPLICATION_PREFERENCES (official entry point for Android Auto on Android 10-15)
        try {
            val prefIntent = Intent(Intent.ACTION_APPLICATION_PREFERENCES).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val matches = queryIntentActivitiesCompat(prefIntent)
            if (matches.isNotEmpty()) {
                val act = matches.first().activityInfo?.name
                if (act != null && startComponentDirectly(pkg, act)) return true
            }
            context.startActivity(prefIntent)
            return true
        } catch (_: Exception) {}

        // 4. Try known exported Android Auto companion/settings/projection activities across versions
        val knownGearheadActivities = listOf(
            "com.google.android.projection.gearhead.companion.settings.DefaultSettingsActivity",
            "com.google.android.apps.auto.components.settings.ui.CarSettingsActivity",
            "com.google.android.projection.gearhead.companion.settings.Material3SettingsActivity",
            "com.google.android.projection.gearhead.vanagon.VnLaunchPadActivity",
            "com.google.android.apps.auto.carservice.gmscorecompat.FirstActivityImpl"
        )
        for (act in knownGearheadActivities) {
            if (startComponentDirectly(pkg, act)) return true
        }

        // 5. Try any ACTION_MAIN or exported activity inside the package
        val mainMatches = queryIntentActivitiesCompat(Intent(Intent.ACTION_MAIN).setPackage(pkg))
        for (info in mainMatches) {
            val act = info.activityInfo?.name ?: continue
            if (startComponentDirectly(pkg, act)) return true
        }

        val exportedAct = findExportedEntryActivity(pkg)
        if (exportedAct != null && startComponentDirectly(pkg, exportedAct)) {
            return true
        }

        return false
    }

    private fun findExportedEntryActivity(packageName: String): String? {
        return try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            }
            val activities = pkgInfo.activities ?: return null
            val exported = activities.filter { it.exported && it.enabled }
            // Prefer main/launch/settings/carplay/auto activities
            val preferred = exported.firstOrNull { act ->
                val n = act.name.lowercase()
                n.contains("main") || n.contains("launch") || n.contains("splash") ||
                n.contains("carplay") || n.contains("auto") || n.contains("zlink") ||
                n.contains("tlink") || n.contains("carlink") || n.contains("settings")
            } ?: exported.firstOrNull()
            preferred?.name
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Multi-stage discovery & launch for Apple CarPlay on Android Car Head Units.
     * Checks user-bound component -> discovered launcher activities by label/class/package ->
     * 40+ known head-unit CarPlay packages (ZLink, TLink, SpeedPlay, CarLink, AutoKit, SYU, Topway, etc.).
     */
    fun launchCarPlay(customComponent: String = ""): Boolean {
        // Stage 1: User-configured CarPlay app/activity
        if (customComponent.isNotBlank() && launchApp(customComponent)) {
            return true
        }

        val allApps = if (_installedApps.value.isNotEmpty()) {
            _installedApps.value
        } else {
            queryAllInstalledLaunchableApps()
        }.filter { it.isCustomInstalled }

        // Stage 2A: Any installed activity whose visible label or class name explicitly says CarPlay
        val explicitCarPlayMatch = allApps.firstOrNull { app ->
            val label = app.name.lowercase()
            val act = (app.activityName ?: "").lowercase()
            val pkg = app.packageName.lowercase()
            label.contains("carplay") || label.contains("car play") ||
            act.contains("carplay") || pkg.contains("carplay")
        }
        if (explicitCarPlayMatch != null && launchShortcut(explicitCarPlayMatch)) {
            return true
        }

        // Stage 2B: Any installed activity matching in-dash CarPlay/projection link apps
        val linkAppKeywords = listOf(
            "zlink", "tlink", "speedplay", "carlink", "autokit", "carletter",
            "phonemirror", "cp/aa", "phonelink", "phone link", "carbit",
            "easyconn", "s-play", "multiplay", "iphonelink", "hicar"
        )
        val linkActivityMatch = allApps.firstOrNull { app ->
            val label = app.name.lowercase()
            val act = (app.activityName ?: "").lowercase()
            val pkg = app.packageName.lowercase()
            linkAppKeywords.any { kw -> label.contains(kw) || act.contains(kw) || pkg.contains(kw) }
        }
        if (linkActivityMatch != null && launchShortcut(linkActivityMatch)) {
            return true
        }

        // Stage 3: Check known CarPlay / Head Unit Link packages directly
        for (pkg in KNOWN_CARPLAY_PACKAGES) {
            if (launchPackageDirectly(pkg)) {
                return true
            }
        }

        // Stage 4: Deep scan all installed packages on the device for any CarPlay/Link keyword
        try {
            val installed = packageManager.getInstalledApplications(0)
            for (appInfo in installed) {
                val pkg = appInfo.packageName.lowercase()
                val label = try { appInfo.loadLabel(packageManager).toString().lowercase() } catch (_: Exception) { "" }
                if (pkg != context.packageName.lowercase()) {
                    val isMatch = pkg.contains("carplay") || label.contains("carplay") ||
                        linkAppKeywords.any { kw -> pkg.contains(kw) || label.contains(kw) }
                    if (isMatch && launchPackageDirectly(appInfo.packageName)) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * Multi-stage discovery & launch for Android Auto.
     * Checks user-bound component -> discovered launcher activities ("Android Auto", Headunit Reloaded,
     * ZLink, TLink, SpeedPlay, CarLink, AutoKit) -> built-in Google Android Auto (Gearhead).
     */
    fun launchAndroidAuto(customComponent: String = ""): Boolean {
        // Stage 1: User-configured Android Auto app/activity
        if (customComponent.isNotBlank() && launchApp(customComponent)) {
            return true
        }

        val allApps = if (_installedApps.value.isNotEmpty()) {
            _installedApps.value
        } else {
            queryAllInstalledLaunchableApps()
        }.filter { it.isCustomInstalled }

        // Stage 2A: Any installed launcher activity with "Android Auto" or "Headunit Reloaded" in its label or class name
        // (Prefer head-unit receiver activities first before Gearhead settings if multiple exist)
        val dedicatedAutoMatch = allApps.firstOrNull { app ->
            val label = app.name.lowercase()
            val act = (app.activityName ?: "").lowercase()
            val pkg = app.packageName.lowercase()
            (pkg != "com.google.android.projection.gearhead") && (
                label.contains("android auto") || label.contains("androidauto") ||
                label.contains("headunit reloaded") || act.contains("androidauto") ||
                act.contains("android_auto") || pkg.contains("androidauto") || pkg == "gb.xxy.hr"
            )
        }
        if (dedicatedAutoMatch != null && launchShortcut(dedicatedAutoMatch)) {
            return true
        }

        // Stage 2B: Any installed head-unit projection app (ZLink, TLink, SpeedPlay, CarLink, AutoKit, etc.)
        val linkAppKeywords = listOf(
            "zlink", "tlink", "speedplay", "carlink", "autokit", "carletter",
            "phonemirror", "cp/aa", "phonelink", "phone link", "carbit",
            "easyconn", "s-play", "multiplay"
        )
        val linkActivityMatch = allApps.firstOrNull { app ->
            val label = app.name.lowercase()
            val act = (app.activityName ?: "").lowercase()
            val pkg = app.packageName.lowercase()
            linkAppKeywords.any { kw -> label.contains(kw) || act.contains(kw) || pkg.contains(kw) }
        }
        if (linkActivityMatch != null && launchShortcut(linkActivityMatch)) {
            return true
        }

        // Stage 3: Try known head-unit Android Auto receiver packages
        for (pkg in KNOWN_HEADUNIT_AUTO_PACKAGES) {
            if (launchPackageDirectly(pkg)) {
                return true
            }
        }

        // Stage 4: Launch official Google Android Auto (com.google.android.projection.gearhead / com.google.android.apps.auto)
        if (launchOfficialAndroidAuto("com.google.android.projection.gearhead")) {
            return true
        }
        if (launchOfficialAndroidAuto("com.google.android.apps.auto")) {
            return true
        }

        // Stage 5: Deep scan all installed packages for any Android Auto or Link keyword
        try {
            val installed = packageManager.getInstalledApplications(0)
            for (appInfo in installed) {
                val pkg = appInfo.packageName.lowercase()
                val label = try { appInfo.loadLabel(packageManager).toString().lowercase() } catch (_: Exception) { "" }
                if (pkg != context.packageName.lowercase()) {
                    val isMatch = pkg.contains("gearhead") || pkg.contains("androidauto") ||
                        label.contains("android auto") ||
                        linkAppKeywords.any { kw -> pkg.contains(kw) || label.contains(kw) }
                    if (isMatch && launchPackageDirectly(appInfo.packageName)) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    private fun isKnownProjectionPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return KNOWN_CARPLAY_PACKAGES.any { it.equals(lower, ignoreCase = true) } ||
            KNOWN_HEADUNIT_AUTO_PACKAGES.any { it.equals(lower, ignoreCase = true) } ||
            lower == "com.google.android.projection.gearhead" ||
            lower == "com.google.android.apps.auto" ||
            lower.contains("zlink") || lower.contains("tlink") ||
            lower.contains("speedplay") || lower.contains("carlink") ||
            lower.contains("carplay") || lower.contains("phonemirror") ||
            lower.contains("autokit") || lower.contains("carletter")
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
            AppShortcut("carplay", "Apple CarPlay", "carplay", AppCategory.COMMUNICATION, "carplay", Color.White, isFavorite = true, isCustomInstalled = false),
            AppShortcut("android_auto", "Android Auto", "android_auto", AppCategory.COMMUNICATION, "android_auto", Color(0xFF00E5FF), isFavorite = true, isCustomInstalled = false),
            AppShortcut("gmaps", "Google Maps", "com.google.android.apps.maps", AppCategory.NAVIGATION, "map", BmwCyan, isFavorite = true, isCustomInstalled = false),
            AppShortcut("spotify", "Spotify", "com.spotify.music", AppCategory.MEDIA, "spotify", Color(0xFF1DB954), isFavorite = true, isCustomInstalled = false),
            AppShortcut("waze", "Waze", "com.waze", AppCategory.NAVIGATION, "navigation", Color(0xFF33CCFF), isFavorite = true, isCustomInstalled = false),
            AppShortcut("ytmusic", "YouTube Music", "com.google.android.apps.youtube.music", AppCategory.MEDIA, "play_circle", BmwCrimson, isFavorite = true, isCustomInstalled = false),
            AppShortcut("torque", "Torque Pro", "org.prowl.torque", AppCategory.OBD_TOOLS, "speed", BmwAmber, isFavorite = true, isCustomInstalled = false),
            AppShortcut("carscanner", "Car Scanner", "com.ovz.carscanner", AppCategory.OBD_TOOLS, "troubleshoot", Color(0xFF2DD4BF), isCustomInstalled = false),
            AppShortcut("phone", "Phone", "com.google.android.dialer", AppCategory.COMMUNICATION, "call", BmwEcoTeal, isFavorite = true, isCustomInstalled = false),
            AppShortcut("settings", "Settings", "com.android.settings", AppCategory.ALL, "settings", Color(0xFF94A3B8), isCustomInstalled = false)
        )
    }

    companion object {
        val KNOWN_CARPLAY_PACKAGES = listOf(
            "com.zjinnova.zlink5",
            "com.zjinnova.zlink",
            "com.zjinnova.zlink6",
            "com.zjinnova.zlink.auto",
            "cn.manstep.phonemirrorBox",
            "cn.manstep.phonemirror",
            "cn.manstep.phonemirrorBox.wear",
            "com.carlinkit.autokit",
            "com.tlink.auto",
            "com.syu.tlink",
            "com.syu.carlink",
            "com.topway.tlink",
            "com.ts.tlink",
            "com.suding.speedplay",
            "com.carlink",
            "com.carlink.ap",
            "com.carlink.carlink",
            "com.jancar.carlink",
            "com.yhp.carlink",
            "com.hp.carlink",
            "com.ankai.carlink",
            "net.easyconn",
            "net.easyconn.carman",
            "com.carletter.carletter",
            "com.carletter",
            "com.xyauto.link",
            "com.autochips.carplay",
            "com.rockchip.carplay",
            "com.wits.pemp.carplay",
            "com.wits.ksw.carplay",
            "com.banma.carplay",
            "com.apple.carplay"
        )

        val KNOWN_HEADUNIT_AUTO_PACKAGES = listOf(
            "gb.xxy.hr",
            "gb.xxy.hr.trial",
            "com.autochips.androidauto",
            "com.zjinnova.zlink5",
            "com.zjinnova.zlink",
            "com.zjinnova.zlink6",
            "com.zjinnova.zlink.auto",
            "com.tlink.auto",
            "com.syu.tlink",
            "com.syu.carlink",
            "com.topway.tlink",
            "com.ts.tlink",
            "com.suding.speedplay",
            "com.carlink",
            "com.carlink.ap",
            "com.carlink.carlink",
            "com.jancar.carlink",
            "com.yhp.carlink",
            "com.hp.carlink",
            "com.ankai.carlink",
            "cn.manstep.phonemirrorBox",
            "cn.manstep.phonemirror",
            "com.carlinkit.autokit",
            "com.carletter.carletter",
            "com.carletter",
            "net.easyconn",
            "net.easyconn.carman",
            "com.xyauto.link"
        )
    }
}
