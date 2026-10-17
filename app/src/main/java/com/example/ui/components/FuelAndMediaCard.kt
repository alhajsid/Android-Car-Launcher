package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TelemetryData
import com.example.ui.theme.BmwAmber
import com.example.ui.theme.BmwCyan
import com.example.ui.theme.GlassCardBorder
import com.example.ui.theme.GlassCardFill
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondaryGrey
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FuelAndMediaCard(
    telemetry: TelemetryData,
    is24Hour: Boolean,
    accentColor: Color,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTimeString by remember { mutableStateOf("") }
    var currentDateString by remember { mutableStateOf("") }

    LaunchedEffect(is24Hour) {
        while (true) {
            val now = Date()
            val timeFormat = if (is24Hour) SimpleDateFormat("HH:mm", Locale.getDefault()) else SimpleDateFormat("hh:mm a", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEE d MMM", Locale.getDefault())
            currentTimeString = timeFormat.format(now)
            currentDateString = dateFormat.format(now)
            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(GlassCardFill)
            .border(1.dp, GlassCardBorder, RoundedCornerShape(24.dp))
            .padding(14.dp)
            .testTag("fuel_and_media_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top: Clock, Date & Weather Glance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = currentTimeString.ifEmpty { "17:07" },
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    lineHeight = 34.sp,
                    letterSpacing = (-1).sp,
                    color = TextPrimaryWhite
                )
                Text(
                    text = currentDateString.ifEmpty { "Wed 24 Oct" }.uppercase(),
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = TextSecondaryGrey
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "Weather",
                    tint = BmwAmber,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "${telemetry.weatherTempC}°",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TextPrimaryWhite
                )
            }
        }

        // Middle: Fuel Level & Remaining Range
        val hasObd = telemetry.isObdConnected
        val hasFuel = hasObd && telemetry.fuelLevelPercent != null
        val hasRange = hasObd && telemetry.remainingRangeKm != null

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x9905070A))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
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
                            imageVector = Icons.Default.LocalGasStation,
                            contentDescription = "Fuel",
                            tint = if (hasFuel) BmwAmber else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (hasFuel) "${telemetry.fuelLevelPercent}%" else "--%",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (hasFuel) TextPrimaryWhite else TextMuted
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (hasFuel) "PETROL" else if (hasObd) "NO SENSOR" else "NO OBD",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                    }

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (hasRange) "${telemetry.remainingRangeKm}" else "--",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = if (hasRange) TextPrimaryWhite else TextMuted
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "km",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = TextSecondaryGrey,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }

                // Fuel Level Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color(0x22FFFFFF))
                ) {
                    if (hasFuel) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((telemetry.fuelLevelPercent!! / 100f).coerceIn(0f, 1f))
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(BmwAmber, Color(0xFFFF7700), Color(0xFFFFD54F))
                                    )
                                )
                        )
                    }
                }

                // Instant vs Average Fuel Consumption
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "INSTANT",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 8.sp,
                            color = TextMuted
                        )
                        Text(
                            text = if (hasObd && telemetry.instantFuelL100km != null) {
                                "${String.format(Locale.US, "%.1f", telemetry.instantFuelL100km)} L/100km"
                            } else {
                                "-- L/100km"
                            },
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (hasObd && telemetry.instantFuelL100km != null) TextPrimaryWhite else TextMuted
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "AVERAGE",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 8.sp,
                            color = TextMuted
                        )
                        Text(
                            text = if (hasObd && telemetry.avgFuelL100km != null) {
                                "${String.format(Locale.US, "%.1f", telemetry.avgFuelL100km)} L/100km"
                            } else {
                                "-- L/100km"
                            },
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (hasObd && telemetry.avgFuelL100km != null) TextPrimaryWhite else TextMuted
                        )
                    }
                }
            }
        }

        // Bottom: Now Playing Media Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x9905070A))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stylized Album Art Thumbnail
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), accentColor)
                                )
                            )
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = TextPrimaryWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = telemetry.songTitle,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimaryWhite,
                            maxLines = 1
                        )
                        Text(
                            text = telemetry.songArtist,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp,
                            color = TextSecondaryGrey,
                            maxLines = 1
                        )
                    }
                }

                // Scrubber Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color(0x22FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(telemetry.mediaProgress)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(accentColor)
                    )
                }

                // Minimal Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { /* Previous */ },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = TextPrimaryWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                            .clickable { onTogglePlayback() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (telemetry.isMediaPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (telemetry.isMediaPlaying) "Pause" else "Play",
                            tint = if (accentColor == BmwCyan) TextDark else TextPrimaryWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = { /* Next */ },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = TextPrimaryWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
