package com.example.ui.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FmdBad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppCategory
import com.example.model.AppShortcut
import com.example.model.TelemetryData
import com.example.model.UserSettings
import com.example.ui.theme.BmwCyan
import com.example.ui.theme.BmwOptimalGreen
import com.example.ui.theme.CockpitSurfaceLowest
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondaryGrey
import kotlinx.coroutines.launch

/**
 * High-performance full-screen automotive app launcher drawer.
 * Redesigned to maximize screen real estate:
 * - 90%+ of the viewport is allocated to the responsive app grid.
 * - Single streamlined top bar with instant Cockpit return, inline search, glanceable speed, and default launcher control.
 * - Horizontal category scroll strip without redundant bottom dock eating up space.
 */
@Composable
fun AllAppsScreen(
    telemetry: TelemetryData,
    settings: UserSettings = UserSettings(),
    apps: List<AppShortcut>,
    isDefaultLauncher: Boolean = false,
    onLaunchApp: (String) -> Unit,
    onToggleFavorite: (String) -> Unit = {},
    onMakeDefaultLauncher: () -> Unit,
    onBackToCockpit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val accentColor = telemetry.driveMode.primaryColor

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(AppCategory.ALL) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Intercept hardware/system back button to return to Cockpit
    BackHandler {
        onBackToCockpit()
    }

    val filteredApps = remember(apps, selectedCategory, searchQuery, settings.favoritePackages, settings.hiddenPackages) {
        apps.filter { app ->
            if (settings.hiddenPackages.contains(app.packageName)) return@filter false
            val isFav = settings.favoritePackages.contains(app.packageName)
            val matchCat = when (selectedCategory) {
                AppCategory.ALL -> true
                AppCategory.FAVORITES -> isFav
                else -> app.category == selectedCategory
            }
            val matchQuery = searchQuery.isBlank() || app.name.contains(searchQuery, ignoreCase = true)
            matchCat && matchQuery
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CockpitSurfaceLowest)
            .testTag("all_apps_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Streamlined Automotive Header Bar (Single compact line, ~46dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Quick Return to Cockpit Dashboard
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x26FFFFFF))
                        .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .clickable { onBackToCockpit() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("back_to_cockpit_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Return to Cockpit",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "COCKPIT",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        color = TextPrimaryWhite
                    )
                }

                // Center: Compact In-Car Search Capsule
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x3310151F))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextSecondaryGrey,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search ${apps.size} installed apps...",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = TextPrimaryWhite,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(accentColor),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = TextSecondaryGrey,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Right: Minimal Safety Telemetry Glance & Default Launcher Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick Glance Speed + CAN Status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22FFFFFF))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${telemetry.speed}",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = TextPrimaryWhite
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "KM/H",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = accentColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (telemetry.isObdConnected) BmwOptimalGreen else Color(0xFF666666))
                        )
                    }

                    // Default Launcher Toggle Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDefaultLauncher) Color(0x3310B981) else Color(0x264CC2FF))
                            .border(
                                1.dp,
                                if (isDefaultLauncher) BmwOptimalGreen.copy(alpha = 0.5f) else BmwCyan.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onMakeDefaultLauncher() }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("launcher_status_pill")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = if (isDefaultLauncher) BmwOptimalGreen else BmwCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            if (!isLandscape) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isDefaultLauncher) "HOME" else "SET HOME",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = if (isDefaultLauncher) BmwOptimalGreen else BmwCyan
                                )
                            }
                        }
                    }
                }
            }

            // Compact Horizontal Category Scroll Strip (height ~34dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppCategory.values().forEach { category ->
                    val isSelected = (category == selectedCategory)
                    val count = if (category == AppCategory.ALL) apps.size else apps.count {
                        if (category == AppCategory.FAVORITES) it.isFavorite else it.category == category
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) accentColor else Color(0x1AFFFFFF))
                            .border(
                                1.dp,
                                if (isSelected) accentColor else Color(0x1AFFFFFF),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedCategory = category }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "${category.title} ($count)",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp,
                            color = if (isSelected) {
                                if (accentColor == BmwCyan) TextDark else TextPrimaryWhite
                            } else {
                                TextSecondaryGrey
                            }
                        )
                    }
                }
            }

            // Expansive Full-Screen In-Car App Grid (occupies entire remaining height & width)
            val minTileSize = if (isLandscape) 96.dp else 84.dp
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = minTileSize),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("apps_grid"),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 8.dp,
                    bottom = 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filteredApps, key = { it.id }) { app ->
                    val isFav = settings.favoritePackages.contains(app.packageName)
                    AppLauncherTile(
                        app = app,
                        isFavorite = isFav,
                        onClick = {
                            onLaunchApp(app.packageName)
                            scope.launch {
                                snackbarHostState.showSnackbar("Opening ${app.name}...")
                            }
                        },
                        onToggleFavorite = {
                            onToggleFavorite(app.packageName)
                        }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        )
    }
}

