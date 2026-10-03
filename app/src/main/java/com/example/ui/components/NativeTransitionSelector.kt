package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalVaultPalette

data class TransitionOptionItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

/**
 * SELECT-UNIFY: Rebuilt with unified SelectorOptionRow system:
 * - 4 SelectorOptionRows with leading icon circle (38dp) and subtitle
 * - Removed pill surface and RadioButton
 * - Uses LocalVaultPalette and LocalAccentColor
 */
@Composable
fun NativeTransitionSelector(
    selectedStyle: Int,
    onSelectStyle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current // SELECT-UNIFY
    val accent = LocalAccentColor.current // SELECT-UNIFY

    val options = remember {
        listOf(
            TransitionOptionItem(
                id = 0,
                title = "Default Motion",
                subtitle = "Smooth vertical slide and fade",
                icon = Icons.Outlined.SwapVert
            ),
            TransitionOptionItem(
                id = 1,
                title = "Lateral Slide",
                subtitle = "Clean horizontal navigation slide",
                icon = Icons.Outlined.SwapHoriz
            ),
            TransitionOptionItem(
                id = 2,
                title = "Smooth Fade & Scale",
                subtitle = "Lightweight fluid scale and fade",
                icon = Icons.Outlined.AutoAwesome
            ),
            TransitionOptionItem(
                id = 3,
                title = "Link Transition",
                subtitle = "Seamless in-place page morph",
                icon = Icons.Outlined.Link
            )
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp) // SELECT-UNIFY: 6.dp spacing
    ) {
        options.forEach { option ->
            val isSelected = selectedStyle == option.id

            // SELECT-UNIFY: Unified SelectorOptionRow
            SelectorOptionRow(
                title = option.title,
                subtitle = option.subtitle,
                selected = isSelected,
                onClick = { onSelectStyle(option.id) },
                leading = {
                    Box(
                        modifier = Modifier
                            .size(38.dp) // SELECT-UNIFY: 38dp unified icon circle
                            .clip(CircleShape)
                            .background(if (isSelected) accent.copy(alpha = 0.15f) else palette.cardBg), // SELECT-UNIFY
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = option.title,
                            tint = if (isSelected) accent else palette.textMuted, // SELECT-UNIFY
                            modifier = Modifier.size(20.dp) // SELECT-UNIFY: 20dp icon
                        )
                    }
                }
            )
        }
    }
}
