package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalVaultPalette
import com.example.ui.theme.MaterialThemePalette
import com.example.ui.theme.MaterialYouColorPresets

/**
 * SELECT-UNIFY: Color Palette Horizontal Picker matching the unified MUSE-REF Selection Language.
 * - Dynamic System tile uses 2.5dp accent border, accent.copy(alpha=0.12f) background, and 16dp check badge.
 * - Circular swatches use SplitCircleSwatch with 2.5dp accent border and matching 16dp check badge.
 * - Powered by LocalVaultPalette and LocalAccentColor.
 */
@Composable
fun ColorPalettePicker(
    selectedId: String,
    onSelectPalette: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current // SELECT-UNIFY
    val accent = LocalAccentColor.current // SELECT-UNIFY
    val presets = MaterialYouColorPresets.Presets
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Color Palette",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = palette.textPrimary // SELECT-UNIFY
            )

            // Current theme name indicator
            val currentPreset = MaterialYouColorPresets.getPreset(selectedId)
            Text(
                text = currentPreset.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = accent // SELECT-UNIFY: Accent color indicator
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Presets: Circular swatches with Pure Circular Selection Ring (no Palette icon, no check badge)
            presets.filter { it.id != MaterialYouColorPresets.SYSTEM_DYNAMIC_ID }.forEach { colorPalette ->
                val isSelected = selectedId.equals(colorPalette.id, ignoreCase = true)

                Box(
                    modifier = Modifier.size(54.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) accent.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .border(
                                width = if (isSelected) 2.5.dp else 0.dp,
                                color = if (isSelected) accent else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onSelectPalette(colorPalette.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        SplitCircleSwatch(
                            topColor = colorPalette.previewTop,
                            bottomLeftColor = colorPalette.previewBottomLeft,
                            bottomRightColor = colorPalette.previewBottomRight,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draws a circle split into:
 * 1. Top half (Dominant theme dark/background color)
 * 2. Bottom-left quadrant (Primary accent)
 * 3. Bottom-right quadrant (Secondary/Tertiary accent)
 */
@Composable
fun SplitCircleSwatch(
    topColor: Color,
    bottomLeftColor: Color,
    bottomRightColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        // Direct hardware-accelerated arc drawing with zero allocations
        // 1. Top half (180° to 360°)
        drawArc(
            color = topColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true
        )
        // 2. Bottom-left quadrant (90° to 180°)
        drawArc(
            color = bottomLeftColor,
            startAngle = 90f,
            sweepAngle = 90f,
            useCenter = true
        )
        // 3. Bottom-right quadrant (0° to 90°)
        drawArc(
            color = bottomRightColor,
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = true
        )
    }
}
