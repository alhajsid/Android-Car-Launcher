package com.example.ui.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppCategory
import com.example.model.AppShortcut
import com.example.model.TelemetryData
import com.example.model.UserSettings
import com.example.ui.components.CockpitDock
import com.example.ui.theme.BmwCyan
import com.example.ui.theme.BmwOptimalGreen
import com.example.ui.theme.CockpitSurfaceLowest
import com.example.ui.theme.GlassCardBorder
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondaryGrey
import kotlinx.coroutines.launch

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
        Column(modifier = Modifier.fillMaxSize()) {
            // Persistent Top Telemetry Glance Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xE605070A))
                    .border(1.dp, Color(0x1AFFFFFF))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exit Cockpit Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x26FFFFFF))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                        .clickable { onBackToCockpit() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
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
                        letterSpacing = 1.sp,
                        color = TextPrimaryWhite
                    )
                }

                // Make Default Launcher Button
                Button(
                    onClick = onMakeDefaultLauncher,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDefaultLauncher) Color(0x3310B981) else Color(0x334CC2FF)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = if (isDefaultLauncher) BmwOptimalGreen else BmwCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDefaultLauncher) "HOME: ACTIVE DEFAULT" else "SET DEFAULT HOME LAUNCHER",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (isDefaultLauncher) BmwOptimalGreen else BmwCyan
                    )
                }

                // Glanceable Driving Telemetry (Speed & Gear)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x1FFFFFFF))
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${telemetry.speed}",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = TextPrimaryWhite
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "KM/H",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = accentColor,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (telemetry.isObdConnected) accentColor else Color(0x33FFFFFF))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (telemetry.isObdConnected) telemetry.currentGear else "-",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (telemetry.isObdConnected && accentColor == BmwCyan) TextDark else TextPrimaryWhite
                        )
                    }

                    if (telemetry.isObdConnected) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(BmwOptimalGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CAN CONNECTED",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = TextPrimaryWhite
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF666666))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "OBD STANDBY",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = TextSecondaryGrey
                            )
                        }
                    }
                }
            }

            // Search Bar & Filter Chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Wide In-Car Safe Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search ${apps.size} installed apps or voice command...",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 14.sp,
                            color = TextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextSecondaryGrey
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = TextSecondaryGrey
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = GlassCardBorder,
                        focusedTextColor = TextPrimaryWhite,
                        unfocusedTextColor = TextPrimaryWhite,
                        focusedContainerColor = Color(0x6610151F),
                        unfocusedContainerColor = Color(0x4D10151F)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppCategory.values().forEach { category ->
                        val isSelected = (category == selectedCategory)
                        val count = if (category == AppCategory.ALL) apps.size else apps.count {
                            if (category == AppCategory.FAVORITES) it.isFavorite else it.category == category
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) accentColor else Color(0x22FFFFFF))
                                .border(
                                    1.dp,
                                    if (isSelected) accentColor else Color(0x1AFFFFFF),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "${category.title} ($count)",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
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
            }

            // Grid of Large In-Car App Tiles
            val columns = if (isLandscape) 6 else 3
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(filteredApps, key = { it.id }) { app ->
                    val isFav = settings.favoritePackages.contains(app.packageName)
                    AppLauncherTile(
                        app = app,
                        isFavorite = isFav,
                        onClick = {
                            val launched = onLaunchApp(app.packageName)
                            scope.launch {
                                snackbarHostState.showSnackbar("Launching ${app.name}...")
                            }
                        },
                        onToggleFavorite = {
                            onToggleFavorite(app.packageName)
                        }
                    )
                }
            }

            // Floating Navigation Dock (Persistent)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                CockpitDock(
                    accentColor = accentColor,
                    isAllAppsActive = true,
                    onNavigate = { dest ->
                        if (dest == "all_apps") onBackToCockpit() else onBackToCockpit()
                    }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
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
            .width(110.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // High Touch Target Glassmorphism Tile (min 64dp)
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            app.accentColor.copy(alpha = 0.25f),
                            Color(0xFF0F172A).copy(alpha = 0.85f)
                        )
                    )
                )
                .border(1.dp, app.accentColor.copy(alpha = 0.5f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (app.iconDrawable != null) {
                AppRealDrawableIcon(
                    drawable = app.iconDrawable,
                    modifier = Modifier.size(46.dp)
                )
            } else {
                val iconVector = getAppIcon(app.iconKey)
                Icon(
                    imageVector = iconVector,
                    contentDescription = app.name,
                    tint = app.accentColor,
                    modifier = Modifier.size(34.dp)
                )
            }

            // Favorite Pin Star Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isFavorite) Color(0xFFFFB800) else Color(0x66000000))
                    .clickable { onToggleFavorite() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Pin Favorite",
                    tint = if (isFavorite) Color.Black else Color(0x80FFFFFF),
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = app.name,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = TextPrimaryWhite,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
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
