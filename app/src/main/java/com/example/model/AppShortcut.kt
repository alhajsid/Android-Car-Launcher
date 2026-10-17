package com.example.model

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color

enum class AppCategory(val title: String) {
    ALL("All Installed Apps"),
    NAVIGATION("Navigation & Traffic"),
    MEDIA("Audio & Media"),
    OBD_TOOLS("OBD & Vehicle Tools"),
    COMMUNICATION("Communication"),
    FAVORITES("Favorites")
}

data class AppShortcut(
    val id: String,
    val name: String,
    val packageName: String,
    val category: AppCategory,
    val iconKey: String = "app",
    val accentColor: Color = Color(0xFF4CC2FF),
    val isPinnedToDock: Boolean = false,
    val isFavorite: Boolean = false,
    val isCustomInstalled: Boolean = true,
    val iconDrawable: Drawable? = null
)
