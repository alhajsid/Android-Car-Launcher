package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.BmwCyan
import com.example.ui.theme.GlassCardBorder
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondaryGrey

@Composable
fun CockpitDock(
    accentColor: Color,
    isAllAppsActive: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xE605070A))
            .border(1.5.dp, GlassCardBorder, RoundedCornerShape(32.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .horizontalScroll(rememberScrollState())
            .testTag("cockpit_dock"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Navigation / Maps Shortcut (Generous touch target for driving)
        DockIconButton(
            icon = Icons.Default.Navigation,
            contentDescription = "Maps",
            testTag = "dock_maps",
            onClick = { onNavigate("maps") }
        )

        // Music / Media Shortcut
        DockIconButton(
            icon = Icons.Default.MusicNote,
            contentDescription = "Music",
            testTag = "dock_music",
            onClick = { onNavigate("music") }
        )

        // Phone / Handsfree Calling Shortcut
        DockIconButton(
            icon = Icons.Default.Phone,
            contentDescription = "Phone",
            testTag = "dock_phone",
            onClick = { onNavigate("phone") }
        )

        // Apple CarPlay Shortcut (Dedicated in-dash projection)
        DockPainterIconButton(
            painterResId = R.drawable.ic_carplay,
            contentDescription = "Apple CarPlay",
            label = "CarPlay",
            testTag = "dock_carplay",
            onClick = { onNavigate("carplay") }
        )

        // Android Auto Shortcut (Dedicated in-dash projection)
        DockPainterIconButton(
            painterResId = R.drawable.ic_android_auto,
            contentDescription = "Android Auto",
            label = "Auto",
            testTag = "dock_android_auto",
            onClick = { onNavigate("android_auto") }
        )

        // OBD-II Live Telemetry & DTC Diagnostics Shortcut
        DockIconButton(
            icon = Icons.Default.Sensors,
            contentDescription = "OBD Diagnostics",
            testTag = "dock_obd",
            onClick = { onNavigate("obd_diagnostics") }
        )

        // Divider
        Box(
            modifier = Modifier
                .height(36.dp)
                .width(1.5.dp)
                .background(Color(0x33FFFFFF))
        )

        // HUD Mirror Mode Quick Toggle (windshield projection)
        DockIconButton(
            icon = Icons.Default.Flip,
            contentDescription = "HUD Mirror Mode",
            testTag = "dock_mirror",
            onClick = { onNavigate("hud_mirror") }
        )

        // Settings Shortcut
        DockIconButton(
            icon = Icons.Default.Settings,
            contentDescription = "Settings",
            testTag = "dock_settings",
            onClick = { onNavigate("settings") }
        )

        // All Apps Button (Primary Highlighted & Extra Large)
        Box(
            modifier = Modifier
                .size(width = 66.dp, height = 58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(accentColor)
                .clickable { onNavigate("all_apps") }
                .testTag("dock_all_apps"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = "All Apps",
                tint = if (accentColor == BmwCyan) TextDark else TextPrimaryWhite,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun DockIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    testTag: String = "",
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x1AFFFFFF))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextPrimaryWhite,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun DockPainterIconButton(
    painterResId: Int,
    contentDescription: String,
    label: String,
    testTag: String = "",
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x22FFFFFF))
            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = painterResId),
                contentDescription = contentDescription,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = label,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = TextPrimaryWhite,
                maxLines = 1
            )
        }
    }
}