@Composable
private fun AppLauncherTile(
    app: AppShortcut,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // High Touch Target Glassmorphism Tile (comfortable 68dp automotive touch size)
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            app.accentColor.copy(alpha = 0.28f),
                            Color(0xFF0F172A).copy(alpha = 0.90f)
                        )
                    )
                )
                .border(1.5.dp, app.accentColor.copy(alpha = 0.55f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                app.iconKey == "carplay" || app.id == "carplay" || app.packageName.contains("zlink") || app.packageName.contains("carplay") -> {
                    Image(
                        painter = painterResource(R.drawable.ic_carplay),
                        contentDescription = app.name,
                        modifier = Modifier.size(46.dp)
                    )
                }
                app.iconKey == "android_auto" || app.id == "android_auto" || app.packageName.contains("gearhead") -> {
                    Image(
                        painter = painterResource(R.drawable.ic_android_auto),
                        contentDescription = app.name,
                        modifier = Modifier.size(46.dp)
                    )
                }
                app.iconDrawable != null -> {
                    AppRealDrawableIcon(
                        drawable = app.iconDrawable,
                        modifier = Modifier.size(44.dp)
                    )
                }
                else -> {
                    val iconVector = getAppIcon(app.iconKey)
                    Icon(
                        imageVector = iconVector,
                        contentDescription = app.name,
                        tint = app.accentColor,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            // Favorite Pin Star Badge (Accessible touch target)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isFavorite) Color(0xFFFFB800) else Color(0x66000000))
                    .clickable { onToggleFavorite() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Toggle Favorite",
                    tint = if (isFavorite) Color.Black else Color(0x80FFFFFF),
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = app.name,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            color = TextPrimaryWhite,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun AppRealDrawableIcon(
    drawable: Drawable,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(drawable) {
        try {
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Icon(
            imageVector = Icons.Default.DirectionsCar,
            contentDescription = null,
            tint = BmwCyan,
            modifier = modifier
        )
    }
}

private fun getAppIcon(key: String): ImageVector {
    return when (key) {
        "map" -> Icons.Default.Map
        "spotify" -> Icons.Default.MusicNote
        "navigation" -> Icons.Default.Navigation
        "play_circle" -> Icons.Default.PlayCircle
        "speed" -> Icons.Default.Speed
        "troubleshoot" -> Icons.Default.Troubleshoot
        "radio" -> Icons.Default.Radio
        "shop" -> Icons.Default.Shop
        "chat" -> Icons.Default.Chat
        "auto_stories" -> Icons.Default.AutoStories
        "assistant" -> Icons.Default.Assistant
        "fmd_bad" -> Icons.Default.FmdBad
        "call" -> Icons.Default.Call
        "settings" -> Icons.Default.Settings
        else -> Icons.Default.DirectionsCar
    }
}
