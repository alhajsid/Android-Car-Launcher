package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.model.AppCategory
import com.example.model.AppShortcut
import com.example.ui.theme.BmwCyan
import com.example.ui.theme.BmwOptimalGreen
import com.example.ui.theme.CockpitSurfaceLowest
import com.example.ui.theme.GlassCardBorder
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondaryGrey

enum class ProjectionType {
    CARPLAY,
    ANDROID_AUTO
}

@Composable
fun PhoneProjectionDialog(
    type: ProjectionType,
    installedApps: List<AppShortcut> = emptyList(),
    currentBoundComponent: String = "",
    onSelectAndLaunchApp: (AppShortcut) -> Unit = {},
    onDismiss: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onOpenConnectionSettings: () -> Unit = {}
) {
    val isCarPlay = type == ProjectionType.CARPLAY
    val title = if (isCarPlay) "Apple CarPlay" else "Android Auto"
    val accentColor = if (isCarPlay) Color.White else BmwCyan
    val iconRes = if (isCarPlay) R.drawable.ic_carplay else R.drawable.ic_android_auto

    val selectableApps = remember(installedApps) {
        installedApps
            .filter { it.isCustomInstalled && it.id != "carplay" && it.id != "android_auto" }
            .sortedWith(
                compareByDescending<AppShortcut> {
                    it.category == AppCategory.COMMUNICATION ||
                    it.name.contains("link", ignoreCase = true) ||
                    it.name.contains("auto", ignoreCase = true) ||
                    it.name.contains("car", ignoreCase = true) ||
                    it.name.contains("play", ignoreCase = true)
                }.thenBy { it.name.lowercase() }
            )
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .background(CockpitSurfaceLowest)
                .border(1.5.dp, GlassCardBorder, RoundedCornerShape(24.dp))
                .padding(20.dp)
                .testTag(if (isCarPlay) "dialog_carplay" else "dialog_android_auto")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x26FFFFFF))
                                .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = iconRes),
                                contentDescription = title,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = title.uppercase(),
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                letterSpacing = 1.sp,
                                color = TextPrimaryWhite
                            )
                            Text(
                                text = "Select Installed Head-Unit App to Open Directly",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                                color = TextSecondaryGrey
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondaryGrey,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectableApps.isNotEmpty()) {
                    Text(
                        text = "Tap your head unit's $title app below to open it immediately and save it for one-tap launch:",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        color = TextSecondaryGrey,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp)),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(selectableApps, key = { it.id }) { app ->
                            val componentKey = if (!app.activityName.isNullOrBlank()) {
                                "${app.packageName}/${app.activityName}"
                            } else {
                                app.packageName
                            }
                            val isSelected = currentBoundComponent == componentKey || currentBoundComponent == app.packageName

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0x334CC2FF) else Color(0x1AFFFFFF))
                                    .border(
                                        1.dp,
                                        if (isSelected) BmwCyan else Color(0x1FFFFFFF),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onSelectAndLaunchApp(app) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsCar,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = app.name,
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = TextPrimaryWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = app.packageName,
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 10.sp,
                                            color = TextSecondaryGrey,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Launch,
                                    contentDescription = "Launch",
                                    tint = if (isSelected) BmwOptimalGreen else accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x1AFFFFFF))
                            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Connect your phone via USB or Bluetooth/Wi-Fi, or open system connection settings below.",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            color = TextSecondaryGrey,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Action Buttons (Bluetooth & System Connection Settings - NEVER Play Store)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenBluetooth,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x334CC2FF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = BmwCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BLUETOOTH",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = BmwCyan
                        )
                    }

                    Button(
                        onClick = onOpenConnectionSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = if (isCarPlay) Color.White else BmwCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = TextDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DEVICE LINK",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = TextDark
                        )
                    }
                }
            }
        }
    }
}
