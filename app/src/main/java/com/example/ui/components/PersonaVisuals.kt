package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CyanBioluminescent
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.VioletAccent

object PersonaVisuals {
    val iconKeys = listOf("spark", "code", "palette", "school", "rocket", "brain", "terminal")

    fun iconFor(key: String): ImageVector = when (key) {
        "code" -> Icons.Rounded.Code
        "palette" -> Icons.Rounded.Brush
        "school" -> Icons.Rounded.School
        "rocket" -> Icons.Rounded.RocketLaunch
        "brain" -> Icons.Rounded.Psychology
        "terminal" -> Icons.Rounded.Terminal
        else -> Icons.Rounded.AutoAwesome
    }

    @Composable
    fun accentColorFor(index: Int): Color = when (index % 5) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> EmeraldAccent
        2 -> AmberWarm
        3 -> VioletAccent
        else -> CyanBioluminescent
    }
}
