package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xD905070A))
            .border(1.dp, GlassCardBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("cockpit_dock"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Navigation / Maps Shortcut
        DockIconButton(
            icon = Icons.Default.Navigation,
            contentDescription = "Maps",
            onClick = { onNavigate("maps") }
        )

        // Music / Media Shortcut
        DockIconButton(
            icon = Icons.Default.MusicNote,
            contentDescription = "Music",
            onClick = { onNavigate("music") }
        )

        // Phone / Handsfree Calling Shortcut
        DockIconButton(
            icon = Icons.Default.Phone,
            contentDescription = "Phone",
            onClick = { onNavigate("phone") }
        )

        // OBD-II Live Telemetry & DTC Diagnostics Shortcut
        DockIconButton(
            icon = Icons.Default.Sensors,
            contentDescription = "OBD Diagnostics",
            onClick = { onNavigate("obd_diagnostics") }
        )

        // Divider
        Box(
            modifier = Modifier
                .height(22.dp)
                .width(1.dp)
                .background(Color(0x22FFFFFF))
        )

        // HUD Mirror Mode Quick Toggle (windshield projection)
        DockIconButton(
            icon = Icons.Default.Flip,
            contentDescription = "HUD Mirror Mode",
            onClick = { onNavigate("hud_mirror") }
        )

        // Settings Shortcut
        DockIconButton(
            icon = Icons.Default.Settings,
            contentDescription = "Settings",
            onClick = { onNavigate("settings") }
        )

        // All Apps Button (Primary Highlighted)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(accentColor)
                .clickable { onNavigate("all_apps") }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = "All Apps",
                tint = if (accentColor == BmwCyan) TextDark else TextPrimaryWhite,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun DockIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextSecondaryGrey,
            modifier = Modifier.size(22.dp)
        )
    }
}
